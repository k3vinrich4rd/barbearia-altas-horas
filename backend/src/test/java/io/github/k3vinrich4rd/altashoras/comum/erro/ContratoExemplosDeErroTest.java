package io.github.k3vinrich4rd.altashoras.comum.erro;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * O contrato (docs/contrato/openapi.yaml) e o enum {@link CodigoErro} não podem se desencontrar: o front decide pelo
 * {@code code} (ADR 0005), e um exemplo de erro com código ou status que o código não devolve ensina errado quem testa
 * pelo Swagger. O teste lê o YAML de verdade (não como texto) e confere todos os exemplos de problem+json.
 */
class ContratoExemplosDeErroTest {

    private static final Path OPENAPI = Path.of("..", "docs", "contrato", "openapi.yaml").toAbsolutePath().normalize();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contrato() throws IOException {
        try (Reader r = Files.newBufferedReader(OPENAPI, StandardCharsets.UTF_8)) {
            return new Yaml().load(r);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o, String chave) {
        Object v = o instanceof Map<?, ?> m ? m.get(chave) : null;
        return v instanceof Map<?, ?> m2 ? (Map<String, Object>) m2 : Map.of();
    }

    /** Exemplo de problem+json encontrado no contrato, com onde ele está e o status da resposta que o contém. */
    private record Exemplo(String onde, Integer statusDaResposta, Map<String, Object> valor) {}

    @SuppressWarnings("unchecked")
    private static void colher(String onde, Integer status, Map<String, Object> resposta, List<Exemplo> saida) {
        Map<String, Object> problema = mapa(mapa(resposta, "content"), "application/problem+json");
        for (Map.Entry<String, Object> e : mapa(problema, "examples").entrySet()) {
            Object valor = mapa(e.getValue(), "value");
            saida.add(new Exemplo(onde + " · " + e.getKey(), status, (Map<String, Object>) valor));
        }
    }

    private static Integer statusDaChave(String chave) {
        return chave.matches("\\d{3}") ? Integer.valueOf(chave) : null;
    }

    private static List<Exemplo> exemplos(Map<String, Object> c) {
        List<Exemplo> saida = new ArrayList<>();
        Map<String, Object> respostas = mapa(mapa(c, "components"), "responses");
        for (String nome : respostas.keySet()) {
            colher("components.responses." + nome, null, mapa(respostas, nome), saida);
        }
        for (Map.Entry<String, Object> rota : mapa(c, "paths").entrySet()) {
            for (Map.Entry<String, Object> op : mapa(c.get("paths"), rota.getKey()).entrySet()) {
                for (Map.Entry<String, Object> resp : mapa(op.getValue(), "responses").entrySet()) {
                    colher(rota.getKey() + " " + op.getKey() + " " + resp.getKey(), statusDaChave(resp.getKey()),
                            mapa(mapa(op.getValue(), "responses"), resp.getKey()), saida);
                }
            }
        }
        return saida;
    }

    @Test
    @DisplayName("Contrato: todo exemplo de erro tem code do enum CodigoErro, com o mesmo status e o mesmo título que a API devolve")
    void exemplosDeErroBatemComOEnum() throws IOException {
        List<Exemplo> exemplos = exemplos(contrato());
        assertThat(exemplos).as("o contrato documenta exemplos de erro").hasSizeGreaterThanOrEqualTo(2);

        for (Exemplo ex : exemplos) {
            Object code = ex.valor().get("code");
            assertThat(code).as("%s: code", ex.onde()).isInstanceOf(String.class);
            CodigoErro codigo = CodigoErro.valueOf((String) code);
            assertThat(ex.valor().get("status")).as("%s: status igual ao do enum", ex.onde()).isEqualTo(codigo.status());
            assertThat(ex.valor().get("title")).as("%s: título igual ao do enum", ex.onde()).isEqualTo(codigo.titulo());
            assertThat((String) ex.valor().get("type")).as("%s: type aponta para a âncora do código no contrato", ex.onde())
                    .endsWith("#" + codigo.name().toLowerCase(java.util.Locale.ROOT));
            if (ex.statusDaResposta() != null) {
                assertThat(codigo.status()).as("%s: exemplo dentro da resposta certa", ex.onde()).isEqualTo(ex.statusDaResposta());
            }
        }
    }

    @Test
    @DisplayName("Contrato: o enum CodigoErro do OpenAPI tem exatamente os mesmos códigos do enum Java")
    @SuppressWarnings("unchecked")
    void enumDoContratoIgualAoDoCodigo() throws IOException {
        List<String> doContrato = (List<String>) mapa(mapa(mapa(contrato(), "components"), "schemas"), "CodigoErro").get("enum");
        Set<String> doCodigo = Arrays.stream(CodigoErro.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(Set.copyOf(doContrato)).isEqualTo(doCodigo);
    }

    @Test
    @DisplayName("A33: o contrato (1.8.0 em diante) declara as rotas do convite, o erro CONVITE_INVALIDO (com exemplo real) e BarbeiroAdmin.acesso")
    @SuppressWarnings("unchecked")
    void contratoDoConvite() throws IOException {
        Map<String, Object> c = contrato();
        assertThat(String.valueOf(mapa(c, "info").get("version"))).matches("1\\.(8|9|1[0-9])\\.\\d+");
        assertThat(mapa(c, "paths")).containsKeys("/auth/convite", "/admin/barbeiros/{id}/convite");
        assertThat(mapa(mapa(c, "paths"), "/admin/barbeiros/{id}/convite")).containsKeys("post", "delete");
        assertThat((List<String>) mapa(mapa(mapa(c, "components"), "schemas"), "CodigoErro").get("enum")).contains("CONVITE_INVALIDO");
        assertThat(exemplos(c)).anySatisfy(ex -> assertThat(ex.valor().get("code")).isEqualTo("CONVITE_INVALIDO"));
        assertThat(CodigoErro.CONVITE_INVALIDO.status()).isEqualTo(422);
        assertThat(mapa(mapa(mapa(mapa(c, "components"), "schemas"), "BarbeiroAdmin").get("allOf") instanceof List<?> l ? (Map<String, Object>) l.get(1) : Map.of(), "properties"))
                .containsKeys("acesso", "conviteExpiraEm");
    }

    @Test
    @DisplayName("A50: o contrato 1.10.0 declara as 6 operações de /admin/notificacoes e os 5 schemas novos, sem código de erro novo; o resto (lint do Redocly e conferir-spec.sh 13 --exigir) roda no CI")
    @SuppressWarnings("unchecked")
    void contratoDaNotificacao() throws IOException {
        Map<String, Object> c = contrato();
        assertThat(String.valueOf(mapa(c, "info").get("version"))).matches("1\\.(10|1[1-9])\\.\\d+");
        Map<String, Object> paths = mapa(c, "paths");
        assertThat(paths).containsKeys("/admin/notificacoes", "/admin/notificacoes/chave-publica", "/admin/notificacoes/inscricao",
                "/admin/notificacoes/marcar-lidas", "/admin/notificacoes/teste");
        assertThat(mapa(paths, "/admin/notificacoes/inscricao")).containsKeys("post", "delete");
        assertThat(mapa(mapa(c, "components"), "schemas")).containsKeys("ChavePublicaPush", "InscricaoPush", "RemocaoDeInscricaoPush",
                "Novidades", "AvisoDoPainel");
    }
}
