package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A42 da Fatia 14C (BOLA): o barbeiro convidado vê só a própria agenda nas duas rotas novas; pedir a do Eduardo é 404,
 * igual a "não existe" (R23); CLIENTE é 403; sem token, 401. Conta BARBEIRO sem vínculo também é 404.
 */
class AgendaIsolamentoIT extends AgendaNovaBase {

    @Test
    @DisplayName("A42: conta do Bruno vê só as próprias reservas (01/12 com 1) · com o barbeiroId do Eduardo 404 · CLIENTE 403 · sem token 401, nas duas rotas")
    void soAPropria() throws Exception {
        MassaDaAgenda m = massaDaAgenda();

        String mes = corpo("/admin/agenda/mes?mes=2026-12", m.bruno());
        assertThat(dia(mes, "2026-12-01", "reservas")).isEqualTo(1);
        assertThat(dia(mes, "2026-12-02", "folga")).isEqualTo(false); // a folga é do Eduardo
        assertThat(dia(mes, "2026-12-31", "reservas")).isEqualTo(0);
        String dia = corpo("/admin/agenda/dia?data=2026-12-01", m.bruno());
        assertThat(JsonPath.<List<String>>read(dia, "$.colunas[*].barbeiroId"))
                .containsExactly(m.barbeiroBruno().toString());
        assertThat(JsonPath.<List<String>>read(dia, "$.colunas[*].agendamentos[*].id"))
                .containsExactly(m.rBruno().toString());
        assertThat(dia).doesNotContain(m.r0900().toString(), m.barbeiroEduardo().toString());

        Conta cliente = conta("CLIENTE");
        Conta semVinculo = conta("BARBEIRO");
        for (String rota : List.of("/admin/agenda/mes?mes=2026-12", "/admin/agenda/dia?data=2026-12-01")) {
            get(rota + "&barbeiroId=" + m.barbeiroEduardo(), m.bruno()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
            get(rota, cliente).andExpect(status().isForbidden());
            mvc.perform(MockMvcRequestBuilders.get(rota)).andExpect(status().isUnauthorized());
            get(rota, semVinculo).andExpect(status().isNotFound());
        }
    }
}
