package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Escopo;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Item;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;

/**
 * A agenda nova do painel (Fatia 14C): o calendário do mês e a grade do dia. Só leitura. O escopo é o mesmo do
 * {@link PainelDaAgenda} ({@link AcessoAgenda}: ADMIN vê todos ou o filtrado; BARBEIRO só o próprio; o resto é 404).
 * <p>
 * O que ocupa a cadeira (E14): CONFIRMADO, CONCLUIDO, NAO_COMPARECEU e AGUARDANDO_PAGAMENTO ainda no prazo, inclusive a
 * reserva manual. O dia é o dia civil no fuso da barbearia. Sem N+1 (E16): o mês é uma consulta de reservas
 * ({@code GROUP BY} do dia no fuso), uma de folgas e uma de jornadas; o dia é uma de barbeiros, uma de reservas, uma de
 * folgas e uma de jornadas, qualquer que seja o número de barbeiros.
 */
@Component
class VisaoDaAgenda {

    /** Situação de um horário da grade, em ordem de prioridade (E15): OCUPADO > FOLGA > ALMOCO > LIVRE. */
    enum Situacao { OCUPADO, FOLGA, ALMOCO, LIVRE }

    private static final String OCUPA = """
            (r.status IN ('CONFIRMADO', 'CONCLUIDO', 'NAO_COMPARECEU')
             OR (r.status = 'AGUARDANDO_PAGAMENTO' AND r.expira_em > :agora))
            """;

    private final JdbcClient jdbc;
    private final ConfiguracaoAgendaRepository configuracoes;
    private final AcessoAgenda acesso;
    private final Clock clock;

    VisaoDaAgenda(JdbcClient jdbc, ConfiguracaoAgendaRepository configuracoes, AcessoAgenda acesso, Clock clock) {
        this.jdbc = jdbc;
        this.configuracoes = configuracoes;
        this.acesso = acesso;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PainelDaAgenda.ResumoDoMes mes(Escopo escopo, YearMonth mes, @Nullable UUID barbeiroId) {
        UUID alvo = acesso.alvo(escopo, barbeiroId);
        ZoneId fuso = fuso();
        LocalDate primeiro = mes.atDay(1);
        LocalDate seguinte = mes.plusMonths(1).atDay(1);
        OffsetDateTime de = utc(primeiro.atStartOfDay(fuso).toInstant());
        OffsetDateTime ate = utc(seguinte.atStartOfDay(fuso).toInstant());

        // 1. reservas que ocupam, contadas por dia civil no fuso (uma reserva às 22:00 do dia 31 não cai no dia 1º)
        Map<LocalDate, Integer> porDia = new HashMap<>();
        comAlvo(jdbc.sql("""
                SELECT (r.inicio AT TIME ZONE :fuso)::date AS dia, count(*) AS total
                FROM reserva r JOIN barbeiro b ON b.id = r.barbeiro_id
                WHERE r.inicio >= :de AND r.inicio < :ate AND
                """ + OCUPA + (alvo == null ? " AND b.ativo" : filtro("r.barbeiro_id", alvo)) + " GROUP BY 1")
                .param("fuso", fuso.getId()).param("de", de).param("ate", ate).param("agora", utc(clock.instant())), alvo)
                .query((rs, n) -> Map.entry(rs.getObject("dia", LocalDate.class), rs.getInt("total"))).list()
                .forEach(e -> porDia.put(e.getKey(), e.getValue()));

        // 2. folgas que tocam o mês; 3. dias da semana com jornada (só barbeiros ativos quando não há filtro)
        List<Periodo> folgas = folgas(alvo, de, ate);
        Set<Integer> diasComJornada = new HashSet<>(comAlvo(jdbc.sql("""
                SELECT DISTINCT j.dia_semana FROM jornada j JOIN barbeiro b ON b.id = j.barbeiro_id
                WHERE
                """ + (alvo == null ? " b.ativo" : " j.barbeiro_id = :barbeiro")), alvo)
                .query((rs, n) -> rs.getInt("dia_semana")).list());

        List<PainelDaAgenda.DiaDoMes> dias = new ArrayList<>();
        for (LocalDate dia = primeiro; dia.isBefore(seguinte); dia = dia.plusDays(1)) {
            Instant inicio = dia.atStartOfDay(fuso).toInstant();
            Instant fim = dia.plusDays(1).atStartOfDay(fuso).toInstant();
            boolean folga = folgas.stream().anyMatch(f -> f.inicio().isBefore(fim) && f.fim().isAfter(inicio));
            dias.add(new PainelDaAgenda.DiaDoMes(dia, porDia.getOrDefault(dia, 0), folga,
                    diasComJornada.contains(dia.getDayOfWeek().getValue())));
        }
        return new PainelDaAgenda.ResumoDoMes(mes, dias);
    }

    @Transactional(readOnly = true)
    PainelDaAgenda.GradeDoDia dia(Escopo escopo, LocalDate data, @Nullable UUID barbeiroId) {
        UUID alvo = acesso.alvo(escopo, barbeiroId);
        ConfiguracaoAgenda config = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
        ZoneId fuso = ZoneId.of(config.getFuso());
        int passo = config.getGranularidadeMin();
        OffsetDateTime de = utc(data.atStartOfDay(fuso).toInstant());
        OffsetDateTime ate = utc(data.plusDays(1).atStartOfDay(fuso).toInstant());

        // 1. barbeiros visíveis, na ordem do nome (o filtrado aparece mesmo desligado: o ADMIN pediu por ele)
        Map<UUID, String> barbeiros = new LinkedHashMap<>();
        comAlvo(jdbc.sql("SELECT id, nome_exibicao FROM barbeiro WHERE "
                + (alvo == null ? "ativo" : "id = :barbeiro") + " ORDER BY nome_exibicao, id"), alvo)
                .query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("nome_exibicao"))).list()
                .forEach(e -> barbeiros.put(e.getKey(), e.getValue()));

        // 2. jornada do dia da semana de todos eles, numa consulta
        Map<UUID, List<LocalTime[]>> jornadas = new HashMap<>();
        comAlvo(jdbc.sql("""
                SELECT j.barbeiro_id, j.hora_inicio, j.hora_fim FROM jornada j JOIN barbeiro b ON b.id = j.barbeiro_id
                WHERE j.dia_semana = :diaSemana AND
                """ + (alvo == null ? " b.ativo" : " j.barbeiro_id = :barbeiro") + " ORDER BY j.hora_inicio")
                .param("diaSemana", data.getDayOfWeek().getValue()), alvo)
                .query((rs, n) -> Map.entry(rs.getObject("barbeiro_id", UUID.class),
                        new LocalTime[] {rs.getObject("hora_inicio", LocalTime.class), rs.getObject("hora_fim", LocalTime.class)}))
                .list().forEach(e -> jornadas.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        // 3. folgas e 4. reservas que ocupam (com o fim da ocupação, para o intervalo entre atendimentos)
        List<Periodo> folgas = folgas(alvo, de, ate);
        String consulta = PainelDaAgenda.SELECIONAR.replaceFirst("SELECT ", "SELECT r.fim_ocupacao, ")
                + " WHERE r.inicio >= :de AND r.inicio < :ate AND " + OCUPA + filtro("r.barbeiro_id", alvo) + " ORDER BY r.inicio, r.id";
        List<Ocupacao> reservas = comAlvo(jdbc.sql(consulta).param("de", de).param("ate", ate)
                .param("agora", utc(clock.instant())), alvo)
                .query((rs, n) -> new Ocupacao(PainelDaAgenda.linha(rs, n),
                        rs.getObject("fim_ocupacao", OffsetDateTime.class).toInstant()))
                .list();

        List<PainelDaAgenda.ColunaDoDia> colunas = new ArrayList<>();
        for (Map.Entry<UUID, String> barbeiro : barbeiros.entrySet()) {
            UUID id = barbeiro.getKey();
            List<Ocupacao> dele = reservas.stream().filter(o -> o.item().barbeiroId().equals(id)).toList();
            List<Periodo> folgasDele = folgas.stream().filter(f -> f.barbeiroId().equals(id)).toList();
            List<LocalTime[]> intervalos = jornadas.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparing(i -> i[0])).toList();
            colunas.add(new PainelDaAgenda.ColunaDoDia(id, barbeiro.getValue(),
                    horarios(data, fuso, passo, intervalos, dele, folgasDele), dele.stream().map(Ocupacao::item).toList()));
        }
        return new PainelDaAgenda.GradeDoDia(data, passo, colunas);
    }

    /** Do primeiro início ao último fim da jornada, de {@code passo} em {@code passo} minutos (E15). */
    private static List<PainelDaAgenda.HorarioDoDia> horarios(LocalDate data, ZoneId fuso, int passo,
            List<LocalTime[]> intervalos, List<Ocupacao> reservas, List<Periodo> folgas) {
        if (intervalos.isEmpty()) {
            return List.of();
        }
        LocalTime primeiro = intervalos.getFirst()[0];
        LocalTime ultimoFim = intervalos.stream().map(i -> i[1]).max(Comparator.naturalOrder()).orElseThrow();
        List<PainelDaAgenda.HorarioDoDia> horarios = new ArrayList<>();
        for (LocalTime hora = primeiro; hora.isBefore(ultimoFim); hora = hora.plusMinutes(passo)) {
            Instant instante = data.atTime(hora).atZone(fuso).toInstant();
            LocalTime doHorario = hora;
            Ocupacao ocupa = reservas.stream()
                    .filter(o -> !instante.isBefore(o.item().inicio()) && instante.isBefore(o.fimOcupacao()))
                    .findFirst().orElse(null);
            Situacao situacao;
            if (ocupa != null) {
                situacao = Situacao.OCUPADO;
            } else if (folgas.stream().anyMatch(f -> !instante.isBefore(f.inicio()) && instante.isBefore(f.fim()))) {
                situacao = Situacao.FOLGA;
            } else if (intervalos.stream().noneMatch(i -> !doHorario.isBefore(i[0]) && doHorario.isBefore(i[1]))) {
                situacao = Situacao.ALMOCO; // o vão entre dois intervalos da jornada
            } else {
                situacao = Situacao.LIVRE;
            }
            horarios.add(new PainelDaAgenda.HorarioDoDia(hora, situacao.name(), ocupa == null ? null : ocupa.item().id()));
            if (hora.plusMinutes(passo).isBefore(hora)) {
                break; // virou a meia-noite
            }
        }
        return horarios;
    }

    private List<Periodo> folgas(@Nullable UUID alvo, OffsetDateTime de, OffsetDateTime ate) {
        return comAlvo(jdbc.sql("""
                SELECT f.barbeiro_id, f.inicio, f.fim FROM folga f JOIN barbeiro b ON b.id = f.barbeiro_id
                WHERE f.inicio < :ate AND f.fim > :de AND
                """ + (alvo == null ? " b.ativo" : " f.barbeiro_id = :barbeiro")).param("de", de).param("ate", ate), alvo)
                .query((rs, n) -> new Periodo(rs.getObject("barbeiro_id", UUID.class),
                        rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                        rs.getObject("fim", OffsetDateTime.class).toInstant()))
                .list();
    }

    private static String filtro(String coluna, @Nullable UUID alvo) {
        return alvo == null ? "" : " AND " + coluna + " = :barbeiro";
    }

    private static JdbcClient.StatementSpec comAlvo(JdbcClient.StatementSpec consulta, @Nullable UUID alvo) {
        return alvo == null ? consulta : consulta.param("barbeiro", alvo);
    }

    private ZoneId fuso() {
        return ZoneId.of(configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow().getFuso());
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private record Periodo(UUID barbeiroId, Instant inicio, Instant fim) {
    }

    private record Ocupacao(Item item, Instant fimOcupacao) {
    }
}
