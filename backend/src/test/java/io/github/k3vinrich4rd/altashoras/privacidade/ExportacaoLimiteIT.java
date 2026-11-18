package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A38: o limite de exportação, 5 por hora por conta (H22, E14), com Retry-After e sem afetar outra conta. */
class ExportacaoLimiteIT extends PrivacidadeBase {

    @Test
    @DisplayName("A38: com 5 exportações na última hora, a 6ª dá 429 com Retry-After; outra conta segue com 200; uma hora depois da primeira, a 6ª dá 200")
    void cincoPorHora() throws Exception {
        Cliente cliente = novoCliente(false);
        Cliente outra = novoCliente(false);
        for (int i = 0; i < 5; i++) {
            get("/me/dados", cliente.acesso()).andExpect(status().isOk());
        }

        get("/me/dados", cliente.acesso()).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"));
        assertThat(contar("SELECT count(*) FROM solicitacao_titular WHERE usuario_id = ? AND tipo = 'EXPORTACAO'",
                cliente.id())).as("o pedido recusado não vira solicitação").isEqualTo(5);
        get("/me/dados", outra.acesso()).andExpect(status().isOk());

        // Uma hora depois da primeira: ela sai da janela (a janela é aberta no começo). O token antigo venceu, então novo login.
        relogio.avancar(Duration.ofHours(1));
        Cliente depois = entrar(cliente.email());
        get("/me/dados", depois.acesso()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A38: 10 minutos depois da 5ª exportação o limite ainda vale e o Retry-After diz quanto falta (50 minutos)")
    void retryAfterDizQuantoFalta() throws Exception {
        Cliente cliente = novoCliente(false);
        for (int i = 0; i < 5; i++) {
            get("/me/dados", cliente.acesso()).andExpect(status().isOk());
        }
        relogio.avancar(Duration.ofMinutes(10));
        Cliente depois = entrar(cliente.email());

        get("/me/dados", depois.acesso()).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3000"));
    }
}
