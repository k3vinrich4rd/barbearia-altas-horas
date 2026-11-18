package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_INEXISTENTE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_ERRO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.erro503;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * A62 a A66: a região que a reserva copia, do CEP do visitante ou do cadastro do cliente (E30). Os provedores de CEP são
 * falsos (A22): CEP nunca impede a reserva (R31).
 */
@ExtendWith(OutputCaptureExtension.class)
class AlcanceDaReservaIT extends AlcanceBase {

    private Map<String, Object> regiaoDaReserva(String reservaId) {
        return jdbc.queryForMap("SELECT regiao_bairro, regiao_cidade, regiao_uf FROM reserva WHERE id = ?::uuid", reservaId);
    }

    private String cepDoVisitante(String reservaId) {
        return jdbc.queryForObject("SELECT cep FROM reserva_guest WHERE reserva_id = ?::uuid", String.class, reservaId);
    }

    private static String idDe(String resposta) {
        return JsonPath.read(resposta, "$.id");
    }

    @Test
    @DisplayName("A62: ViaCEP devolve Sé, São Paulo, SP e o visitante agenda com cep 01001-000: 201, a reserva guarda Sé, São Paulo, SP e reserva_guest.cep = 01001000, sem o CEP no log")
    void visitanteComCepResolvido(CapturedOutput saida) throws Exception {
        Catalogo c = catalogo();
        provedores.viaCep(CEP_SE, json(VIACEP_SE));

        String resposta = reservarComoVisitante(c, "10:00", "01001-000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.cep").doesNotExist()).andExpect(jsonPath("$.regiao").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(regiaoDaReserva(idDe(resposta))).containsEntry("regiao_bairro", "Sé")
                .containsEntry("regiao_cidade", "São Paulo").containsEntry("regiao_uf", "SP");
        assertThat(cepDoVisitante(idDe(resposta))).isEqualTo("01001000");
        assertThat(saida.getAll()).contains("região resolvida: sim").doesNotContain("01001000").doesNotContain("01001-000");
    }

    @Test
    @DisplayName("A63: com os dois provedores fora (503) ou com o CEP 99999999, o visitante agenda e recebe 201: a reserva fica sem região e reserva_guest.cep fica nulo")
    void cepNuncaImpedeAReserva(CapturedOutput saida) throws Exception {
        Catalogo c = catalogo();
        provedores.viaCep(CEP_SE, erro503());
        provedores.brasilApi(CEP_SE, erro503());
        String fora = reservarComoVisitante(c, "10:00", CEP_SE).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        provedores.verificar();
        provedores.reiniciar();

        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));
        String inexistente = reservarComoVisitante(c, "10:30", CEP_INEXISTENTE).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        for (String resposta : new String[] {fora, inexistente}) {
            assertThat(regiaoDaReserva(idDe(resposta))).containsEntry("regiao_bairro", null)
                    .containsEntry("regiao_cidade", null).containsEntry("regiao_uf", null);
            assertThat(cepDoVisitante(idDe(resposta))).isNull();
        }
        assertThat(saida.getAll()).contains("região resolvida: não").doesNotContain(CEP_SE).doesNotContain(CEP_INEXISTENTE);
    }

    @Test
    @DisplayName("A64: cep 123 (e outros formatos errados): 400 CEP_INVALIDO, nenhuma reserva criada e zero chamadas externas")
    void cepComFormatoErradoEBadRequest() throws Exception {
        Catalogo c = catalogo();
        for (String errado : new String[] {"123", "0100100", "010010000", "abcdefgh", "01001/000"}) {
            reservarComoVisitante(c, "10:00", errado).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CEP_INVALIDO"));
        }

        assertThat(contar("SELECT count(*) FROM reserva WHERE barbeiro_id = ?", c.barbeiro())).isZero();
        provedores.verificar(); // nenhuma expectativa: qualquer chamada externa teria falhado
    }

    @Test
    @DisplayName("A65: visitante sem cep: 201 como antes, sem chamada externa e sem região (os testes da Fatia 4 seguem verdes)")
    void semCepComoAntes() throws Exception {
        Catalogo c = catalogo();

        String resposta = reservarComoVisitante(c, "10:00", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO")).andReturn().getResponse()
                .getContentAsString();

        assertThat(regiaoDaReserva(idDe(resposta))).containsEntry("regiao_cidade", null);
        assertThat(cepDoVisitante(idDe(resposta))).isNull();
        provedores.verificar();
    }

    @Test
    @DisplayName("A66: cliente com Centro, Osasco, SP no cadastro: a reserva copia Centro, Osasco, SP, e mudar o endereço do usuário depois não muda a reserva")
    void clienteCopiaARegiaoDoCadastro() throws Exception {
        Catalogo c = catalogo();
        UUID cliente = cliente("Cliente Teste", "11988880001");
        jdbc.update("UPDATE usuario SET cep = '06010000', bairro = 'Centro', cidade = 'Osasco', uf = 'SP' WHERE id = ?", cliente);

        String resposta = reservarComoCliente(c, "10:00", cliente).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        assertThat(regiaoDaReserva(idDe(resposta))).containsEntry("regiao_bairro", "Centro")
                .containsEntry("regiao_cidade", "Osasco").containsEntry("regiao_uf", "SP");

        jdbc.update("UPDATE usuario SET bairro = 'Sé', cidade = 'São Paulo', uf = 'SP' WHERE id = ?", cliente);
        assertThat(regiaoDaReserva(idDe(resposta))).containsEntry("regiao_bairro", "Centro")
                .containsEntry("regiao_cidade", "Osasco");

        // Sem endereço resolvido no cadastro (R31), a reserva nasce sem região e nada é consultado.
        UUID semEndereco = cliente("Cliente Sem Endereco", "11988880002");
        String outra = reservarComoCliente(c, "10:30", semEndereco).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        assertThat(regiaoDaReserva(idDe(outra))).containsEntry("regiao_cidade", null);
        provedores.verificar();
    }
}
