package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.yaml.snakeyaml.Yaml;

import io.github.k3vinrich4rd.altashoras.AltasHorasApplication;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;

/**
 * A21 da Fatia 14A: as fronteiras dos módulos (a agenda não depende do pagamento; quem escuta o prazo é o pagamento) e o
 * contrato 1.11.0 (código novo no enum e na tabela do contrato-api.md; a antecedência, o horizonte e o FORA_DO_HORIZONTE
 * marcados como deprecated). O lint do OpenAPI roda no CI e na PR.
 */
class ProrrogacaoModulosTest {

    private static final Path CONTRATO = Path.of("..", "docs", "contrato").toAbsolutePath().normalize();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object pai, String... caminho) {
        Object atual = pai;
        for (String chave : caminho) {
            atual = ((Map<String, Object>) atual).get(chave);
        }
        return (Map<String, Object>) atual;
    }

    private static Map<String, Object> openapi() throws IOException {
        try (Reader r = Files.newBufferedReader(CONTRATO.resolve("openapi.yaml"), StandardCharsets.UTF_8)) {
            return new Yaml().load(r);
        }
    }

    @Test
    @DisplayName("A21: ModulithTest e ArquiteturaTest seguem valendo: a agenda não depende do pagamento e o pagamento depende da agenda (o PrazoDaReservaProrrogado vai por evento)")
    void agendaNaoConheceOPagamento() {
        ApplicationModules modulos = ApplicationModules.of(AltasHorasApplication.class);
        modulos.verify();
        assertThat(modulos.getModuleByName("agenda").orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString())).doesNotContain("pagamento");
        assertThat(modulos.getModuleByName("pagamento").orElseThrow().getDirectDependencies(modulos).uniqueModules()
                .map(m -> m.getIdentifier().toString())).contains("agenda");
    }

    @Test
    @DisplayName("A21: PRORROGACAO_FORA_DA_JANELA existe no CodigoErro (422) e na tabela de erros do contrato-api.md e no enum do openapi.yaml")
    @SuppressWarnings("unchecked")
    void codigoNovoNoEnumENoContrato() throws IOException {
        CodigoErro codigo = CodigoErro.valueOf("PRORROGACAO_FORA_DA_JANELA");
        assertThat(codigo.status()).isEqualTo(422);
        assertThat(codigo.titulo()).isEqualTo("Fora da janela de prorrogação");
        assertThat(codigo.detalhePadrao()).isEqualTo("Só dá para acrescentar tempo no último minuto.");

        String texto = Files.readString(CONTRATO.resolve("contrato-api.md"), StandardCharsets.UTF_8);
        assertThat(texto.lines().filter(l -> l.startsWith("| 422 |") && l.contains("`PRORROGACAO_FORA_DA_JANELA`")))
                .as("linha do código na tabela de erros").hasSize(1);
        // A 1.11.0 introduziu o código; versões menores seguintes só acrescentam (1.12.0, ADR 0015).
        assertThat(texto).containsPattern("# 📑 Contrato da API · v1\\.(1[1-9])\\.\\d+");

        Map<String, Object> contrato = openapi();
        assertThat(String.valueOf(mapa(contrato, "info").get("version"))).matches("1\\.(1[1-9])\\.\\d+");
        assertThat((List<String>) mapa(contrato, "components", "schemas", "CodigoErro").get("enum"))
                .contains("PRORROGACAO_FORA_DA_JANELA", "FORA_DO_HORIZONTE");
    }

    @Test
    @DisplayName("A21: FORA_DO_HORIZONTE, antecedenciaMinimaMin e horizonteDias estão marcados deprecated no contrato; a API não devolve mais o primeiro")
    void deprecadosNoContrato() throws IOException {
        Map<String, Object> contrato = openapi();
        assertThat((String) mapa(contrato, "components", "schemas", "CodigoErro").get("description"))
                .contains("FORA_DO_HORIZONTE").contains("deprecated");
        Map<String, Object> configuracao = mapa(contrato, "components", "schemas", "ConfiguracaoAgenda", "properties");
        assertThat(mapa(configuracao, "antecedenciaMinimaMin").get("deprecated")).isEqualTo(true);
        assertThat(mapa(configuracao, "horizonteDias").get("deprecated")).isEqualTo(true);
        assertThat((List<String>) mapa(contrato, "components", "schemas", "ConfiguracaoAgenda").get("required"))
                .doesNotContain("antecedenciaMinimaMin", "horizonteDias");
        Map<String, Object> caminhos = mapa(contrato, "paths");
        assertThat(caminhos).containsKeys("/agendamentos/{id}/prorrogacao", "/agendamentos/guest/{id}/prorrogacao");
    }
}
