package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A67 a A70: o painel de alcance (GET /admin/alcance), sem limite mínimo (D149), filtro por barbeiro e escopo do BARBEIRO (E31, D149, E36). */
class AlcancePainelIT extends AlcanceBase {

    @Test
    @DisplayName("A67: 6 atendimentos em Centro/Osasco, 2 em Sé/São Paulo, 1 EXPIRADO e 1 CONFIRMADO sem região: total 9 (o EXPIRADO fica fora), semRegiao 1, Osasco com Centro 6 e São Paulo com Sé 2, tudo com o número exato, do maior para o menor, percentuais com 1 casa decimal e nenhum Outras cidades nem abaixoDoLimite")
    void adminVeTudoSemLimiteMinimo() throws Exception {
        cenarioDoA67();

        String corpo = get("/admin/alcance", conta("ADMIN")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.periodo.de").value("2026-10-19"))
                .andExpect(jsonPath("$.periodo.ate").value("2026-11-17"))
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.semRegiao").value(1))
                .andExpect(jsonPath("$.cidades.length()").value(2))
                .andExpect(jsonPath("$.cidades[0].cidade").value("Osasco"))
                .andExpect(jsonPath("$.cidades[0].uf").value("SP"))
                .andExpect(jsonPath("$.cidades[0].atendimentos").value(6))
                .andExpect(jsonPath("$.cidades[0].percentual").value(66.7))
                .andExpect(jsonPath("$.cidades[0].bairros.length()").value(1))
                .andExpect(jsonPath("$.cidades[0].bairros[0].bairro").value("Centro"))
                .andExpect(jsonPath("$.cidades[0].bairros[0].atendimentos").value(6))
                .andExpect(jsonPath("$.cidades[0].bairros[0].percentual").value(66.7))
                .andExpect(jsonPath("$.cidades[1].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.cidades[1].uf").value("SP"))
                .andExpect(jsonPath("$.cidades[1].atendimentos").value(2))
                .andExpect(jsonPath("$.cidades[1].percentual").value(22.2))
                .andExpect(jsonPath("$.cidades[1].bairros.length()").value(1))
                .andExpect(jsonPath("$.cidades[1].bairros[0].bairro").value("Sé"))
                .andExpect(jsonPath("$.cidades[1].bairros[0].atendimentos").value(2))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain("Outras cidades").doesNotContain("Outros bairros")
                .doesNotContain("abaixoDoLimite");
    }

    @Test
    @DisplayName("A68: os bairros de uma cidade aparecem todos, até o de 1 atendimento, do maior para o menor (empate por nome): 5 no Centro, 1 em Presidente Altino e 1 em Vila Yara, com o número exato e o percentual de cada um")
    void todoBairroApareceDoMaiorParaOMenor() throws Exception {
        Catalogo c = catalogo();
        atendimentos(1, c.barbeiro(), c.servico(), "Vila Yara", "Osasco", "SP");
        atendimentos(5, c.barbeiro(), c.servico(), "Centro", "Osasco", "SP");
        atendimentos(1, c.barbeiro(), c.servico(), "Presidente Altino", "Osasco", "SP");

        get("/admin/alcance", conta("ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.cidades.length()").value(1))
                .andExpect(jsonPath("$.cidades[0].atendimentos").value(7))
                .andExpect(jsonPath("$.cidades[0].percentual").value(100.0))
                .andExpect(jsonPath("$.cidades[0].bairros.length()").value(3))
                .andExpect(jsonPath("$.cidades[0].bairros[0].bairro").value("Centro"))
                .andExpect(jsonPath("$.cidades[0].bairros[0].atendimentos").value(5))
                .andExpect(jsonPath("$.cidades[0].bairros[0].percentual").value(71.4))
                .andExpect(jsonPath("$.cidades[0].bairros[1].bairro").value("Presidente Altino"))
                .andExpect(jsonPath("$.cidades[0].bairros[1].atendimentos").value(1))
                .andExpect(jsonPath("$.cidades[0].bairros[1].percentual").value(14.3))
                .andExpect(jsonPath("$.cidades[0].bairros[2].bairro").value("Vila Yara"))
                .andExpect(jsonPath("$.cidades[0].bairros[2].atendimentos").value(1));
    }

    @Test
    @DisplayName("A69: atendimentos de dois barbeiros: o BARBEIRO A chama com barbeiroId do B e vê só os próprios; o ADMIN vê todos ou filtra por barbeiro")
    void barbeiroVeSoOsProprios() throws Exception {
        Conta contaA = conta("BARBEIRO");
        Conta contaB = conta("BARBEIRO");
        UUID barbeiroA = barbeiroDaConta(contaA.id());
        UUID barbeiroB = barbeiroDaConta(contaB.id());
        UUID servico = catalogo().servico();
        atendimentos(5, barbeiroA, servico, "Centro", "Osasco", "SP");
        atendimentos(6, barbeiroB, servico, "Alphaville", "Barueri", "SP");

        get("/admin/alcance?barbeiroId=" + barbeiroB, contaA).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.cidades.length()").value(1))
                .andExpect(jsonPath("$.cidades[0].cidade").value("Osasco"));
        get("/admin/alcance", contaA).andExpect(jsonPath("$.total").value(5));
        get("/admin/alcance?barbeiroId=" + barbeiroA, contaB).andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.cidades[0].cidade").value("Barueri"));

        Conta admin = conta("ADMIN");
        get("/admin/alcance", admin).andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.cidades.length()").value(2));
        get("/admin/alcance?barbeiroId=" + barbeiroB, admin).andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.cidades[0].cidade").value("Barueri"));

        // Conta BARBEIRO sem vínculo com um barbeiro: 404, sem confirmar nada (R23).
        get("/admin/alcance", conta("BARBEIRO")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A70: ADMIN com barbeiroId inexistente → 404; período invertido → 400; período de 400 dias → 400; sem token → 401; cliente → 403 (e 366 dias ainda vale)")
    void errosDoPainel() throws Exception {
        Conta admin = conta("ADMIN");

        get("/admin/alcance?barbeiroId=" + UUID.randomUUID(), admin).andExpect(status().isNotFound());
        get("/admin/alcance?de=2026-11-10&ate=2026-11-01", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA")).andExpect(jsonPath("$.errors[0].field").value("de"));
        get("/admin/alcance?de=2025-10-01&ate=2026-11-05", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA")).andExpect(jsonPath("$.errors[0].field").value("ate"));
        get("/admin/alcance?de=2025-11-16&ate=2026-11-17", admin).andExpect(status().isBadRequest()); // 367 dias
        get("/admin/alcance?de=2025-11-17&ate=2026-11-17", admin).andExpect(status().isOk()); // 366 dias
        get("/admin/alcance?de=abc", admin).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/alcance"))
                .andExpect(status().isUnauthorized());
        get("/admin/alcance", emissor.emitir(cliente("Cliente Teste", "11988880001"), java.util.List.of("CLIENTE"), 0, false)
                .valor()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A18: três cidades com 1, 3 e 2 atendimentos aparecem todas, da que mais atendeu para a que menos atendeu (3, 2, 1), cada uma com o seu bairro e o número exato, inclusive a de 1 atendimento")
    void cidadesDoMaiorParaOMenor() throws Exception {
        Catalogo c = catalogo();
        atendimentos(1, c.barbeiro(), c.servico(), "Centro", "Barueri", "SP");
        atendimentos(3, c.barbeiro(), c.servico(), "Km 18", "Osasco", "SP");
        atendimentos(2, c.barbeiro(), c.servico(), "Vila Dirce", "Carapicuíba", "SP");

        get("/admin/alcance", conta("ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.cidades.length()").value(3))
                .andExpect(jsonPath("$.cidades[0].cidade").value("Osasco"))
                .andExpect(jsonPath("$.cidades[0].atendimentos").value(3))
                .andExpect(jsonPath("$.cidades[0].bairros[0].bairro").value("Km 18"))
                .andExpect(jsonPath("$.cidades[1].cidade").value("Carapicuíba"))
                .andExpect(jsonPath("$.cidades[1].atendimentos").value(2))
                .andExpect(jsonPath("$.cidades[2].cidade").value("Barueri"))
                .andExpect(jsonPath("$.cidades[2].atendimentos").value(1))
                .andExpect(jsonPath("$.cidades[2].percentual").value(16.7))
                .andExpect(jsonPath("$.cidades[2].bairros[0].atendimentos").value(1));
    }
}
