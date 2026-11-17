package io.github.k3vinrich4rd.altashoras.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A8 e A9: quem pode chamar o quê no painel (a matriz de papéis da spec, E2). */
class AcessoAdminIT extends PainelBase {

    private ResultActions chamar(HttpMethod metodo, String rota, Conta quem, String corpo) throws Exception {
        var pedido = MockMvcRequestBuilders.request(metodo, rota).contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
        if (quem != null) {
            pedido.header("Authorization", "Bearer " + quem.token());
        }
        return mvc.perform(pedido);
    }

    private ResultActions agendaDoDia(Conta quem) throws Exception {
        return chamar(HttpMethod.GET, "/admin/agenda?data=2026-12-01", quem, "");
    }

    @Test
    @DisplayName("A8: sem token é 401, CLIENTE é 403 ACESSO_NEGADO e ADMIN ou BARBEIRO com senha inicial é 403 TROCA_DE_SENHA_OBRIGATORIA em todas as rotas")
    void acessoAoPainel() throws Exception {
        UUID reserva = UUID.randomUUID();
        String corpo = corpoDeStatus("CONCLUIDO", 0);
        Conta cliente = conta("CLIENTE");
        Conta adminComSenhaInicial = conta(true, "ADMIN", "BARBEIRO");
        Conta barbeiroComSenhaInicial = conta(true, "BARBEIRO");

        String[][] rotas = {
                {"GET", "/admin/agenda?data=2026-12-01"},
                {"PATCH", "/admin/agendamentos/" + reserva + "/status"},
                {"GET", "/admin/pagamentos"},
                {"GET", "/admin/auditoria"}};
        for (String[] rota : rotas) {
            HttpMethod metodo = HttpMethod.valueOf(rota[0]);
            chamar(metodo, rota[1], null, corpo).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
            chamar(metodo, rota[1], cliente, corpo).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
            chamar(metodo, rota[1], adminComSenhaInicial, corpo).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TROCA_DE_SENHA_OBRIGATORIA"));
            chamar(metodo, rota[1], barbeiroComSenhaInicial, corpo).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TROCA_DE_SENHA_OBRIGATORIA"));
        }
    }

    @Test
    @DisplayName("A9: pagamentos, devolução, eventos e auditoria dão 403 ACESSO_NEGADO para só BARBEIRO; quem tem ADMIN e BARBEIRO nunca recebe 403")
    void soAdminNoDinheiro() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        Conta eduardo = conta("ADMIN", "BARBEIRO");
        UUID inexistente = UUID.randomUUID();
        String[][] rotas = {
                {"GET", "/admin/pagamentos", "200"},
                {"POST", "/admin/pagamentos/" + inexistente + "/devolucao", "404"},
                {"GET", "/admin/pagamentos/eventos", "200"},
                {"POST", "/admin/pagamentos/eventos/" + inexistente + "/revisao", "404"},
                {"GET", "/admin/auditoria", "200"}};
        for (String[] rota : rotas) {
            HttpMethod metodo = HttpMethod.valueOf(rota[0]);
            chamar(metodo, rota[1], barbeiro, "").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
            chamar(metodo, rota[1], eduardo, "").andExpect(status().is(Integer.parseInt(rota[2])));
        }
        agendaDoDia(barbeiro).andExpect(status().isNotFound());
    }
}
