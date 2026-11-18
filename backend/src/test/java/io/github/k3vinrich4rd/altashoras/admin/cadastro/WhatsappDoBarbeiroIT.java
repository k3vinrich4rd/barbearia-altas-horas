package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/**
 * A7 a A11 da Fatia 6D: o WhatsApp do barbeiro como contato comercial (D122). Cadastro só pelo ADMIN, formato, rota pública
 * com o número, e o texto da política (versão 1.1) que explica isso. Números fictícios.
 */
class WhatsappDoBarbeiroIT extends CadastrosBase {

    private static String criar(String nome, String whatsapp) {
        String campo = whatsapp == null ? "" : ", \"whatsapp\": \"" + whatsapp + "\"";
        return """
                { "nomeExibicao": "%s"%s }
                """.formatted(nome, campo);
    }

    private static String alterar(String nome, String whatsapp, boolean ativo, long version) {
        String campo = whatsapp == null ? "" : "\"whatsapp\": \"" + whatsapp + "\", ";
        return """
                { "nomeExibicao": "%s", %s"ativo": %s, "version": %d }
                """.formatted(nome, campo, ativo, version);
    }

    private String whatsappNoBanco(String id) {
        return jdbc.queryForObject("SELECT whatsapp FROM barbeiro WHERE id = ?::uuid", String.class, id);
    }

    @Test
    @DisplayName("A7: o ADMIN cadastra o WhatsApp do barbeiro com máscara no POST e no PUT: o banco guarda só os dígitos, a resposta e o GET /admin/barbeiros mostram o número, e o PUT sem o campo remove")
    void adminCadastraEAltera() throws Exception {
        Conta admin = admin();

        String id = idDe(postJson("/admin/barbeiros", admin, criar("Bruno", "(11) 91234-5678"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.whatsapp").value("11912345678")));
        assertThat(whatsappNoBanco(id)).isEqualTo("11912345678");
        get("/admin/barbeiros", admin).andExpect(jsonPath("$[0].whatsapp").value("11912345678"));

        put("/admin/barbeiros/" + id, admin, alterar("Bruno", "+55 11 98888-7777", true, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("5511988887777")).andExpect(jsonPath("$.version").value(1));
        assertThat(whatsappNoBanco(id)).isEqualTo("5511988887777");

        put("/admin/barbeiros/" + id, admin, alterar("Bruno", null, true, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
        assertThat(whatsappNoBanco(id)).isNull();

        List<String> detalhes = jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin ORDER BY ocorrido_em, id",
                String.class);
        assertThat(detalhes).anySatisfy(d -> assertThat(d).contains("whatsapp"));
        assertThat(String.join(" ", detalhes)).doesNotContain("91234").doesNotContain("88887777").doesNotContain("Bruno");
    }

    @Test
    @DisplayName("A8: GET /barbeiros, sem token, devolve o WhatsApp só dos barbeiros ativos (nulo quando não cadastrou) e continua com Cache-Control público de 60 s; o inativo não aparece")
    void rotaPublicaDevolveONumero() throws Exception {
        UUID comNumero = DadosDeTeste.barbeiro(jdbc, true);
        UUID semNumero = DadosDeTeste.barbeiro(jdbc, true);
        UUID inativo = DadosDeTeste.barbeiro(jdbc, false);
        jdbc.update("UPDATE barbeiro SET whatsapp = '11912345678' WHERE id = ?", comNumero);
        jdbc.update("UPDATE barbeiro SET whatsapp = '11955554444' WHERE id = ?", inativo);

        String corpo = mvc.perform(MockMvcRequestBuilders.get("/barbeiros")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(corpo, "$[?(@.id == '" + comNumero + "')].whatsapp"))
                .containsExactly("11912345678");
        assertThat((List<?>) JsonPath.read(corpo, "$[?(@.id == '" + semNumero + "')].whatsapp")).hasSize(1).allMatch(java.util.Objects::isNull);
        assertThat(corpo).doesNotContain("11955554444").doesNotContain(inativo.toString());
    }

    @Test
    @DisplayName("A9: WhatsApp com menos de 10 ou mais de 13 dígitos, ou sem dígitos, é 400 VALIDACAO_INVALIDA no campo whatsapp, no POST e no PUT, e nada é gravado")
    void formatoInvalido() throws Exception {
        Conta admin = admin();
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        long antes = jdbc.queryForObject("SELECT count(*) FROM barbeiro", Long.class);

        for (String invalido : new String[] {"123456789", "55119123456789", "telefone"}) {
            postJson("/admin/barbeiros", admin, criar("Carlos", invalido)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value("whatsapp"));
            put("/admin/barbeiros/" + barbeiro, admin, alterar("Carlos", invalido, true, 0)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("whatsapp"));
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM barbeiro", Long.class)).isEqualTo(antes);
        assertThat(whatsappNoBanco(barbeiro.toString())).isNull();
    }

    @Test
    @DisplayName("A10: só o ADMIN cadastra o WhatsApp: o BARBEIRO e o CLIENTE recebem 403 no POST e no PUT, sem token é 401, e o número não muda")
    void soOAdminCadastra() throws Exception {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        Conta doBarbeiro = conta("BARBEIRO");
        Conta cliente = conta("CLIENTE");

        for (Conta quem : new Conta[] {doBarbeiro, cliente}) {
            postJson("/admin/barbeiros", quem, criar("Dudu", "11912345678")).andExpect(status().isForbidden());
            put("/admin/barbeiros/" + barbeiro, quem, alterar("Dudu", "11912345678", true, 0)).andExpect(status().isForbidden());
        }
        postJson("/admin/barbeiros", null, criar("Dudu", "11912345678")).andExpect(status().isUnauthorized());
        put("/admin/barbeiros/" + barbeiro, null, alterar("Dudu", "11912345678", true, 0)).andExpect(status().isUnauthorized());

        assertThat(whatsappNoBanco(barbeiro.toString())).isNull();
    }

    @Test
    @DisplayName("A11: GET /politica-privacidade devolve a versão 1.1 e o texto diz que o WhatsApp do barbeiro é contato comercial (não é dado de cliente) e que a reserva manual guarda só nome e observação, sem WhatsApp do cliente")
    void politicaExplicaOContatoComercial() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/politica-privacidade")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value("1.1"))
                .andExpect(jsonPath("$.politica").value(containsString("WhatsApp comercial do barbeiro")))
                .andExpect(jsonPath("$.politica").value(containsString("não é dado de cliente")))
                .andExpect(jsonPath("$.politica").value(containsString("agendamento marcado por fora do sistema")))
                .andExpect(jsonPath("$.politica").value(containsString("não guardamos o WhatsApp")))
                .andExpect(jsonPath("$.politica").value(containsString("Backup semanal do banco (criptografado)")))
                .andExpect(jsonPath("$.politica").value(containsString("inclusive os de contas já excluídas")));
    }
}
