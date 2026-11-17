package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
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

    public Pagina listar(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                    "pagina deve ser >= 0 e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
        List<Item> itens = jdbc.sql("""
                SELECT id, ator_id, acao, recurso, recurso_id, detalhe::text AS detalhe, ocorrido_em
                FROM auditoria_admin ORDER BY ocorrido_em DESC, id LIMIT :limite OFFSET :deslocamento
                """)
                .param("limite", tamanho).param("deslocamento", (long) pagina * tamanho)
                .query((rs, n) -> new Item(rs.getObject("id", UUID.class), rs.getObject("ator_id", UUID.class),
                        rs.getString("acao"), rs.getString("recurso"), rs.getObject("recurso_id", UUID.class),
                        FormatoDeData.formatar(rs.getObject("ocorrido_em", OffsetDateTime.class).toInstant(), fuso),
                        rs.getString("detalhe") == null ? null : json.readTree(rs.getString("detalhe"))))
                .list();
        long total = jdbc.sql("SELECT count(*) FROM auditoria_admin").query(Long.class).single();
        return new Pagina(itens, pagina, tamanho, total);
    }
}
