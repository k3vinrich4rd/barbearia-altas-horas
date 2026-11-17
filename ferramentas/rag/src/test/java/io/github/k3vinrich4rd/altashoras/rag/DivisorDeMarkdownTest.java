package io.github.k3vinrich4rd.altashoras.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DivisorDeMarkdownTest {

    @Test
    @DisplayName("Cada título abre um trecho e o trecho guarda o título e a linha onde começa")
    void divideporTitulo() {
        String md = "# Guia\n\nIntrodução com texto suficiente para passar do tamanho mínimo.\n\n"
                + "## Regras\n\n| R15 | reserva vencida não ocupa horário na disponibilidade |\n";
        List<Trecho> trechos = DivisorDeMarkdown.dividir("docs/x.md", md);
        assertThat(trechos).hasSize(2);
        assertThat(trechos.get(0).titulo()).isEqualTo("Guia");
        assertThat(trechos.get(1)).satisfies(t -> {
            assertThat(t.titulo()).isEqualTo("Regras");
            assertThat(t.linha()).isEqualTo(5);
            assertThat(t.texto()).contains("R15");
        });
    }

    @Test
    @DisplayName("Seção grande é cortada em quebra de linha: nenhum trecho passa do limite nem parte uma linha")
    void cortaSecaoGrande() {
        StringBuilder md = new StringBuilder("## Tabela\n");
        for (int i = 1; i <= 60; i++) {
            md.append("| R").append(i).append(" | uma regra de negócio com texto de tamanho médio para o teste |\n");
        }
        List<Trecho> trechos = DivisorDeMarkdown.dividir("docs/x.md", md.toString());
        assertThat(trechos).hasSizeGreaterThan(2).allSatisfy(t -> {
            assertThat(t.texto().length()).isLessThanOrEqualTo(DivisorDeMarkdown.TAMANHO_MAXIMO);
            assertThat(t.titulo()).isEqualTo("Tabela");
            assertThat(t.texto()).endsWith("|");
        });
    }

    @Test
    @DisplayName("Cerquilha dentro de bloco de código não vira título; trecho minúsculo é descartado")
    void codigoETrechoPequeno() {
        String md = "## Comandos\n\n```bash\n# isto é comentário de shell, não título\ndocker compose up -d\n```\n\n## Vazio\n\nok\n";
        List<Trecho> trechos = DivisorDeMarkdown.dividir("docs/x.md", md);
        assertThat(trechos).hasSize(1);
        assertThat(trechos.getFirst().titulo()).isEqualTo("Comandos");
    }
}
