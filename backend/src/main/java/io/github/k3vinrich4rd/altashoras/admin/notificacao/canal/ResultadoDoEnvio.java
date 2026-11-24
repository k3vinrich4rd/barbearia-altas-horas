package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

/** O que aconteceu quando um canal tentou entregar (E7). */
public enum ResultadoDoEnvio {
    /** O serviço aceitou a mensagem. */
    ENVIADO,
    /** Falha passageira (rede, 429, 5xx, timeout): vale tentar de novo. */
    TENTAR_DEPOIS,
    /**
     * O serviço de push recusou o envio mas a inscrição NÃO morreu (400, 401, 403, 413: por exemplo par VAPID trocado). Só 404 e
     * 410 apagam a inscrição (RFC 8030). Conta como tentativa: depois de N o aviso fica só em Novidades (D178).
     */
    RECUSADO,
    /** O destino não existe mais (404, 410) ou não é aceito: apagar a inscrição. */
    DESTINO_INVALIDO,
    /** O canal está ligado mas falta configuração: nada foi enviado. */
    NAO_CONFIGURADO
}
