package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.agenda.ResultadoConfirmacao;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewayDePagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.StatusNoGateway;

/**
 * O único caminho para "esta cobrança foi paga" (E20, E22, E23): o webhook, a conciliação, o job de reprocessamento
 * e a simulação chamam o mesmo método. Quem manda é o gateway, consultado de novo (o corpo do webhook pode ser
 * forjado, A36). A confirmação da reserva é uma chamada síncrona à API pública da agenda (D67), porque o resultado
 * decide se a cobrança fica PAGA ou PAGA_APOS_EXPIRACAO (R14). O gateway é chamado fora de transação (E27).
 */
@Service
class ProcessarPagamento {

    /** PROCESSADO: está paga (agora ou antes). SEM_COBRANCA: o id é desconhecido (pode ser corrida com a criação). */
    enum Resultado {
        PROCESSADO,
        PAGO_APOS_EXPIRACAO,
        IGNORADO,
        SEM_COBRANCA
    }

    private final CobrancaRepository cobrancas;
    private final GatewayDePagamento gateway;
    private final Reservas reservas;
    private final TransicoesDeCobranca transicoes;
    private final TransactionTemplate transacao;
    /** Gancho só de teste: roda entre a confirmação da reserva e a marcação da cobrança (a janela da corrida, D77). */
    volatile Runnable aposConfirmarReserva = () -> { };
    private final Clock clock;

    ProcessarPagamento(CobrancaRepository cobrancas, GatewayDePagamento gateway, Reservas reservas, TransicoesDeCobranca transicoes,
            PlatformTransactionManager gerenciador, Clock clock) {
        this.cobrancas = cobrancas;
        this.gateway = gateway;
        this.reservas = reservas;
        this.transicoes = transicoes;
        this.transacao = new TransactionTemplate(gerenciador);
        this.clock = clock;
    }

    Resultado porIdProvedor(String idProvedor) {
        Optional<Cobranca> achada = cobrancas.findByProvedorAndIdCobrancaProvedor(gateway.provedor(), idProvedor);
        if (achada.isEmpty()) {
            return Resultado.SEM_COBRANCA;
        }
        Cobranca cobranca = achada.get();
        if (cobranca.getStatus() == StatusCobranca.PAGA) {
            return Resultado.PROCESSADO; // já tratada: o evento repetido não muda nada
        }
        if (cobranca.getStatus() == StatusCobranca.PAGA_APOS_EXPIRACAO) {
            return Resultado.PAGO_APOS_EXPIRACAO;
        }
        // Só PENDENTE e CANCELADA (reserva expirada) ainda podem receber um pagamento.
        if (!EnumSet.of(StatusCobranca.PENDENTE, StatusCobranca.CANCELADA).contains(cobranca.getStatus())) {
            return Resultado.IGNORADO;
        }

        if (gateway.consultar(idProvedor) != StatusNoGateway.PAGA) {
            return Resultado.IGNORADO;
        }

        boolean semHorario;
        try {
            semHorario = reservas.confirmarPagamento(cobranca.getReservaId()) == ResultadoConfirmacao.HORARIO_OCUPADO;
        } catch (ExcecaoDeNegocio regra) {
            if (regra.codigo() != CodigoErro.TRANSICAO_INVALIDA) {
                throw regra;
            }
            // Reserva CANCELADO, CONCLUIDO ou NAO_COMPARECEU: o dinheiro entrou e não há horário para confirmar.
            // Vira pendência para o painel (Fatia 5), igual ao pagamento tardio sem horário; o webhook responde 200.
            semHorario = true;
        }
        aposConfirmarReserva.run();
        boolean tardio = semHorario;
        UUID id = cobranca.getId();
        boolean[] canceladaNaJanela = {false};
        transacao.executeWithoutResult(status -> {
            Instant agora = clock.instant();
            if (tardio) {
                transicoes.pagarAposExpiracao(id, agora);
            } else {
                // Paga primeiro e só depois pergunta à agenda: se a barbearia cancelou entre a confirmação e aqui, o
                // listener do cancelamento já passou (cobrança CANCELADA) ou ainda vai passar (vê PAGA e devolve).
                transicoes.pagar(id, agora);
                if (reservas.estaCancelada(cobranca.getReservaId())) {
                    transicoes.pagarAposExpiracao(id, agora);
                    canceladaNaJanela[0] = true;
                }
            }
        });
        return tardio || canceladaNaJanela[0] ? Resultado.PAGO_APOS_EXPIRACAO : Resultado.PROCESSADO;
    }
}
