package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A27 da Fatia 6D: a reserva manual não tem região e fica fora do alcance (D127), nem como "sem região". */
class ReservaManualAlcanceIT extends AlcanceBase {

    @Test
    @DisplayName("A27: 5 atendimentos em Centro/Osasco e 3 reservas manuais CONFIRMADO e CONCLUIDO no período: o painel segue com total 5 e semRegiao 0")
    void manualNaoContaNoAlcance() throws Exception {
        Catalogo c = catalogo();
        atendimentos(5, c.barbeiro(), c.servico(), "Centro", "Osasco", "SP");
        for (int i = 0; i < 3; i++) {
            OffsetDateTime inicio = DadosDeTeste.em(DIA_DO_ATENDIMENTO, 15, 0).plusMinutes(30L * i);
            jdbc.update("""
                    INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos,
                                         nome_cliente)
                    VALUES (?, ?, 'MANUAL', ?, ?, ?, ?, 4500, 'Fulano')
                    """, c.barbeiro(), c.servico(), inicio, inicio.plusMinutes(30), inicio.plusMinutes(30),
                    i == 0 ? "CONFIRMADO" : "CONCLUIDO");
        }

        get("/admin/alcance", conta("ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.semRegiao").value(0))
                .andExpect(jsonPath("$.cidades.length()").value(1))
                .andExpect(jsonPath("$.cidades[0].atendimentos").value(5));
    }
}
