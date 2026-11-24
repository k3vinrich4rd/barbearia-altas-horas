package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

/**
 * Porta de entrega do aviso (E7, ADR 0007 e 0011): sem framework, para trocar o canal ser configuração e não código. O
 * Despachante só chama os canais ligados em {@code app.notificacao.canais}. Quem implementa nunca é chamado dentro de
 * transação (E5: só 3 conexões) e nunca imprime o destino.
 */
public interface CanalDeNotificacao {

    Canal canal();

    /** {@code true}: chamado uma vez por aparelho inscrito (Web Push). {@code false}: uma vez por conta (e-mail). */
    default boolean porAparelho() {
        return true;
    }

    ResultadoDoEnvio enviar(AvisoParaEnvio aviso, DestinoDoAviso destino);
}
