package io.github.k3vinrich4rd.altashoras.agenda;

import java.util.UUID;

/**
 * Porta que a agenda chama antes de responder a leitura de um agendamento (E25): se o webhook atrasou, quem tem
 * a cobrança (o módulo pagamento) reconsulta o gateway e confirma a reserva. A interface mora na agenda e quem
 * implementa é o pagamento: a agenda não depende do pagamento (inversão de dependência, ADR 0002).
 */
public interface ConciliadorDePagamento {

    /** Não lança exceção: falha do gateway aqui não pode quebrar a leitura da reserva. */
    void conciliarReserva(UUID reservaId);
}
