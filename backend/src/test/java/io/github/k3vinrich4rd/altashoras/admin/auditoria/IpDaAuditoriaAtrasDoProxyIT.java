package io.github.k3vinrich4rd.altashoras.admin.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A34: com a mesma configuração do perfil prod (forward-headers-strategy), o IP gravado é o do cliente, não o do proxy. */
@SpringBootTest(properties = {"app.teste.contexto=painel-proxy", "server.forward-headers-strategy=framework"})
class IpDaAuditoriaAtrasDoProxyIT extends PainelBase {

    @Test
    @DisplayName("A34: atrás do proxy, a auditoria grava o IP do X-Forwarded-For e não o do proxy")
    void ipDoCliente() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        UUID reserva = reserva(barbeiro, servico, as(7, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);

        mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reserva + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeStatus("CONCLUIDO", 0))
                .header("X-Forwarded-For", "198.51.100.9").with(req -> {
                    req.setRemoteAddr("10.0.0.1");
                    return req;
                })).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT ip FROM auditoria_admin", String.class)).isEqualTo("198.51.100.9");
    }
}
