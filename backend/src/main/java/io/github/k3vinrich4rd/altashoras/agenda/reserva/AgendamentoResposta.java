package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroResumo;

/**
 * O "Agendamento" do contrato. {@code tokenGuest} só aparece na resposta da criação pelo visitante (mostrado uma
 * vez; o banco guarda o hash). {@code expiraEm} é nulo depois que a reserva sai da espera do pagamento.
 */
public record AgendamentoResposta(UUID id, StatusReserva status, Reserva.Origem origem, ServicoDaReserva servico,
        BarbeiroResumo barbeiro, String inicio, String fim, long precoCentavos, @Nullable String expiraEm,
        @Nullable String motivoCancelamento, long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String tokenGuest) {

    public record ServicoDaReserva(UUID id, String nome, int duracaoMin) {
    }

    /** O mesmo formato da disponibilidade: sempre com segundos e offset (2026-12-01T10:00:00-03:00). */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static AgendamentoResposta de(LinhaAgendamento linha, ZoneId fuso) {
        return de(linha, fuso, null);
    }

    static AgendamentoResposta de(LinhaAgendamento linha, ZoneId fuso, @Nullable String tokenGuest) {
        boolean esperandoPagamento = linha.status() == StatusReserva.AGUARDANDO_PAGAMENTO
                || linha.status() == StatusReserva.EXPIRADO;
        return new AgendamentoResposta(linha.id(), linha.status(), linha.origem(),
                new ServicoDaReserva(linha.servicoId(), linha.servicoNome(), linha.duracaoMin()),
                new BarbeiroResumo(linha.barbeiroId(), linha.barbeiroNome()),
                formatar(linha.inicio(), fuso), formatar(linha.fim(), fuso), linha.precoCentavos(),
                esperandoPagamento && linha.expiraEm() != null ? formatar(linha.expiraEm(), fuso) : null,
                linha.motivoCancelamento(), linha.version(), tokenGuest);
    }

    private static String formatar(Instant instante, ZoneId fuso) {
        return FORMATO.format(instante.atZone(fuso));
    }

    @Override
    public String toString() {
        return "AgendamentoResposta[" + id + ", " + status + "]";
    }
}
