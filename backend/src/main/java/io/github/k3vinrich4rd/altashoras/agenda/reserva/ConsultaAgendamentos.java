package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.ConciliadorDePagamento;
import io.github.k3vinrich4rd.altashoras.agenda.ProrrogacaoDoPagamento;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Leituras de agendamento. Reserva de outra pessoa e token errado respondem igual a "não existe" (404, R23):
 * a resposta não confirma que o id é válido.
 */
@Service
@Transactional(readOnly = true)
class ConsultaAgendamentos {

    static final int TAMANHO_MAXIMO = 50;
    private static final String HASH_INEXISTENTE = "0".repeat(64);

    private final ReservaRepository reservas;
    private final ReservaGuestRepository visitantes;
    private final ObjectProvider<ConciliadorDePagamento> conciliador;
    private final ZoneId fuso;
    private final Clock clock;
    private final Duration prorrogacao;

    ConsultaAgendamentos(ReservaRepository reservas, ReservaGuestRepository visitantes,
            ObjectProvider<ConciliadorDePagamento> conciliador, @Value("${app.fuso:America/Sao_Paulo}") String fuso,
            Clock clock, @Value("${app.agenda.prorrogacao-pagamento:PT60S}") Duration prorrogacao) {
        this.reservas = reservas;
        this.visitantes = visitantes;
        this.conciliador = conciliador;
        this.fuso = ZoneId.of(fuso);
        this.clock = clock;
        this.prorrogacao = prorrogacao;
    }

    /** A resposta do contrato com a janela do "Acrescentar 1 minuto" (E9), contada com o relógio injetado. */
    private AgendamentoResposta resposta(LinhaAgendamento linha, @Nullable String tokenGuest) {
        ProrrogacaoDoPagamento janela = ProrrogacaoDoPagamento.de(linha.status().name(), linha.origem().name(),
                linha.expiraEm(), linha.prorrogadaEm(), clock.instant(), prorrogacao);
        return AgendamentoResposta.de(linha, fuso, tokenGuest, janela);
    }

    Pagina meus(UUID clienteId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                    "pagina deve ser >= 0 e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
        Page<LinhaAgendamento> resultado = reservas.doCliente(clienteId, PageRequest.of(pagina, tamanho));
        return new Pagina(resultado.map(linha -> resposta(linha, null)).getContent(), pagina, tamanho,
                resultado.getTotalElements());
    }

    /**
     * Sem transação aberta (NOT_SUPPORTED): a conciliação chama o gateway e grava em transações próprias (E25, E27).
     * Só concilia depois de achar a reserva do dono, para um id alheio não disparar consulta ao gateway.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    AgendamentoResposta meu(UUID clienteId, UUID id) {
        LinhaAgendamento linha = reservas.doCliente(clienteId, id).orElseThrow(ConsultaAgendamentos::naoEncontrado);
        if (esperandoPagamento(linha)) {
            conciliar(id);
            linha = reservas.doCliente(clienteId, id).orElseThrow(ConsultaAgendamentos::naoEncontrado);
        }
        return resposta(linha, null);
    }

    /**
     * O estado de agora, sem reconsultar o gateway: a resposta da prorrogação não pode disparar conciliação (E6: o pagamento
     * só acompanha o prazo, e o gateway não é chamado).
     */
    AgendamentoResposta meuSemConciliar(UUID clienteId, UUID id) {
        return resposta(reservas.doCliente(clienteId, id).orElseThrow(ConsultaAgendamentos::naoEncontrado), null);
    }

    AgendamentoResposta doVisitanteSemConciliar(UUID id) {
        return resposta(reservas.doVisitante(id).orElseThrow(ConsultaAgendamentos::naoEncontrado), null)
                .comRegiaoAutorizada(regiaoAutorizada(id));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    AgendamentoResposta doVisitante(UUID id, @Nullable String token) {
        // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
        String hashGuardado = visitantes.findById(id).map(ReservaGuest::getTokenHash).orElse(HASH_INEXISTENTE);
        if (!TokenGuest.confere(token, hashGuardado) || HASH_INEXISTENTE.equals(hashGuardado)) {
            throw naoEncontrado();
        }
        LinhaAgendamento linha = reservas.doVisitante(id).orElseThrow(ConsultaAgendamentos::naoEncontrado);
        if (esperandoPagamento(linha)) {
            conciliar(id);
            linha = reservas.doVisitante(id).orElseThrow(ConsultaAgendamentos::naoEncontrado);
        }
        return resposta(linha, null).comRegiaoAutorizada(regiaoAutorizada(id));
    }

    /** A reserva guarda hoje a região com a prova do aceite? Some quando o visitante retira a autorização. */
    private boolean regiaoAutorizada(UUID id) {
        return reservas.findById(id).map(r -> r.getRegiaoConsentimentoVersao() != null).orElse(false);
    }

    private static boolean esperandoPagamento(LinhaAgendamento linha) {
        return linha.status() == StatusReserva.AGUARDANDO_PAGAMENTO || linha.status() == StatusReserva.EXPIRADO;
    }

    /** Se o módulo de pagamento existe, ele reconsulta o gateway (no máximo a cada 10 s por cobrança, E25). */
    private void conciliar(UUID reservaId) {
        conciliador.ifAvailable(c -> c.conciliarReserva(reservaId));
    }

    AgendamentoResposta criadaPeloVisitante(UUID id, String token) {
        return reservas.doVisitante(id).map(linha -> resposta(linha, token)
                .comRegiaoAutorizada(regiaoAutorizada(id))).orElseThrow();
    }

    private static ExcecaoDeNegocio naoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }

    record Pagina(List<AgendamentoResposta> itens, int pagina, int tamanho, long total) {
    }
}
