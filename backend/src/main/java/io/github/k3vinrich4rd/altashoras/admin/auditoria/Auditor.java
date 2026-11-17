package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.k3vinrich4rd.altashoras.admin.Ator;

/**
 * Grava uma linha em {@code auditoria_admin} por ação bem-sucedida do painel (E11). {@code MANDATORY}: só roda dentro
 * da transação da própria ação, então se a gravação falhar a ação desfaz, e se a ação falhar nada é gravado.
 * O {@code detalhe} é uma lista fechada de campos técnicos (de/para do status, ids): nunca motivo, nome, WhatsApp,
 * e-mail ou token (R30). Quem chama monta o mapa só com isso.
 */
@Component
public class Auditor {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    Auditor(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Ator ator, String acao, String recurso, @Nullable UUID recursoId, Map<String, String> detalhe) {
        jdbc.sql("""
                INSERT INTO auditoria_admin (ator_id, acao, recurso, recurso_id, detalhe, ip, ocorrido_em)
                VALUES (:ator, :acao, :recurso, :recursoId, CAST(:detalhe AS jsonb), :ip, :quando)
                """)
                .param("ator", ator.id()).param("acao", acao).param("recurso", recurso).param("recursoId", recursoId)
                .param("detalhe", json.writeValueAsString(detalhe)).param("ip", ator.ip())
                .param("quando", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }
}
