package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Fatia 8B: confere, sem rede e sem Docker, os arquivos de deploy novos (render.yaml com checksPass, CIs na main,
 * liveness no Render e no Docker, keep-alive do banco, rollback, checklist, backup inalterado e custo zero). Fica numa
 * classe à parte do {@code ArquivosDeImplantacaoTest} da Fatia 8 porque os números dos critérios (A1, A17, A22...)
 * se repetem entre as duas specs e o {@code conferir-spec.sh} procura o número dentro da classe citada.
 */
class ArquivosDeImplantacao8bTest {

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
        return ((List<Map<String, Object>>) yaml("render.yaml").get("services")).get(0);
    }

    /** O texto do arquivo sem as linhas de comentário (# ...), para não confundir o que é explicação com o que vale. */
    private static String semComentarios(String texto) {
        return texto.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> gatilhos(String arquivo) throws IOException {
        Map<String, Object> wf = yaml(arquivo);
        // No YAML 1.1 do SnakeYAML a chave "on" vira o booleano true.
        return (Map<String, Object>) (wf.containsKey("on") ? wf.get("on") : wf.get(Boolean.TRUE));
    }

    @Test
    @DisplayName("A1: render.yaml publica só com os checks verdes (autoDeployTrigger: checksPass) na main, sem autoDeploy")
    void deploySoComChecksVerdes() throws IOException {
        Map<String, Object> web = servicoWeb();
        assertThat(web).containsEntry("autoDeployTrigger", "checksPass").containsEntry("branch", "main")
                .doesNotContainKey("autoDeploy");
        assertThat(semComentarios(ler("render.yaml"))).doesNotContain("autoDeploy: true")
                .doesNotContain("autoDeployTrigger: commit").doesNotContain("autoDeployTrigger: off");
    }

    @Test
    @DisplayName("A2: Backend CI e Frontend CI rodam em push na main e não têm filtro paths que pule o commit de release")
    @SuppressWarnings("unchecked")
    void cisRodamNoPushDaMain() throws IOException {
        for (String arquivo : List.of(".github/workflows/backend-ci.yml", ".github/workflows/frontend-ci.yml")) {
            Map<String, Object> g = gatilhos(arquivo);
            Map<String, Object> push = (Map<String, Object>) g.get("push");
            assertThat((List<String>) push.get("branches")).as(arquivo).contains("main");
            assertThat(push).as(arquivo + " push").doesNotContainKeys("paths", "paths-ignore");
            assertThat(g).as(arquivo).containsKey("pull_request");
            Map<String, Object> pr = g.get("pull_request") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            assertThat(pr).as(arquivo + " pull_request").doesNotContainKeys("paths", "paths-ignore");
        }
    }

    @Test
    @DisplayName("A16: o health check do Render e o HEALTHCHECK do Docker usam o liveness, nunca o health completo")
    void healthDoRenderEDoDockerEhLiveness() throws IOException {
        assertThat(servicoWeb()).containsEntry("healthCheckPath", "/actuator/health/liveness");
        String dockerfile = semComentarios(ler("backend/Dockerfile"));
        assertThat(dockerfile).contains("HEALTHCHECK").contains("/actuator/health/liveness");
        assertThat(dockerfile).doesNotContainPattern("/actuator/health(?!/liveness)[\"\\s]");
    }

    @Test
    @DisplayName("A22: manter-banco.yml roda só por agenda ou à mão, só no repositório original, com o segredo e select 1")
    @SuppressWarnings("unchecked")
    void workflowManterBanco() throws IOException {
        String arquivo = ".github/workflows/manter-banco.yml";
        String texto = ler(arquivo);
        String codigo = semComentarios(texto);
        Map<String, Object> wf = yaml(arquivo);

        assertThat(gatilhos(arquivo).keySet()).containsExactlyInAnyOrder("schedule", "workflow_dispatch");
        assertThat(codigo).doesNotContain("pull_request").doesNotContain("push:");
        assertThat((Map<String, Object>) wf.get("permissions")).containsEntry("contents", "read");
        assertThat(codigo).contains("if: github.repository == 'k3vinrich4rd/barbearia-altas-horas'")
                .contains("secrets.DB_URL_BACKUP").contains("postgres:18-alpine").contains("select 1")
                .contains("timeout-minutes: 5");
        // A URL nunca é impressa: nenhum echo/cat/printf do segredo, e a saída de erro do psql vai para /dev/null.
        assertThat(codigo).doesNotContainPattern("(echo|printf|cat)[^\\n]*\\$\\{?DB_URL_BACKUP").contains("2>/dev/null");
    }

    @Test
    @DisplayName("A24: ativar-deploy.md traz os 3 degraus de rollback, a regra do schema e o verificar-no-ar.sh")
    void guiaDeRollback() throws IOException {
        String guia = ler("docs/planejamento/ativar-deploy.md");
        assertThat(guia).contains("Rollback no Render", "Manual Deploy", "git revert", "PR")
                .contains("compatível com a versão anterior").contains("expandir → migrar → contrair")
                .contains("scripts/verificar-no-ar.sh").contains("DB_URL_BACKUP").contains("BACKUP_SENHA")
                .contains("ping-perfil.diff.md");
        assertThat(ler("scripts/verificar-no-ar.sh")).contains("pagamento-simulado").contains("Access-Control-Allow-Origin");
    }

    @Test
    @DisplayName("A25: o checklist da PR tem o item da migração compatível com o código anterior")
    void checklistDaPr() throws IOException {
        assertThat(ler("docs/sdd/checklist-pr.md")).contains("Migração nova é compatível com o código anterior")
                .contains("expandir → migrar → contrair");
    }

    @Test
    @DisplayName("A32: backup-banco.yml continua como na Fatia 8 (agenda, segredos, gpg AES256, 30 dias) e recusa senha curta")
    @SuppressWarnings("unchecked")
    void backupInalterado() throws IOException {
        String arquivo = ".github/workflows/backup-banco.yml";
        String texto = ler(arquivo);
        assertThat(gatilhos(arquivo).keySet()).containsExactlyInAnyOrder("schedule", "workflow_dispatch");
        assertThat(texto).contains("cron: \"0 6 * * 0\"").contains("secrets.DB_URL_BACKUP").contains("secrets.BACKUP_SENHA")
                .contains("--cipher-algo AES256").contains("retention-days: 30").contains("path: ${{ env.ARQUIVO }}")
                .contains("[ \"${#BACKUP_SENHA}\" -ge 24 ]").contains("BACKUP_SENHA curta demais");
        assertThat(texto).doesNotContain("pull_request");
    }

    @Test
    @DisplayName("A33: custo zero: todo plan é free, nada de starter, e o guia traz a tabela de limites com a data da conferência")
    void custoZero() throws IOException {
        String renderYaml = ler("render.yaml");
        assertThat(servicoWeb()).containsEntry("plan", "free");
        assertThat(renderYaml.lines().filter(l -> l.stripLeading().startsWith("plan:")).toList()).isNotEmpty()
                .allMatch(l -> l.contains("plan: free"));
        for (String arquivo : List.of("render.yaml", ".github/workflows/manter-banco.yml", ".github/workflows/backup-banco.yml")) {
            assertThat(semComentarios(ler(arquivo)).toLowerCase()).as(arquivo).doesNotContain("starter")
                    .doesNotContain("standard plus");
        }
        String guia = ler("docs/planejamento/ativar-deploy.md");
        assertThat(guia).contains("Limites e custo zero (conferido em 23/11/2026)").contains("750 h/mês")
                .contains("Render Free").contains("Aiven PostgreSQL Free").contains("Vercel Hobby").contains("GitHub Actions")
                .contains("O que acontece se estourar").doesNotContain("starter");
    }
}
