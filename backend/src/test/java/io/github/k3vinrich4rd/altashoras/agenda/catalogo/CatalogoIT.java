package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

/** A1, A3 e A5: o catálogo público (serviços e barbeiros ativos). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class CatalogoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private List<Map<String, Object>> lista(String rota) throws Exception {
        String corpo = mvc.perform(get(rota)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$");
    }

    @Test
    @DisplayName("A1: GET /servicos traz só os ativos, por nome sem diferença de maiúsculas, e só os campos do contrato")
    void servicosAtivos() throws Exception {
        UUID b = DadosDeTeste.servico(jdbc, "bbb corte", 30, 5500, true);
        UUID a = DadosDeTeste.servico(jdbc, "AAA barba", 60, 123456, true);
        UUID inativo = DadosDeTeste.servico(jdbc, "aaa inativo", 30, 1000, false);

        List<Map<String, Object>> servicos = lista("/servicos");
        List<Object> ids = servicos.stream().map(s -> s.get("id")).toList();

        assertThat(ids).contains(a.toString(), b.toString()).doesNotContain(inativo.toString());
        assertThat(ids.indexOf(a.toString())).isLessThan(ids.indexOf(b.toString()));
        Map<String, Object> servicoA = servicos.get(ids.indexOf(a.toString()));
        assertThat(servicoA).containsOnlyKeys("id", "nome", "duracaoMin", "precoCentavos", "precoFormatado")
                .containsEntry("duracaoMin", 60)
                .containsEntry("precoCentavos", 123456)
                .containsEntry("precoFormatado", "R$ 1.234,56");
    }

    @Test
    @DisplayName("A3: GET /barbeiros traz só os ativos, com id e nomeExibicao")
    void barbeirosAtivos() throws Exception {
        UUID ativo = DadosDeTeste.barbeiro(jdbc, true);
        UUID inativo = DadosDeTeste.barbeiro(jdbc, false);

        List<Map<String, Object>> barbeiros = lista("/barbeiros");
        List<Object> ids = barbeiros.stream().map(x -> x.get("id")).toList();

        assertThat(ids).contains(ativo.toString()).doesNotContain(inativo.toString());
        assertThat(barbeiros.get(ids.indexOf(ativo.toString()))).containsOnlyKeys("id", "nomeExibicao", "whatsapp")
                .containsEntry("nomeExibicao", "Barbeiro " + ativo).containsEntry("whatsapp", null);
    }

    @Test
    @DisplayName("A5: catálogo responde com Cache-Control max-age=60, public")
    void cacheDoCatalogo() throws Exception {
        for (String rota : List.of("/servicos", "/barbeiros", "/politica-pagamento")) {
            mvc.perform(get(rota)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "max-age=60, public"));
        }
    }
}
