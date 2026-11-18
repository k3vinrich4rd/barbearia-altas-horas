package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A55: depois de 1 ano o IP da auditoria vira nulo, mas a linha (quem fez o quê) fica (D77, E20, D-2). */
class RetencaoAuditoriaIT extends PrivacidadeBase {

    @Autowired
    RetencaoDeDados retencao;

    private UUID auditoria(UUID ator, String acao, OffsetDateTime quando) {
        return jdbc.queryForObject("""
                INSERT INTO auditoria_admin (ator_id, acao, recurso, recurso_id, detalhe, ip, ocorrido_em)
                VALUES (?, ?, 'agendamento', gen_random_uuid(), '{"de":"CONFIRMADO","para":"CONCLUIDO"}'::jsonb, ?, ?)
                RETURNING id
                """, UUID.class, ator, acao, IP, quando);
    }

    @Test
    @DisplayName("A55: auditoria de 01/12/2025 09:30 (365 dias), de 01/12/2025 09:31 e de 30/11/2026: só a primeira perde o IP, as três mantêm ator, ação, recurso e detalhe, e GET /admin/auditoria segue sem ip")
    void soOIpDaMaisAntigaSai() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID exata = auditoria(admin.id(), "ACAO_EXATA", DadosDeTeste.em(LocalDate.of(2025, 12, 1), 9, 30));
        UUID umMinutoDepois = auditoria(admin.id(), "ACAO_UM_MINUTO_DEPOIS", DadosDeTeste.em(LocalDate.of(2025, 12, 1), 9, 31));
        UUID recente = auditoria(admin.id(), "ACAO_RECENTE", DadosDeTeste.em(LocalDate.of(2026, 11, 30), 9, 30));
        Map<UUID, String> antes = Map.of(exata, resto(exata), umMinutoDepois, resto(umMinutoDepois), recente, resto(recente));

        retencao.executar();

        assertThat(ip(exata)).isNull();
        assertThat(ip(umMinutoDepois)).isEqualTo(IP);
        assertThat(ip(recente)).isEqualTo(IP);
        // Ator, ação, recurso, recurso_id, detalhe e data continuam exatamente como estavam.
        antes.forEach((id, linha) -> assertThat(resto(id)).isEqualTo(linha));
        assertThat(resto(exata)).contains("ACAO_EXATA").contains(admin.id().toString());

        String corpo = get("/admin/auditoria", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("\"ip\"").doesNotContain(IP);
        List<String> acoes = com.jayway.jsonpath.JsonPath.read(corpo, "$.itens[*].acao");
        assertThat(acoes).containsExactlyInAnyOrder("ACAO_EXATA", "ACAO_UM_MINUTO_DEPOIS", "ACAO_RECENTE");
    }

    private String ip(UUID id) {
        return jdbc.queryForObject("SELECT ip FROM auditoria_admin WHERE id = ?", String.class, id);
    }

    /** A linha inteira, menos o IP. */
    private String resto(UUID id) {
        return jdbc.queryForObject("SELECT (to_jsonb(a) - 'ip')::text FROM auditoria_admin a WHERE id = ?", String.class, id);
    }
}
