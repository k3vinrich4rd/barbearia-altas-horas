package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
import io.github.k3vinrich4rd.altashoras.agenda.reserva.ReservarHorario;
import io.github.k3vinrich4rd.altashoras.agenda.reserva.StatusReserva;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o painel (ADR 0002, E1): a agenda do dia ou da semana e o fechamento do atendimento.
 * O módulo admin chama este serviço e grava a auditoria na mesma transação; ele nunca lê a tabela da reserva.
 * Quem pode mexer na agenda de quem é da política única {@link AcessoAgenda}: o ADMIN vê e fecha a de qualquer barbeiro;
 * o BARBEIRO só a própria, e o que não é dele responde 404, igual a "não existe" (R23, H9).
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
            @Nullable Instant expiraEm, @Nullable String motivoCancelamento, long version, @Nullable String clienteNome,
            @Nullable String clienteWhatsapp, @Nullable String observacao) {

        @Override
        public String toString() {
            return "Item[" + id + ", " + status + "]";
        }
    }

    /** O resultado do fechamento: o status de antes (a auditoria guarda de/para) e a reserva já atualizada. */
    public record Mudanca(String de, Item item) {
    }

    /**
     * O horário combinado por WhatsApp que a barbearia trava no painel (Fatia 6D). Nome e observação são opcionais, já
     * aparados e com tamanho conferido pela rota; são dado pessoal, então o toString não os imprime (R30).
     */
    public record NovaReservaManual(UUID servicoId, UUID barbeiroId, OffsetDateTime inicio, @Nullable String nomeCliente,
            @Nullable String observacao) {

        @Override
        public String toString() {
            return "NovaReservaManual[dados pessoais omitidos]";
        }
    }

    // ---- cadastros do painel (5B) ----------------------------------------------------------------------------------

    /** Resultado de uma alteração: o item já atualizado e os NOMES dos campos que mudaram (a auditoria guarda só isso). */
    public record Alterado<T>(T item, List<String> campos) {
    }

    public record NovoServico(String nome, @Nullable String descricao, int duracaoMin, long precoCentavos) {
    }

    public record AlteracaoDeServico(String nome, @Nullable String descricao, int duracaoMin, long precoCentavos,
            boolean ativo, long version) {
    }

    public record ServicoCadastrado(UUID id, String nome, @Nullable String descricao, int duracaoMin,
            long precoCentavos, boolean ativo, long version) {
    }

    /** Um intervalo de trabalho num dia da semana (1 = segunda ... 7 = domingo), em hora local da barbearia. */
    public record Intervalo(int diaSemana, LocalTime horaInicio, LocalTime horaFim) {
    }

    /** O barbeiro da conta e a jornada dele (D142): o id serve à reserva manual do BARBEIRO, os intervalos ao almoço. */
    public record JornadaDoBarbeiro(UUID barbeiroId, String nomeExibicao, List<Intervalo> intervalos) {
    }

    public record AlteracaoDeBarbeiro(String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp,
            boolean ativo, long version) {
    }

    public record BarbeiroCadastrado(UUID id, String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp,
            boolean ativo, long version, List<Intervalo> jornada) {
    }

    /** O motivo da folga pode ser pessoal (consulta médica): o toString não o imprime (R30). */
    public record FolgaCadastrada(UUID id, Instant inicio, Instant fim, @Nullable String motivo) {

        @Override
        public String toString() {
            return "FolgaCadastrada[" + id + "]";
        }
    }

    public record NovaConfiguracao(int antecedenciaMinimaMin, int intervaloEntreAtendimentosMin, int granularidadeMin,
            int horizonteDias, int expiracaoPagamentoMin, long version) {
    }

    public record ConfiguracaoCadastrada(String fuso, int antecedenciaMinimaMin, int intervaloEntreAtendimentosMin,
            int granularidadeMin, int horizonteDias, int expiracaoPagamentoMin, long version) {
    }

    private static final int MOTIVO_MAXIMO = 300;

    private static final String SELECIONAR = """
            SELECT r.id, r.status, r.origem, r.servico_id, s.nome AS servico_nome, s.duracao_min,
                   r.barbeiro_id, b.nome_exibicao AS barbeiro_nome, r.inicio, r.fim, r.preco_centavos, r.expira_em,
                   r.motivo_cancelamento, r.version,
                   COALESCE(g.nome, u.nome, r.nome_cliente) AS cliente_nome,
                   COALESCE(g.whatsapp, u.whatsapp) AS cliente_whatsapp, r.observacao
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
                rs.getLong("version"), rs.getString("cliente_nome"), rs.getString("cliente_whatsapp"),
                rs.getString("observacao"));
    };

    private final JdbcClient jdbc;
    private final EntityManager em;
    private final ConfiguracaoAgendaRepository configuracoes;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final CadastrosDoPainel cadastros;
    private final ReservarHorario reservas;
    private final AcessoAgenda acesso;

    PainelDaAgenda(JdbcClient jdbc, EntityManager em, ConfiguracaoAgendaRepository configuracoes,
            ApplicationEventPublisher eventos, Clock clock, CadastrosDoPainel cadastros, ReservarHorario reservas,
            AcessoAgenda acesso) {
        this.acesso = acesso;
        this.cadastros = cadastros;
        this.reservas = reservas;
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
        UUID alvo = acesso.alvo(escopo, barbeiroId);
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
     * Trava um horário combinado por fora do app (Fatia 6D, R33). ADMIN escolhe qualquer barbeiro; BARBEIRO só o próprio,
     * com a mesma regra do painel: barbeiro alheio, inexistente ou conta sem vínculo é 404. A reserva nasce
     * {@code CONFIRMADO}, sem cobrança, e respeita a grade, o almoço, a folga e a constraint do banco (409
     * {@code HORARIO_INDISPONIVEL}). {@code aoGravar} (a auditoria) roda dentro da transação da reserva. Esta rota NÃO é
     * {@code @Transactional}: o caso de uso da reserva abre a própria transação, para o deadlock poder ser refeito (E4).
     */
    public Item criarReservaManual(Escopo escopo, NovaReservaManual pedido, Consumer<UUID> aoGravar) {
        acesso.alvo(escopo, pedido.barbeiroId());
        UUID id = reservas.comoBarbearia(new ReservarHorario.Manual(pedido.servicoId(), pedido.barbeiroId(),
                pedido.inicio(), pedido.nomeCliente(), pedido.observacao(), escopo.usuarioId()), aoGravar);
        return jdbc.sql(SELECIONAR + " WHERE r.id = :id").param("id", id).query(LINHA).single();
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
        acesso.exigirDonoDaReserva(escopo, reserva.getBarbeiroId());
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

    // ---- cadastros do painel (5B): a agenda é a dona das tabelas; o admin só chama e audita ---------------------------

    public List<ServicoCadastrado> servicos() {
        return cadastros.servicos();
    }

    public ServicoCadastrado criarServico(NovoServico novo) {
        return cadastros.criarServico(novo);
    }

    public Alterado<ServicoCadastrado> alterarServico(UUID id, AlteracaoDeServico alteracao) {
        return cadastros.alterarServico(id, alteracao);
    }

    public List<BarbeiroCadastrado> barbeiros() {
        return cadastros.barbeiros();
    }

    /** Um barbeiro pelo id (o painel precisa do estado de antes para desligar e convidar). Inexistente: 404. */
    public BarbeiroCadastrado barbeiro(UUID id) {
        return cadastros.barbeiro(id);
    }

    public BarbeiroCadastrado criarBarbeiro(String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp) {
        return cadastros.criarBarbeiro(nomeExibicao, usuarioId, whatsapp);
    }

    public Alterado<BarbeiroCadastrado> alterarBarbeiro(UUID id, AlteracaoDeBarbeiro alteracao) {
        return cadastros.alterarBarbeiro(id, alteracao);
    }

    /** Substitui a semana inteira numa transação. Sobreposição no mesmo dia: 422; encostar não é sobrepor (E19). */
    public List<Intervalo> substituirJornada(UUID barbeiroId, List<Intervalo> intervalos) {
        return cadastros.substituirJornada(barbeiroId, intervalos);
    }

    /** A jornada do barbeiro da conta (D142). Sem vínculo: 404. Só leitura; quem edita a jornada é o ADMIN. */
    public JornadaDoBarbeiro minhaJornada(UUID usuarioId) {
        return cadastros.minhaJornada(usuarioId);
    }

    /** As folgas de hoje em diante (dia civil no fuso da barbearia), por início. */
    public List<FolgaCadastrada> folgas(UUID barbeiroId) {
        return cadastros.folgas(barbeiroId);
    }

    /**
     * Cria a folga se nenhuma reserva ativa a atravessa. Trava a linha do barbeiro ({@code FOR UPDATE}), e a reserva
     * trava a mesma linha com {@code FOR SHARE}: folga e reserva nunca passam juntas no mesmo horário (E21).
     */
    public FolgaCadastrada criarFolga(UUID barbeiroId, UUID criadaPor, Instant inicio, Instant fim,
            @Nullable String motivo) {
        return cadastros.criarFolga(barbeiroId, criadaPor, inicio, fim, motivo);
    }

    public void removerFolga(UUID barbeiroId, UUID folgaId) {
        cadastros.removerFolga(barbeiroId, folgaId);
    }

    public ConfiguracaoCadastrada configuracao() {
        return cadastros.configuracao();
    }

    public Alterado<ConfiguracaoCadastrada> alterarConfiguracao(UUID alteradaPor, NovaConfiguracao nova) {
        return cadastros.alterarConfiguracao(alteradaPor, nova);
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static ExcecaoDeNegocio reservaNaoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }
}
