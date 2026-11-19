package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;

/**
 * A61: as rotas novas e as que mudaram da 6B estão no contrato 1.5.0 do jeito que o código as serve, e cada uma recusa
 * quem não pode (401 sem login; 403 onde há papel). O {@code redocly lint} do contrato roda à parte (comando no guia de
 * testes); aqui o teste lê o OpenAPI como texto e confere o que a 6B prometeu.
 */
class ContratoPrivacidadeTest extends PrivacidadeBase {

    private static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();

    private static String ler(String arquivo) throws IOException {
        return Files.readString(RAIZ.resolve(arquivo), StandardCharsets.UTF_8);
    }

    /** O trecho de uma rota: da linha "  /rota:" até a próxima rota ("  /outra:") ou o fim da seção paths. */
    private static String rota(String openapi, String caminho) {
        Matcher inicio = Pattern.compile("(?m)^  " + Pattern.quote(caminho) + ":\\s*$").matcher(openapi);
        assertThat(inicio.find()).as("a rota " + caminho + " está no OpenAPI").isTrue();
        Matcher fim = Pattern.compile("(?m)^(  /|components:)").matcher(openapi);
        int ate = fim.find(inicio.end()) ? fim.start() : openapi.length();
        return openapi.substring(inicio.start(), ate);
    }

    private static String operacao(String rota, String metodo) {
        Matcher inicio = Pattern.compile("(?m)^    " + metodo + ":\\s*$").matcher(rota);
        assertThat(inicio.find()).as("a operação " + metodo + " está na rota").isTrue();
        Matcher fim = Pattern.compile("(?m)^    [a-z]+:\\s*$").matcher(rota);
        int ate = fim.find(inicio.end()) ? fim.start() : rota.length();
        return rota.substring(inicio.start(), ate);
    }

    @Test
    @DisplayName("A61: o OpenAPI 1.5.0 declara as rotas da 6B com as respostas que o código devolve (401, 403, 409, 429) e os schemas novos")
    void contratoDizOQueOCodigoFaz() throws IOException {
        String openapi = ler("docs/contrato/openapi.yaml");
        assertThat(openapi).contains("version: 1.7.0");

        String politica = operacao(rota(openapi, "/politica-privacidade"), "get");
        assertThat(politica).contains("security: []").contains("PoliticaPrivacidade");

        String dados = operacao(rota(openapi, "/me/dados"), "get");
        assertThat(dados).contains("'401'").contains("'429'").contains("Cache-Control").contains("no-store");

        String excluir = operacao(rota(openapi, "/me"), "delete");
        assertThat(excluir).contains("'204'").contains("'401'").contains("'403'").contains("'409'")
                .contains("EXCLUSAO_BLOQUEADA_POR_RESERVA");

        assertThat(operacao(rota(openapi, "/me/consentimentos"), "get")).contains("'401'").contains("estado atual");
        assertThat(operacao(rota(openapi, "/me/consentimentos/marketing"), "put")).contains("'400'").contains("'401'");

        assertThat(openapi).contains("    PoliticaPrivacidade:").contains("required: [versao, textoConsentimento, politica, atualizadaEm]")
                .contains("required: [id, inicio, fim, status, servico, precoCentavos, motivoCancelamento]")
                .contains("required: [nome, email, whatsapp, endereco]");
    }

    @Test
    @DisplayName("A61: o código de erro novo está no enum (409) e na tabela de erros do contrato-api.md")
    void codigoDeErroNoContrato() throws IOException {
        assertThat(CodigoErro.EXCLUSAO_BLOQUEADA_POR_RESERVA.status()).isEqualTo(409);
        String humano = ler("docs/contrato/contrato-api.md");
        assertThat(humano).contains("<a id=\"exclusao_bloqueada_por_reserva\"></a>`EXCLUSAO_BLOQUEADA_POR_RESERVA`")
                .contains("`GET` | `/politica-privacidade`");
        assertThat(CodigoErro.EXCLUSAO_BLOQUEADA_POR_RESERVA.tipo()).endsWith("#exclusao_bloqueada_por_reserva");
    }

    @Test
    @DisplayName("A61: sem token, todas as rotas protegidas da 6B dão 401, e a política pública dá 200")
    void semTokenDa401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/me/consentimentos")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.put("/me/consentimentos/marketing").contentType("application/json")
                .content(consentimento(true, "1.1"))).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/me/dados")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.delete("/me")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/politica-privacidade")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A61: DELETE /me dá 403 para BARBEIRO e para ADMIN (conta de equipe não se exclui pelo app)")
    void equipeNaoSeExclui() throws Exception {
        delete("/me", conta("BARBEIRO").token()).andExpect(status().isForbidden());
        delete("/me", conta("ADMIN", "BARBEIRO").token()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A61c: o admin com a senha inicial ainda em uso recebe 403 TROCA_DE_SENHA_OBRIGATORIA nas rotas /me/dados e /me/consentimentos, e continua chegando a /me e /me/senha para trocá-la")
    void senhaInicialBarraAsRotasDeConta() throws Exception {
        Conta admin = conta(true, "ADMIN", "BARBEIRO");

        get("/me/dados", admin).andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("TROCA_DE_SENHA_OBRIGATORIA"));
        get("/me/consentimentos", admin).andExpect(status().isForbidden());
        put("/me/consentimentos/marketing", admin.token(), consentimento(true, "1.1")).andExpect(status().isForbidden());
        get("/me", admin).andExpect(status().isOk());
        put("/me/senha", admin.token(), "{}").andExpect(status().isBadRequest()); // chega ao controller (corpo inválido)
    }
}
