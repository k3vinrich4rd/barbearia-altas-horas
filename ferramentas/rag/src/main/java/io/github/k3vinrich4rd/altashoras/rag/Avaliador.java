package io.github.k3vinrich4rd.altashoras.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Eval da recuperação: para cada pergunta de avaliacao.tsv, o documento esperado aparece entre os 3 primeiros?
 * Sem isto, "o RAG funciona" é impressão. Com isto, é um número que piora ou melhora quando se troca o modelo,
 * o tamanho do trecho ou a estratégia de busca. Roda nos dois modos para mostrar o que a busca híbrida muda.
 */
@Component
class Avaliador {

    private static final int TOP = 3;

    private final Buscador buscador;

    Avaliador(Buscador buscador) {
        this.buscador = buscador;
    }

    void avaliar() throws IOException {
        List<String> linhas = new ClassPathResource("avaliacao.tsv").getContentAsString(StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        int soVetor = rodar(linhas, false, false);
        int hibrido = rodar(linhas, true, true);
        System.out.printf("%nacerto@%d · só vetor: %d de %d · híbrido: %d de %d%n", TOP, soVetor, linhas.size(), hibrido,
                linhas.size());
    }

    private int rodar(List<String> linhas, boolean hibrido, boolean detalhar) {
        int acertos = 0;
        for (String linha : linhas) {
            String[] partes = linha.split("\t");
            List<String> achados = buscador.buscar(partes[0], TOP, hibrido).stream().map(Buscador.Achado::arquivo).toList();
            boolean acertou = achados.stream().anyMatch(a -> a.contains(partes[1]));
            acertos += acertou ? 1 : 0;
            if (detalhar) {
                System.out.printf("%s %s%n     esperado: %s · achou: %s%n", acertou ? "OK  " : "ERRO", partes[0], partes[1],
                        achados);
            }
        }
        return acertos;
    }
}
