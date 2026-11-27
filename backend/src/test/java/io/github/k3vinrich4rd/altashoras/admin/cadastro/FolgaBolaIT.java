package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A15 e A16: folga de outro barbeiro é 404 (R23) e só o ADMIN mexe em folgas, nem o próprio barbeiro (H27, E8). */
class FolgaBolaIT extends FeriasBase {

    @Test
    @DisplayName("A15: PUT e DELETE em /admin/barbeiros/{Eduardo}/folgas/{F2}, com F2 de outro barbeiro, dão 404 nos dois e F2 fica intacta")
    void folgaDeOutroBarbeiro() throws Exception {
        UUID outro = DadosDeTeste.barbeiro(jdbc, true);
        String f2 = idDe(postJson(rotaDasFolgas(outro), admin, corpo(dez(5, 10, 0), dez(5, 11, 0), "FOLGA", null, null))
                .andExpect(status().isCreated()));

        alterarFolga(f2, corpo(dez(5, 14, 0), dez(5, 16, 0), "FERIAS", null, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        delete(rotaDasFolgas(eduardo) + "/" + f2, admin).andExpect(status().isNotFound());

        Map<String, Object> linha = jdbc.queryForMap("SELECT barbeiro_id, tipo, atualizado_em FROM folga WHERE id = ?::uuid", f2);
        assertThat(linha.get("barbeiro_id")).isEqualTo(outro);
        assertThat(jdbc.queryForObject("SELECT inicio FROM folga WHERE id = ?::uuid", OffsetDateTime.class, f2).toInstant())
                .isEqualTo(dez(5, 10, 0).toInstant());
        assertThat(linha.get("tipo")).isEqualTo("FOLGA");
        assertThat(linha.get("atualizado_em")).isNull();
    }

    @Test
    @DisplayName("A16: a conta BARBEIRO vinculada ao Eduardo recebe 403 ACESSO_NEGADO em GET, POST, PUT e DELETE /folgas e em GET /folgas/conflitos do próprio Eduardo")
    void barbeiroNaoMexeEmFolga() throws Exception {
        Conta doEduardo = conta("BARBEIRO");
        jdbc.update("UPDATE barbeiro SET usuario_id = ? WHERE id = ?", doEduardo.id(), eduardo);
        String existente = idDe(criarFolga(corpo(dez(5, 10, 0), dez(5, 11, 0), "FOLGA", null, null))
                .andExpect(status().isCreated()));
        String rota = rotaDasFolgas(eduardo);
        String corpo = corpo(dez(5, 14, 0), dez(5, 16, 0), "FOLGA", null, null);

        get(rota, doEduardo).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        postJson(rota, doEduardo, corpo).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        put(rota + "/" + existente, doEduardo, corpo).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        delete(rota + "/" + existente, doEduardo).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        conflitos(eduardo, doEduardo, dez(5, 0, 0), dez(6, 0, 0)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));

        assertThat(folgasGravadas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atualizado_em FROM folga WHERE id = ?::uuid", OffsetDateTime.class, existente))
                .isNull();
    }
}
