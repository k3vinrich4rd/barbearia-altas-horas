package io.github.k3vinrich4rd.altashoras.agenda;

/**
 * O que aconteceu quando o pagamento pediu para confirmar a reserva. O pagamento decide a partir disto se a
 * cobrança fica PAGA ou PAGA_APOS_EXPIRACAO (R14).
 */
public enum ResultadoConfirmacao {

    /** A reserva está CONFIRMADO (agora, ou já estava: chamar de novo não muda nada). */
    CONFIRMADA,

    /** Pagou depois de expirar e o horário já é de outra pessoa: a reserva continua EXPIRADO. */
    HORARIO_OCUPADO
}
