package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * A reserva de um horário. Serviço e barbeiro ficam como ids (sem associação JPA): a leitura usa projeção com
 * JOIN (sem N+1, E12) e a escrita não precisa carregar o catálogo. O preço é copiado do serviço na criação (E6).
 * {@code @Version} protege duas transições ao mesmo tempo (ex.: webhook confirmando enquanto o job expira).
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    public enum Origem { CLIENTE, GUEST }

    @Id
    private UUID id;

    @Column(name = "barbeiro_id", nullable = false)
    private UUID barbeiroId;

    @Column(name = "servico_id", nullable = false)
    private UUID servicoId;

    @Column(name = "cliente_id")
    private @Nullable UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origem origem;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(name = "fim_ocupacao", nullable = false)
    private Instant fimOcupacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusReserva status;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(name = "expira_em")
    private @Nullable Instant expiraEm;

    @Column(name = "motivo_cancelamento")
    private @Nullable String motivoCancelamento;

    @Column(name = "cancelado_por")
    private @Nullable UUID canceladoPor;

    @Column(name = "cancelado_em")
    private @Nullable Instant canceladoEm;

    @Column(name = "finalizado_por")
    private @Nullable UUID finalizadoPor;

    @Column(name = "finalizado_em")
    private @Nullable Instant finalizadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /** Objeto, não primitivo: nulo = entidade nova, e o persist não confunde com uma reserva que já existe. */
    @Version
    private @Nullable Long version;

    protected Reserva() {
    }

    private Reserva(Horario horario, @Nullable UUID clienteId, long precoCentavos, Instant expiraEm, Instant agora) {
        this.id = UUID.randomUUID();
        this.barbeiroId = horario.barbeiroId();
        this.servicoId = horario.servicoId();
        this.clienteId = clienteId;
        this.origem = clienteId == null ? Origem.GUEST : Origem.CLIENTE;
        this.inicio = horario.inicio();
        this.fim = horario.fim();
        this.fimOcupacao = horario.fimOcupacao();
        this.status = StatusReserva.AGUARDANDO_PAGAMENTO;
        this.precoCentavos = precoCentavos;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Toda reserva nasce aguardando o pagamento de 100%, com prazo (R6, R7). Sem cliente = visitante. */
    static Reserva nova(Horario horario, @Nullable UUID clienteId, long precoCentavos, Instant expiraEm, Instant agora) {
        return new Reserva(horario, clienteId, precoCentavos, expiraEm, agora);
    }

    /** Muda o status só se a máquina de estados permitir; qualquer outra mudança é 422 TRANSICAO_INVALIDA. */
    public void mudarPara(StatusReserva destino, Instant agora) {
        if (!status.podeIrPara(destino)) {
            throw new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA,
                    "Uma reserva " + status + " não pode ir para " + destino + ".");
        }
        this.status = destino;
        this.atualizadoEm = agora;
    }

    /**
     * Fechamento do atendimento (R19 a R21): CONCLUIDO ou NAO_COMPARECEU, só a partir do início (inclusivo, E7).
     * A máquina de estados responde primeiro; "antes do início" reaproveita TRANSICAO_INVALIDA com texto próprio.
     */
    public void fechar(StatusReserva destino, UUID por, Instant agora) {
        if (status.podeIrPara(destino) && agora.isBefore(inicio)) {
            throw new ExcecaoDeNegocio(CodigoErro.TRANSICAO_INVALIDA,
                    "O atendimento só pode ser fechado a partir do horário de início.");
        }
        mudarPara(destino, agora);
        this.finalizadoPor = por;
        this.finalizadoEm = agora;
    }

    /** Cancelamento pela barbearia (R22): o motivo é obrigatório e fica na reserva, que já é o registro (E9, E11). */
    public void cancelarPelaBarbearia(String motivo, UUID por, Instant agora) {
        mudarPara(StatusReserva.CANCELADO, agora);
        this.motivoCancelamento = motivo;
        this.canceladoPor = por;
        this.canceladoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBarbeiroId() {
        return barbeiroId;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public StatusReserva getStatus() {
        return status;
    }
}
