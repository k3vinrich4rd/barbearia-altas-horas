package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ipNovo;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A14: 30 consultas por minuto por IP (H15, E7), pelo mesmo limitador do login e do cadastro. */
@CepTeste
class CepLimiteIT extends CepTesteBase {

    private static final String IP_DO_ROBO = "203.0.113.7";

    @Test
    @DisplayName("A14: na 31ª consulta do mesmo IP no minuto vem 429 com Retry-After; outro IP segue com 200 e um minuto depois o IP volta")
    void trintaPorMinutoPorIp() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE)); // uma chamada externa só: as outras 29 saem do cache

        for (int i = 1; i <= 30; i++) {
            consultarDo(CEP_SE, IP_DO_ROBO).andExpect(status().isOk());
        }
        consultarDo(CEP_SE, IP_DO_ROBO)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"))
                .andExpect(header().string("Retry-After", "60"));

        consultarDo(CEP_SE, ipNovo()).andExpect(status().isOk());

        relogio.avancar(Duration.ofMinutes(1));
        consultarDo(CEP_SE, IP_DO_ROBO).andExpect(status().isOk());
        provedores.verificar();
    }

    @Test
    @DisplayName("A14: CEP em formato errado não gasta o limite do IP (o 400 vem antes de limitar)")
    void formatoErradoNaoConsomeLimite() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 40; i++) {
            consultarDo("123", ip).andExpect(status().isBadRequest());
        }
        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        consultarDo(CEP_SE, ip).andExpect(status().isOk());
    }
}
