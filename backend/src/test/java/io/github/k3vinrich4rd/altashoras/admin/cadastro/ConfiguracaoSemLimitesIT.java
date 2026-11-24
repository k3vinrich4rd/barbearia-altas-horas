package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/**
 * A18 da Fatia 14A (D197): a configuração da agenda não tem mais antecedência mínima nem horizonte. A resposta não traz os
 * dois campos; o PUT de um front antigo que ainda os envia recebe 200 e eles são ignorados (a agenda continua sem limites).
 */
class ConfiguracaoSemLimitesIT extends CadastrosBase {

    private static final String ROTA = "/admin/configuracao";

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
    }

    @Test
    @DisplayName("A18: GET /admin/configuracao não traz antecedenciaMinimaMin nem horizonteDias; o PUT de um front antigo (com os dois) dá 200, ignora os dois e o A7 continua valendo")
    void configuracaoSemOsDoisCampos() throws Exception {
        Conta admin = admin();
        enviar(HttpMethod.GET, ROTA, admin, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedenciaMinimaMin").doesNotExist())
                .andExpect(jsonPath("$.horizonteDias").doesNotExist())
                .andExpect(jsonPath("$.granularidadeMin").value(30))
                .andExpect(jsonPath("$.expiracaoPagamentoMin").value(3))
                .andExpect(jsonPath("$.fuso").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.version").value(0));

        put(ROTA, admin, """
                { "antecedenciaMinimaMin": 120, "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30,
                  "horizonteDias": 30, "expiracaoPagamentoMin": 5, "version": 0 }
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedenciaMinimaMin").doesNotExist())
                .andExpect(jsonPath("$.horizonteDias").doesNotExist())
                .andExpect(jsonPath("$.expiracaoPagamentoMin").value(5))
                .andExpect(jsonPath("$.version").value(1));

        // Sem os dois campos o PUT também vale (é o que o front novo manda).
        put(ROTA, admin, """
                { "intervaloEntreAtendimentosMin": 0, "granularidadeMin": 30, "expiracaoPagamentoMin": 3, "version": 1 }
                """).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        // O A7 continua valendo depois do PUT antigo: reservar daqui a 5 minutos e daqui a 400 dias.
        irPara(LocalDate.of(2026, 12, 1), 9, 55);
        Conta cliente = conta("CLIENTE");
        reservar(cliente, servico, barbeiro, DadosDeTeste.em(LocalDate.of(2026, 12, 1), 10, 0)).andExpect(status().isCreated());
        reservar(cliente, servico, barbeiro, DadosDeTeste.em(LocalDate.of(2028, 1, 5), 10, 0)).andExpect(status().isCreated());
        assertThat(horas(servico, barbeiro, LocalDate.of(2028, 1, 6))).contains("07:00", "19:30");
    }
}
