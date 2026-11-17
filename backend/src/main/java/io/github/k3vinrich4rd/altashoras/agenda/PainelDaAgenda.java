package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.Reserva;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.StatusReserva;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o painel (ADR 0002, E1): a agenda do dia ou da semana e o fechamento do atendimento.
 * O módulo admin chama este serviço e grava a auditoria na mesma transação; ele nunca lê a tabela da reserva.
 * A conta é ligada ao barbeiro por {@code barbeiro.usuario_id}: o BARBEIRO só enxerga e fecha o que é dele, e o que
 * não é dele responde 404, igual a "não existe" (R23, H9).
 */
@Service
public class PainelDaAgenda {

    public enum Visao { DIA, SEMANA }

    /** O que o painel pode pedir para a reserva. As demais transições (confirmar, expirar) não são do painel. */
    public enum StatusAlvo { CONCLUIDO, NAO_COMPARECEU, CANCELADO }

    /** Quem pede: o {@code sub} do token e se tem o papel ADMIN (quem tem os dois papéis vale como ADMIN, E3). */
    public record Escopo(UUID usuarioId, boolean admin) {
    }

    /**
     * Uma reserva da agenda, já com o contato de quem vem (nome e WhatsApp saem só para o painel, D-4). O toString não
     * imprime nada além do id e do status (R30).
     */
    public record Item(UUID id, String status, String origem, UUID servicoId, String servicoNome, int duracaoMin,
            UUID barbeiroId, String barbeiroNome, Instant inicio, Instant fim, long precoCentavos,
            @Nullable Instant expiraEm, @Nullable String motivoCancelamento, long version, String clienteNome,
            @Nullable String clienteWhatsapp) {

        @Override
        public String toString() {
            return "Item[" + id + ", " + status + "]";
        }
    }

    /** O resultado do fechamento: o status de antes (a auditoria guarda de/para) e a reserva já atualizada. */
    public record Mudanca(String de, Item item) {
    }

    private static final int MOTIVO_MAXIMO = 300;

    private static final String SELECIONAR = """
            SELECT r.id, r.status, r.origem, r.servico_id, s.nome AS servico_nome, s.duracao_min,
                   r.barbeiro_id, b.nome_exibicao AS barbeiro_nome, r.inicio, r.fim, r.preco_centavos, r.expira_em,
                   r.motivo_cancelamento, r.version,
                   COALESCE(g.nome, u.nome) AS cliente_nome, COALESCE(g.whatsapp, u.whatsapp) AS cliente_whatsapp
            FROM reserva r
            JOIN servico s ON s.id = r.servico_id
            JOIN barbeiro b ON b.id = r.barbeiro_id
            LEFT JOIN usuario u ON u.id = r.cliente_id
            LEFT JOIN reserva_guest g ON g.reserva_id = r.id
            """;

    private static final RowMapper<Item> LINHA = (rs, n) -> {
        String status = rs.getString("status");
        StatusReserva estado = StatusReserva.valueOf(status);
        boolean esperando = estado == StatusReserva.AGUARDANDO_PAGAMENTO || estado == StatusReserva.EXPIRADO;
        OffsetDateTime expiraEm = rs.getObject("expira_em", OffsetDateTime.class);
        return new Item(rs.getObject("id", UUID.class), status, rs.getString("origem"),
                rs.getObject("servico_id", UUID.class), rs.getString("servico_nome"), rs.getInt("duracao_min"),
                rs.getObject("barbeiro_id", UUID.class), rs.getString("barbeiro_nome"),
                rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                rs.getObject("fim", OffsetDateTime.class).toInstant(), rs.getLong("preco_centavos"),
                esperando && expiraEm != null ? expiraEm.toInstant() : null, rs.getString("motivo_cancelamento"),
                rs.getLong("version"), rs.getString("cliente_nome"), rs.getString("cliente_whatsapp"));
    };

    private final JdbcClient jdbc;
    private final EntityManager em;
    private final ConfiguracaoAgendaRepository configuracoes;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    PainelDaAgenda(JdbcClient jdbc, EntityManager em, ConfiguracaoAgendaRepository configuracoes,
            ApplicationEventPublisher eventos, Clock clock) {
        this.jdbc = jdbc;
        this.em = em;
        this.configuracoes = configuracoes;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * A agenda do dia ou da semana (segunda a domingo), por {@code inicio}, no fuso da barbearia (E4). Sem as
     * expiradas e sem as pendentes já vencidas, que não ocupam horário (R15, E5). Uma consulta só, com JOIN (E6).
     */
    @Transactional(readOnly = true)
    public List<Item> agenda(Escopo escopo, LocalDate data, Visao visao, @Nullable UUID barbeiroId) {
        UUID alvo = barbeiroDoPedido(escopo, barbeiroId);
        ZoneId fuso = ZoneId.of(configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow().getFuso());
        LocalDate primeiroDia = visao == Visao.SEMANA ? data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : data;
        LocalDate diaSeguinte = primeiroDia.plusDays(visao == Visao.SEMANA ? 7 : 1);
        OffsetDateTime de = utc(primeiroDia.atStartOfDay(fuso).toInstant());
        OffsetDateTime ate = utc(diaSeguinte.atStartOfDay(fuso).toInstant());

        String filtroDoBarbeiro = alvo == null ? "" : " AND r.barbeiro_id = :barbeiro";
        JdbcClient.StatementSpec consulta = jdbc.sql(SELECIONAR + """
                WHERE r.inicio >= :de AND r.inicio < :ate AND r.status <> 'EXPIRADO'
                  AND NOT (r.status = 'AGUARDANDO_PAGAMENTO' AND r.expira_em <= :agora)
                """ + filtroDoBarbeiro + " ORDER BY r.inicio, r.id")
                .param("de", de).param("ate", ate).param("agora", utc(clock.instant()));
        if (alvo != null) {
            consulta = consulta.param("barbeiro", alvo);
        }
        return consulta.query(LINHA).list();
    }

    /**
     * ADMIN vê todos os barbeiros ou o filtrado; BARBEIRO só o próprio. Barbeiro inexistente, alheio ou conta sem
     * vínculo: 404, sem confirmar que existe (R23).
     */
    private @Nullable UUID barbeiroDoPedido(Escopo escopo, @Nullable UUID barbeiroId) {
        if (escopo.admin()) {
            if (barbeiroId == null) {
                return null;
            }
            boolean existe = jdbc.sql("SELECT count(*) FROM barbeiro WHERE id = :id").param("id", barbeiroId)
                    .query(Long.class).single() > 0;
            if (!existe) {
                throw barbeiroNaoEncontrado();
            }
            return barbeiroId;
        }
        UUID proprio = barbeiroDaConta(escopo.usuarioId());
        if (proprio == null || (barbeiroId != null && !barbeiroId.equals(proprio))) {
            throw barbeiroNaoEncontrado();
        }
        return proprio;
    }

    private @Nullable UUID barbeiroDaConta(UUID usuarioId) {
        return jdbc.sql("SELECT id FROM barbeiro WHERE usuario_id = :usuario").param("usuario", usuarioId)
                .query(UUID.class).optional().orElse(null);
    }

    /**
     * Conclui, marca falta ou cancela (E7: 404, 403, 422 do motivo, 409 da versão e só então a máquina de estados).
     * Roda dentro da transação de quem chama (o admin grava a auditoria nela, E11). A linha fica travada
     * ({@code FOR UPDATE}): dois pedidos com a mesma versão não passam juntos, o segundo vê a versão nova e recebe 409.
     */
    @Transactional
    public Mudanca mudarStatus(Escopo escopo, UUID id, StatusAlvo alvo, @Nullable String motivo, long version) {
        Reserva reserva = em.find(Reserva.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (reserva == null) {
            throw reservaNaoEncontrada();
        }
        if (!escopo.admin() && !reserva.getBarbeiroId().equals(barbeiroDaConta(escopo.usuarioId()))) {
            throw reservaNaoEncontrada();
        }
        if (alvo == StatusAlvo.CANCELADO && !escopo.admin()) {
            throw new ExcecaoDeNegocio(CodigoErro.ACESSO_NEGADO, "Só o administrador cancela uma reserva.");
        }
        String motivoLimpo = motivo == null ? "" : motivo.strip();
        if (alvo == StatusAlvo.CANCELADO) {
            if (motivoLimpo.isEmpty()) {
                throw new ExcecaoDeNegocio(CodigoErro.MOTIVO_OBRIGATORIO, "Informe o motivo do cancelamento.");
            }
            if (motivoLimpo.length() > MOTIVO_MAXIMO) {
                throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                        "O motivo deve ter no máximo " + MOTIVO_MAXIMO + " caracteres.");
            }
        }
        if (reserva.getVersion() != version) {
            throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_DE_VERSAO);
        }

        String de = reserva.getStatus().name();
        Instant agora = clock.instant();
        switch (alvo) {
            case CONCLUIDO -> reserva.fechar(StatusReserva.CONCLUIDO, escopo.usuarioId(), agora);
            case NAO_COMPARECEU -> reserva.fechar(StatusReserva.NAO_COMPARECEU, escopo.usuarioId(), agora);
            case CANCELADO -> reserva.cancelarPelaBarbearia(motivoLimpo, escopo.usuarioId(), agora);
        }
        em.flush();
        if (alvo == StatusAlvo.CANCELADO) {
            // Pelo outbox, na mesma transação: se o pagamento não conseguir tratar, o evento é reenviado (E10).
            eventos.publishEvent(new ReservaCancelada(id));
        }
        return new Mudanca(de, jdbc.sql(SELECIONAR + " WHERE r.id = :id").param("id", id).query(LINHA).single());
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static ExcecaoDeNegocio barbeiroNaoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado.");
    }

    private static ExcecaoDeNegocio reservaNaoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }
}
