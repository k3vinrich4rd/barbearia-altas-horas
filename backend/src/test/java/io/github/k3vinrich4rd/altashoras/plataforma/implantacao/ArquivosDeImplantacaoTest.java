package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Fatia 8: confere, sem rede e sem Docker, os arquivos que descrevem o deploy (render.yaml, o workflow de backup e o
 * guia do Kevin). Se alguém renomear uma variável ou colar um segredo, estes testes quebram antes de a PR abrir.
 */
class ArquivosDeImplantacaoTest {

    /** Segredos e valores que dependem do ambiente: NUNCA com valor no render.yaml (sync: false). */
    private static final List<String> VARIAVEIS_SEM_VALOR = List.of("DB_URL", "DB_USUARIO", "DB_SENHA", "ADMIN_EMAIL",
            "ADMIN_SENHA", "ADMIN_NOME", "CORS_ORIGENS", "URL_CHECKOUT_SIMULADO", "APP_PRIVACIDADE_CONTATO");

    /** Segredos que o Render gera sozinho (generateValue): ninguém digita nem o arquivo guarda. */
    private static final List<String> VARIAVEIS_GERADAS = List.of("JWT_SEGREDO", "IDEMPOTENCIA_HMAC_SEGREDO",
            "GATEWAY_SEGREDO_WEBHOOK");

    private static Path raiz() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("render.yaml"))) {
            p = p.getParent();
        }
        assertThat(p).as("render.yaml na raiz do repositório").isNotNull();
        return p;
    }

    private static String ler(String relativo) throws IOException {
        return Files.readString(raiz().resolve(relativo));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String relativo) throws IOException {
        return (Map<String, Object>) new Yaml().load(ler(relativo));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicoWeb() throws IOException {
        List<Map<String, Object>> servicos = (List<Map<String, Object>>) yaml("render.yaml").get("services");
        assertThat(servicos).hasSize(1);
        return servicos.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> variaveis() throws IOException {
        Map<String, Map<String, Object>> porNome = new LinkedHashMap<>();
        for (Map<String, Object> v : (List<Map<String, Object>>) servicoWeb().get("envVars")) {
            porNome.put((String) v.get("key"), v);
        }
        return porNome;
    }

    @Test
    @DisplayName("A17: render.yaml tem só a API (Docker, plano free, health check no liveness) e nenhum banco do Render")
    void blueprintDaApi() throws IOException {
        Map<String, Object> raizDoYaml = yaml("render.yaml");
        Map<String, Object> web = servicoWeb();

        assertThat(raizDoYaml).doesNotContainKeys("databases", "keyValueServices");
        assertThat(web).containsEntry("type", "web").containsEntry("runtime", "docker").containsEntry("plan", "free")
                .containsEntry("healthCheckPath", "/actuator/health/liveness").containsEntry("dockerContext", "./backend")
                .containsEntry("dockerfilePath", "./backend/Dockerfile").containsEntry("branch", "main");
        assertThat(Files.exists(raiz().resolve("backend/Dockerfile"))).isTrue();
    }

    @Test
    @DisplayName("A18: toda variável do render.yaml é sync: false (Kevin preenche) ou generateValue (Render gera), sem valor escrito")
    void blueprintSemSegredo() throws IOException {
        Map<String, Map<String, Object>> vars = variaveis();

        assertThat(vars.keySet()).containsAll(VARIAVEIS_SEM_VALOR);
        for (String nome : VARIAVEIS_SEM_VALOR) {
            assertThat(vars.get(nome)).as(nome).containsEntry("sync", false).doesNotContainKey("value");
        }
        assertThat(vars.keySet()).containsAll(VARIAVEIS_GERADAS);
        for (String nome : VARIAVEIS_GERADAS) {
            assertThat(vars.get(nome)).as(nome).containsEntry("generateValue", true).doesNotContainKey("value")
                    .doesNotContainKey("sync");
        }
        // O que tem valor fixo é só configuração pública do beta.
        assertThat(vars.get("SPRING_PROFILES_ACTIVE")).containsEntry("value", "prod");
        assertThat(vars.get("APP_PAGAMENTO_MODO")).containsEntry("value", "simulado");
        assertThat(ler("render.yaml")).doesNotContainPattern("(?i)(postgres(ql)?|jdbc|mysql)://[^\\s]*@");
    }

    @Test
    @DisplayName("A19: toda variável sem valor padrão do application.yml e do application-prod.yml está no render.yaml")
    void blueprintCobreAsVariaveisObrigatorias() throws IOException {
        Pattern semPadrao = Pattern.compile("\\$\\{([A-Z0-9_]+)(:?)\\}");
        List<String> obrigatorias = new ArrayList<>();
        for (String arquivo : List.of("backend/src/main/resources/application.yml", "backend/src/main/resources/application-prod.yml")) {
            Matcher m = semPadrao.matcher(ler(arquivo));
            while (m.find()) {
                if (!List.of("ALTAS_HORAS_ENV", "GATEWAY_TOKEN").contains(m.group(1))) { // GATEWAY_TOKEN: só no modo asaas, fora do beta
                    obrigatorias.add(m.group(1));
                }
            }
        }
        assertThat(obrigatorias).as("variáveis lidas sem valor padrão").contains("DB_URL", "DB_USUARIO", "DB_SENHA",
                "JWT_SEGREDO", "IDEMPOTENCIA_HMAC_SEGREDO", "CORS_ORIGENS", "APP_PRIVACIDADE_CONTATO");
        assertThat(variaveis().keySet()).containsAll(obrigatorias);
    }

    @Test
    @DisplayName("A21: o workflow de backup roda só por agenda ou à mão, nunca em PR, com os dois segredos e retenção de 30 dias")
    @SuppressWarnings("unchecked")
    void workflowDeBackup() throws IOException {
        String texto = ler(".github/workflows/backup-banco.yml");
        Map<String, Object> wf = yaml(".github/workflows/backup-banco.yml");
        // No YAML 1.1 do SnakeYAML a chave "on" vira o booleano true.
        Map<String, Object> gatilhos = (Map<String, Object>) (wf.containsKey("on") ? wf.get("on") : wf.get(Boolean.TRUE));

        assertThat(gatilhos.keySet()).containsExactlyInAnyOrder("schedule", "workflow_dispatch");
        assertThat(texto).doesNotContain("pull_request").doesNotContain("push:");
        assertThat(((Map<String, Object>) wf.get("permissions"))).containsEntry("contents", "read");
        assertThat(texto).contains("secrets.DB_URL_BACKUP", "secrets.BACKUP_SENHA", "retention-days: 30", "pg_dump",
                "-Fc", "gpg", "--symmetric", "--cipher-algo AES256", "actions/upload-artifact@");
        // O dump em claro nunca vira artefato: só o arquivo .gpg.
        assertThat(texto).contains("path: ${{ env.ARQUIVO }}").contains(".dump.gpg").doesNotContain("path: altashoras.dump");
    }

    @Test
    @DisplayName("A22: o guia de ativação cita cada variável do render.yaml e os dois segredos do GitHub")
    void guiaDeAtivacao() throws IOException {
        String guia = ler("docs/planejamento/ativar-deploy.md");

        for (String nome : variaveis().keySet()) {
            assertThat(guia).as("variável " + nome + " no guia").contains(nome);
        }
        assertThat(guia).contains("DB_URL_BACKUP", "BACKUP_SENHA", "btree_gist", "/actuator/health");
        // Nenhum valor secreto de verdade: o guia só nomeia as variáveis.
        assertThat(guia).doesNotContainPattern("(?i)(postgres(ql)?|jdbc:postgresql)://[^\\s<]*:[^\\s<@]+@");
    }
}
