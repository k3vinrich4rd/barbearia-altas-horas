package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.CriptografiaWebPush;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ValidadorDeEndpoint;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * Os aparelhos do barbeiro (Fatia 13B): inscrever, remover, a chave pública para o navegador e o aviso de teste. A conta é
 * SEMPRE a do token, nunca vem do corpo (D174). O endpoint é uma URL que o usuário informou: só hosts de serviço de push, em
 * https (E9, A24). Nada de endpoint ou chave vai para log (R30).
 */
@Service
class AparelhosDoBarbeiro {

    /** A resposta de {@code GET /admin/notificacoes/chave-publica}: sem VAPID, só {@code disponivel: false}. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record ChavePublica(boolean disponivel, @Nullable String chavePublica) {
    }

    private final Inscricoes inscricoes;
    private final Despachante despachante;
    private final ParametrosDeNotificacao parametros;
    private final ValidadorDeEndpoint validador;
    private final LimitadorDeTentativas limitador;
    private final Clock clock;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;
    private final TransactionTemplate semTransacao;

    AparelhosDoBarbeiro(Inscricoes inscricoes, Despachante despachante, ParametrosDeNotificacao parametros,
            LimitadorDeTentativas limitador, Clock clock, PlatformTransactionManager gerenciador) {
        this.inscricoes = inscricoes;
        this.despachante = despachante;
        this.parametros = parametros;
        this.validador = new ValidadorDeEndpoint(parametros.hostsPermitidos());
        this.limitador = limitador;
        this.clock = clock;
        this.leitura = new TransactionTemplate(gerenciador);
        this.leitura.setReadOnly(true);
        this.escrita = new TransactionTemplate(gerenciador);
        this.semTransacao = new TransactionTemplate(gerenciador);
        this.semTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    ChavePublica chavePublica() {
        boolean disponivel = parametros.vapidConfigurada() && parametros.ligado(Canal.WEBPUSH);
        return new ChavePublica(disponivel, disponivel ? parametros.vapidPublica() : null);
    }

    void inscrever(UUID usuarioId, InscricaoRequest pedido) {
        if (!validador.aceita(pedido.endpoint())) {
            throw ExcecaoDeNegocio.campoInvalido("endpoint", "Endereço de notificação não aceito.");
        }
        if (!ehChaveDoAparelho(pedido.chaves().p256dh(), 65, (byte) 4) || !ehChaveDoAparelho(pedido.chaves().auth(), 16, null)
                || !CriptografiaWebPush.ehChavePublicaValida(decodificar(pedido.chaves().p256dh()))) {
            throw ExcecaoDeNegocio.campoInvalido("chaves", "Chaves de notificação inválidas.");
        }
        Aparelho aparelho = new Aparelho(pedido.endpoint(), pedido.chaves().p256dh(), pedido.chaves().auth());
        escrita.executeWithoutResult(s -> inscricoes.guardar(usuarioId, aparelho, clock.instant()));
    }

    void remover(UUID usuarioId, String endpoint) {
        escrita.executeWithoutResult(s -> inscricoes.remover(usuarioId, endpoint));
    }

    /**
     * Manda o aviso de teste só para os aparelhos da conta (D-10). Limite de 5 por hora por conta. O envio roda fora de
     * transação (E5); depois, numa transação curta, apaga a inscrição morta e marca a que respondeu.
     */
    void enviarTeste(UUID usuarioId) {
        String chave = "notificacao-teste|" + usuarioId;
        long bloqueado = limitador.segundosBloqueado(chave, parametros.testeMax(), parametros.testeJanela());
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        limitador.registrar(chave, parametros.testeJanela());
        List<Aparelho> aparelhos = leitura.execute(s -> inscricoes.daConta(usuarioId));
        if (aparelhos == null || aparelhos.isEmpty()) {
            return;
        }
        Despachante.Relatorio relatorio = semTransacao.execute(s -> despachante.entregar(TextoDoAviso.deTeste(), usuarioId,
                aparelhos, () -> null));
        if (relatorio == null) {
            return;
        }
        Instant agora = clock.instant();
        escrita.executeWithoutResult(s -> {
            relatorio.invalidos().forEach(a -> inscricoes.removerMorta(a.endpoint()));
            relatorio.enviados().forEach(a -> inscricoes.marcarEnvioOk(a.endpoint(), agora));
        });
    }

    private static byte[] decodificar(String valor) {
        return java.util.Base64.getUrlDecoder().decode(valor.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    /** Tamanho certo (em bytes) depois de decodificar o base64url; nunca devolve o valor recebido. */
    private static boolean ehChaveDoAparelho(String valor, int tamanho, @Nullable Byte primeiro) {
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(valor.replace('+', '-').replace('/', '_').replace("=", ""));
            return bytes.length == tamanho && (primeiro == null || bytes[0] == primeiro);
        } catch (IllegalArgumentException invalido) {
            return false;
        }
    }
}
