package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas.DadosParaCobranca;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas.Dono;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cpf;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.CobrancaNoGateway;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayDePagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.NovaCobranca;
import io.github.k3vinrich4rd.altashoras.pagamento.politica.PoliticaPagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.politica.PoliticaPagamentoRepository;

/**
 * Criar a cobrança (E14 a E18, E21, E27). A ordem é a do D21: 1) grava a cobrança PENDENTE com os hashes da
 * idempotência e o id do provedor nulo; 2) chama o gateway FORA de transação (pool de 5 conexões, D12); 3) grava o
 * que o gateway respondeu. Se a API cair entre 2 e 3, a repetição com a mesma chave acha a cobrança pelo
 * {@code externalReference} em vez de criar outra. O CPF só passa por aqui, a caminho do gateway (ADR 0009).
 */
@Service
class CriarCobranca {

    /** SQLSTATE do PostgreSQL para violação de unique (a chave de idempotência ou a cobrança viva da reserva). */
    private static final String UNICIDADE_VIOLADA = "23505";

    /**
     * Maior que o tempo máximo da criação no gateway: 3 chamadas (cliente, cobrança, QR) de até 5 s de leitura cada.
     * Cobrança sem id do provedor mais nova que isto pode estar sendo criada por outra requisição (E14).
     */
    static final Duration TEMPO_MAXIMO_DA_CRIACAO = Duration.ofSeconds(15);

    /** {@code repeticao}: mesma chave e mesmo corpo de antes (200, sem cpfMascarado); senão 201. */
    record Resultado(boolean repeticao, CobrancaResposta cobranca) {
    }

    private final CobrancaRepository cobrancas;
    private final Reservas reservas;
    private final GatewayDePagamento gateway;
    private final PoliticaPagamentoRepository politicas;
    private final EntityManager em;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final ZoneId fuso;
    private final byte[] segredo;

    CriarCobranca(CobrancaRepository cobrancas, Reservas reservas, GatewayDePagamento gateway,
            PoliticaPagamentoRepository politicas, EntityManager em, PlatformTransactionManager gerenciador, Clock clock,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso,
            @Value("${app.pagamento.idempotencia-segredo:}") String segredo) {
        if (segredo.isBlank()) {
            // Sem segredo o HMAC vira um hash comum e o CPF seria revertido por força bruta (D17): a API não sobe.
            throw new IllegalStateException("Defina IDEMPOTENCIA_HMAC_SEGREDO (app.pagamento.idempotencia-segredo).");
        }
        this.cobrancas = cobrancas;
        this.reservas = reservas;
        this.gateway = gateway;
        this.politicas = politicas;
        this.em = em;
        this.transacao = new TransactionTemplate(gerenciador);
        this.clock = clock;
        this.fuso = ZoneId.of(fuso);
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    Resultado executar(Dono dono, @Nullable String chave, CobrancaRequest pedido) {
        if (chave == null || chave.isBlank()) {
            throw new ExcecaoDeNegocio(CodigoErro.IDEMPOTENCY_KEY_AUSENTE);
        }
        Cpf cpf = Cpf.de(pedido.cpf());
        DadosParaCobranca reserva = reservas.paraCobranca(pedido.reservaId(), dono);

        PoliticaPagamento vigente = politicas.findFirstByOrderByVersaoDesc().orElseThrow();
        if (!Boolean.TRUE.equals(pedido.aceitePolitica()) || pedido.versaoPolitica() == null
                || pedido.versaoPolitica() != vigente.getVersao()) {
            throw new ExcecaoDeNegocio(CodigoErro.ACEITE_POLITICA_OBRIGATORIO);
        }

        String chaveHash = sha256(chave.strip());
        String corpoHash = hmac(pedido.reservaId() + "|" + pedido.metodo() + "|" + cpf.digitos());
        Optional<Cobranca> existente = cobrancas.findByChaveIdempotenciaHash(chaveHash);
        if (existente.isPresent()) {
            return repetir(existente.get(), corpoHash, reserva, cpf);
        }

        if (reserva.vencida()) {
            throw new ExcecaoDeNegocio(CodigoErro.RESERVA_EXPIRADA);
        }
        if (!reserva.aguardandoPagamento()) {
            throw new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA, "A reserva não está aguardando pagamento.");
        }
        cobrancas.findFirstByReservaIdAndStatusIn(reserva.id(), EnumSet.of(StatusCobranca.PENDENTE, StatusCobranca.PAGA))
                .ifPresent(viva -> {
                    throw emAndamento(viva);
                });

        Instant agora = clock.instant();
        Cobranca nova = Cobranca.nova(reserva.id(), gateway.provedor(), pedido.metodo(), reserva.precoCentavos(),
                chaveHash, corpoHash, reserva.expiraEm(), agora);
        try {
            transacao.executeWithoutResult(status -> {
                em.persist(nova);
                em.flush();
                reservas.registrarAceite(reserva.id(), vigente.getVersao(), agora);
            });
        } catch (RuntimeException falha) {
            if (!UNICIDADE_VIOLADA.equals(sqlState(falha))) {
                throw falha;
            }
            // Corrida: outra requisição gravou primeiro (a mesma chave ou outra cobrança viva desta reserva).
            Optional<Cobranca> outra = cobrancas.findByChaveIdempotenciaHash(chaveHash);
            if (outra.isEmpty()) {
                outra = cobrancas.findFirstByReservaIdAndStatusIn(reserva.id(),
                        EnumSet.of(StatusCobranca.PENDENTE, StatusCobranca.PAGA));
            }
            throw outra.map(CriarCobranca::emAndamento).orElse(new ExcecaoDeNegocio(CodigoErro.COBRANCA_EM_ANDAMENTO));
        }
        return new Resultado(false, completar(nova, reserva, cpf, false));
    }

    /** Mesma chave: o corpo precisa ser o mesmo (HMAC, R10); sendo, devolve a cobrança sem falar com o gateway. */
    private Resultado repetir(Cobranca existente, String corpoHash, DadosParaCobranca reserva, Cpf cpf) {
        boolean igual = MessageDigest.isEqual(existente.getHashCorpo().getBytes(StandardCharsets.US_ASCII),
                corpoHash.getBytes(StandardCharsets.US_ASCII));
        if (!igual) {
            throw new ExcecaoDeNegocio(CodigoErro.IDEMPOTENCIA_CONFLITO);
        }
        if (existente.getIdCobrancaProvedor() != null) {
            return new Resultado(true, CobrancaResposta.de(existente, fuso, null, null));
        }
        // Sem id do provedor: ou a resposta do gateway se perdeu (A30) ou outra requisição está criando agora. Só retoma
        // depois de passado o tempo máximo da criação; antes disso a outra requisição ainda pode estar no gateway.
        if (existente.getCriadoEm().plus(TEMPO_MAXIMO_DA_CRIACAO).isAfter(clock.instant())) {
            throw emAndamento(existente);
        }
        // A cobrança foi gravada, mas a resposta do gateway se perdeu (A30): procura pelo externalReference antes de criar.
        if (reserva.vencida()) {
            throw new ExcecaoDeNegocio(CodigoErro.RESERVA_EXPIRADA);
        }
        return new Resultado(false, completar(existente, reserva, cpf, true));
    }

    /** Passos 2 e 3 do D21: o gateway fora de transação, depois o registro do que ele respondeu. */
    private CobrancaResposta completar(Cobranca cobranca, DadosParaCobranca reserva, Cpf cpf, boolean procurarAntes) {
        CobrancaNoGateway resposta = null;
        if (procurarAntes) {
            resposta = gateway.buscarPorReferencia(cobranca.getId()).orElse(null);
        }
        if (resposta == null) {
            resposta = gateway.criar(new NovaCobranca(cobranca.getId(), cobranca.getMetodo(), cobranca.getValorCentavos(),
                    cpf, reserva.nomePagador(), reserva.expiraEm() == null ? clock.instant() : reserva.expiraEm()));
        }
        CobrancaNoGateway gravar = resposta;
        Cobranca atual = transacao.execute(status -> {
            Cobranca c = cobrancas.findById(cobranca.getId()).orElseThrow();
            c.registrarNoProvedor(gravar, clock.instant());
            return c;
        });
        return CobrancaResposta.de(atual, fuso, resposta.pixQrCodeBase64(), cpf.mascarado());
    }

    private static ExcecaoDeNegocio emAndamento(Cobranca viva) {
        return new ExcecaoDeNegocio(CodigoErro.COBRANCA_EM_ANDAMENTO,
                "Esta reserva já tem uma cobrança em andamento: " + viva.getId() + ".");
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }

    /** HMAC-SHA256 em hex, com o segredo do ambiente: um hash puro do CPF seria revertido por força bruta (D17). */
    private String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }

    private static @Nullable String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
