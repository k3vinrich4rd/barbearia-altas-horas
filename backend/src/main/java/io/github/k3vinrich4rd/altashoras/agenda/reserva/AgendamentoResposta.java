package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.ProrrogacaoDoPagamento;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroResumo;

/**
 * O "Agendamento" do contrato. {@code tokenGuest} só aparece na resposta da criação pelo visitante (mostrado uma
 * vez; o banco guarda o hash). {@code expiraEm} é nulo depois que a reserva sai da espera do pagamento.
 * {@code regiaoAutorizada} só aparece nas respostas do visitante (Fatia 11): a reserva guarda a região com a prova do aceite.
 * {@code prorrogacao} (1.11.0, Fatia 14A; 1.12.0, ADR 0015) é a janela do "mais tempo para pagar"; nulo quando não se aplica.
 */
public record AgendamentoResposta(UUID id, StatusReserva status, Reserva.Origem origem, ServicoDaReserva servico,
        BarbeiroResumo barbeiro, String inicio, String fim, long precoCentavos, @Nullable String expiraEm,
        @Nullable String motivoCancelamento, long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String tokenGuest,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean regiaoAutorizada,
        @Nullable Prorrogacao prorrogacao) {

    public record ServicoDaReserva(UUID id, String nome, int duracaoMin) {
    }

    /**
     * A janela do "mais tempo para pagar", no mesmo formato de {@code expiraEm}: o servidor decide, o front só mostra (E9).
     * {@code usada} fica por compatibilidade (deprecated na 1.12.0): {@code true} quando {@code usadas > 0}.
     */
    public record Prorrogacao(boolean usada, int usadas, int maximo, int restantes, long segundos, String abreEm,
            String fechaEm) {

        static @Nullable Prorrogacao de(@Nullable ProrrogacaoDoPagamento janela, ZoneId fuso) {
            return janela == null ? null
                    : new Prorrogacao(janela.usada(), janela.usadas(), janela.maximo(), janela.restantes(),
                            janela.segundos(), formatar(janela.abreEm(), fuso), formatar(janela.fechaEm(), fuso));
        }
    }

    /** O mesmo formato da disponibilidade: sempre com segundos e offset (2026-12-01T10:00:00-03:00). */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static AgendamentoResposta de(LinhaAgendamento linha, ZoneId fuso, @Nullable String tokenGuest,
            @Nullable ProrrogacaoDoPagamento prorrogacao) {
        boolean esperandoPagamento = linha.status() == StatusReserva.AGUARDANDO_PAGAMENTO
                || linha.status() == StatusReserva.EXPIRADO;
        return new AgendamentoResposta(linha.id(), linha.status(), linha.origem(),
                new ServicoDaReserva(linha.servicoId(), linha.servicoNome(), linha.duracaoMin()),
                new BarbeiroResumo(linha.barbeiroId(), linha.barbeiroNome()),
                formatar(linha.inicio(), fuso), formatar(linha.fim(), fuso), linha.precoCentavos(),
                esperandoPagamento && linha.expiraEm() != null ? formatar(linha.expiraEm(), fuso) : null,
                linha.motivoCancelamento(), linha.version(), tokenGuest, null, Prorrogacao.de(prorrogacao, fuso));
    }

    AgendamentoResposta comRegiaoAutorizada(boolean autorizada) {
        return new AgendamentoResposta(id, status, origem, servico, barbeiro, inicio, fim, precoCentavos, expiraEm,
                motivoCancelamento, version, tokenGuest, autorizada, prorrogacao);
    }

    private static String formatar(Instant instante, ZoneId fuso) {
        return FORMATO.format(instante.atZone(fuso));
    }

    @Override
    public String toString() {
        return "AgendamentoResposta[" + id + ", " + status + "]";
    }
}
