package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Bairro;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Cidade;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Painel;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Periodo;

/**
 * A conta do alcance (Fatia 6C): uma consulta agrupa os atendimentos do período por cidade, uf e bairro, e o resto é
 * agregação em memória (poucas dezenas de grupos). Só CONFIRMADO e CONCLUIDO contam (E31), e a reserva manual da 6D fica de fora (D127: sem região, só inflaria "sem região").
 * Sem limite mínimo (D149, decisão do Kevin em 18/11/2026): toda cidade e todo bairro aparece com o número exato, do
 * maior para o menor; a decisão substitui o k-anonimato da E32. Reserva sem região só entra em {@code semRegiao}. O banco nunca devolve nome de pessoa: só região e contagem.
 */
@Service
public class ConsultaAlcance {

    private record Grupo(@Nullable String cidade, @Nullable String uf, @Nullable String bairro, long atendimentos) {
    }

    private record CidadeUf(String cidade, String uf) {
    }

    private final JdbcClient jdbc;

    ConsultaAlcance(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code de} inclusive e {@code ate} exclusivo, em UTC; {@code barbeiroId} nulo = todos. */
    public Painel painel(Periodo periodo, OffsetDateTime de, OffsetDateTime ate, @Nullable UUID barbeiroId) {
        JdbcClient.StatementSpec consulta = jdbc.sql("""
                SELECT regiao_cidade, regiao_uf, regiao_bairro, count(*) AS atendimentos
                FROM reserva
                WHERE status IN ('CONFIRMADO', 'CONCLUIDO') AND origem <> 'MANUAL' AND inicio >= :de AND inicio < :ate
                """ + (barbeiroId == null ? "" : " AND barbeiro_id = :barbeiro") + """
                 GROUP BY regiao_cidade, regiao_uf, regiao_bairro
                """).param("de", de).param("ate", ate);
        if (barbeiroId != null) {
            consulta = consulta.param("barbeiro", barbeiroId);
        }
        List<Grupo> grupos = consulta.query((rs, n) -> new Grupo(rs.getString("regiao_cidade"), rs.getString("regiao_uf"),
                rs.getString("regiao_bairro"), rs.getLong("atendimentos"))).list();
        return agregar(periodo, grupos);
    }

    private static Painel agregar(Periodo periodo, List<Grupo> grupos) {
        long total = grupos.stream().mapToLong(Grupo::atendimentos).sum();
        long semRegiao = 0;
        // Por cidade: quantos atendimentos em cada bairro (bairro nulo = o provedor não informou).
        Map<CidadeUf, Map<String, Long>> porCidade = new LinkedHashMap<>();
        for (Grupo g : grupos) {
            if (g.cidade() == null || g.uf() == null) {
                semRegiao += g.atendimentos();
                continue;
            }
            porCidade.computeIfAbsent(new CidadeUf(g.cidade(), g.uf()), c -> new LinkedHashMap<>())
                    .merge(g.bairro() == null ? "" : g.bairro(), g.atendimentos(), Long::sum);
        }

        List<Cidade> cidades = new ArrayList<>();
        for (Map.Entry<CidadeUf, Map<String, Long>> entrada : porCidade.entrySet()) {
            long daCidade = entrada.getValue().values().stream().mapToLong(Long::longValue).sum();
            List<Bairro> bairros = new ArrayList<>();
            for (Map.Entry<String, Long> bairro : entrada.getValue().entrySet()) {
                if (!bairro.getKey().isEmpty()) { // bairro que o provedor não informou fica só na soma da cidade
                    bairros.add(new Bairro(bairro.getKey(), bairro.getValue(), percentual(bairro.getValue(), total)));
                }
            }
            bairros.sort(Comparator.comparingLong(Bairro::atendimentos).reversed().thenComparing(Bairro::bairro));
            cidades.add(new Cidade(entrada.getKey().cidade(), entrada.getKey().uf(), daCidade, percentual(daCidade, total),
                    bairros));
        }
        cidades.sort(Comparator.comparingLong(Cidade::atendimentos).reversed().thenComparing(Cidade::cidade));
        return new Painel(periodo, total, semRegiao, List.copyOf(cidades));
    }

    /** Sobre o total do período, com 1 casa decimal (arredonda para cima na metade). */
    private static double percentual(long parte, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(parte * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
