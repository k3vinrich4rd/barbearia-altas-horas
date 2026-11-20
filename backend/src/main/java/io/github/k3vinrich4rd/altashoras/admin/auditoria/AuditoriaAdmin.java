package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/** Consulta paginada da auditoria, as ações mais recentes primeiro. O IP fica no banco e não sai na API (contrato 1.4.0). */
@Service
@Transactional(readOnly = true)
public class AuditoriaAdmin {

    static final int TAMANHO_MAXIMO = 50;

    public record Item(UUID id, UUID atorId, String acao, String recurso, @Nullable UUID recursoId, String ocorridoEm,
            @Nullable JsonNode detalhe) {
    }

    public record Pagina(List<Item> itens, int pagina, int tamanho, long total) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ZoneId fuso;

    AuditoriaAdmin(JdbcClient jdbc, ObjectMapper json, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.jdbc = jdbc;
        this.json = json;
        this.fuso = ZoneId.of(fuso);
    }

    /** As ações que o painel grava (lista fechada, a mesma do contrato 1.8.0): o filtro por {@code acao} só aceita estas. */
    static final Set<String> ACOES = Set.of("AGENDAMENTO_MANUAL_CRIADO", "AGENDAMENTO_CONCLUIDO",
            "AGENDAMENTO_NAO_COMPARECEU", "AGENDAMENTO_CANCELADO", "BARBEIRO_CRIADO", "BARBEIRO_ALTERADO",
            "JORNADA_SUBSTITUIDA", "FOLGA_CRIADA", "FOLGA_REMOVIDA", "SERVICO_CRIADO", "SERVICO_ALTERADO",
            "CONFIGURACAO_ALTERADA", "DEVOLUCAO_REGISTRADA", "EVENTO_REVISADO");

    /** {@code acao} nula ou vazia = todas; fora da lista, 400. O valor vai como parâmetro da consulta e nunca para o log. */
    public Pagina listar(int pagina, int tamanho, @Nullable String acao) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                    "pagina deve ser >= 0 e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
        String filtro = acao == null || acao.isBlank() ? null : acao;
        if (filtro != null && !ACOES.contains(filtro)) {
            throw ExcecaoDeNegocio.campoInvalido("acao", "Ação desconhecida.");
        }
        List<Item> itens = jdbc.sql("""
                SELECT id, ator_id, acao, recurso, recurso_id, detalhe::text AS detalhe, ocorrido_em
                FROM auditoria_admin WHERE (CAST(:acao AS varchar) IS NULL OR acao = :acao)
                ORDER BY ocorrido_em DESC, id LIMIT :limite OFFSET :deslocamento
                """)
                .param("acao", filtro).param("limite", tamanho).param("deslocamento", (long) pagina * tamanho)
                .query((rs, n) -> new Item(rs.getObject("id", UUID.class), rs.getObject("ator_id", UUID.class),
                        rs.getString("acao"), rs.getString("recurso"), rs.getObject("recurso_id", UUID.class),
                        FormatoDeData.formatar(rs.getObject("ocorrido_em", OffsetDateTime.class).toInstant(), fuso),
                        rs.getString("detalhe") == null ? null : json.readTree(rs.getString("detalhe"))))
                .list();
        long total = jdbc.sql("SELECT count(*) FROM auditoria_admin WHERE (CAST(:acao AS varchar) IS NULL OR acao = :acao)")
                .param("acao", filtro).query(Long.class).single();
        return new Pagina(itens, pagina, tamanho, total);
    }
}
