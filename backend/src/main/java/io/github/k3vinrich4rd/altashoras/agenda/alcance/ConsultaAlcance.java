package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.K_MINIMO;
import static io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.OUTRAS_CIDADES;
import static io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.OUTROS_BAIRROS;

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
 * agregação em memória (poucas dezenas de grupos). Só CONFIRMADO e CONCLUIDO contam (E31). K-anonimato (E32): o bairro com
 * menos de 5 atendimentos na cidade vira "Outros bairros"; a cidade com menos de 5 vira "Outras cidades", sem uf e sem
 * bairros. Reserva sem região só entra em {@code semRegiao}. O banco nunca devolve nome de pessoa: só região e contagem.
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
                WHERE status IN ('CONFIRMADO', 'CONCLUIDO') AND inicio >= :de AND inicio < :ate
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
        long deOutrasCidades = 0;
        for (Map.Entry<CidadeUf, Map<String, Long>> entrada : porCidade.entrySet()) {
            long daCidade = entrada.getValue().values().stream().mapToLong(Long::longValue).sum();
            if (daCidade < K_MINIMO) {
                deOutrasCidades += daCidade;
                continue;
            }
            List<Bairro> bairros = new ArrayList<>();
            long deOutrosBairros = 0;
            for (Map.Entry<String, Long> bairro : entrada.getValue().entrySet()) {
                if (!bairro.getKey().isEmpty() && bairro.getValue() >= K_MINIMO) {
                    bairros.add(new Bairro(bairro.getKey(), bairro.getValue(), percentual(bairro.getValue(), total)));
                } else {
                    deOutrosBairros += bairro.getValue();
                }
            }
            bairros.sort(Comparator.comparingLong(Bairro::atendimentos).reversed().thenComparing(Bairro::bairro));
            if (deOutrosBairros > 0) {
                bairros.add(new Bairro(OUTROS_BAIRROS, deOutrosBairros, percentual(deOutrosBairros, total)));
            }
            cidades.add(new Cidade(entrada.getKey().cidade(), entrada.getKey().uf(), daCidade, percentual(daCidade, total),
                    bairros));
        }
        cidades.sort(Comparator.comparingLong(Cidade::atendimentos).reversed().thenComparing(Cidade::cidade));
        if (deOutrasCidades > 0) {
            cidades.add(new Cidade(OUTRAS_CIDADES, null, deOutrasCidades, percentual(deOutrasCidades, total), List.of()));
        }
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
