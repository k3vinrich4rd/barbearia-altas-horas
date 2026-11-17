package io.github.k3vinrich4rd.altashoras.rag;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A parte "R" do RAG, em modo híbrido:
 *   - identificador (R15, D40, A29, HORARIO_INDISPONIVEL, "ADR 0011") vai por busca EXATA no texto;
 *   - pergunta em linguagem natural vai por VETOR (cosseno entre o vetor da pergunta e o de cada trecho).
 * Por quê: medido em avaliacao.tsv, o vetor sozinho acerta as perguntas, mas erra todos os identificadores:
 * para o modelo, "R15" e "R16" são quase o mesmo texto.
 */
@Component
class Buscador {

    /** Tudo em maiúsculas, dígitos e "_", com pelo menos um dígito ou "_": R15, D40, A29, HORARIO_INDISPONIVEL. */
    private static final Pattern IDENTIFICADOR = Pattern.compile("^(?=.*[0-9_])[A-Z][A-Z0-9_]{1,40}$");
    private static final Pattern ADR = Pattern.compile("^ADR ?(\\d{4})$");

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;

    Buscador(VectorStore vectorStore, JdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    /** Achado da busca, venha do vetor ou da busca exata. */
    record Achado(String arquivo, String titulo, String linha, String texto, String como, Double pontuacao) {
    }

    List<Achado> buscar(String pergunta, int quantos, boolean hibrido) {
        String termo = pergunta.strip();
        if (hibrido) {
            var adr = ADR.matcher(termo);
            if (adr.matches()) {
                List<Achado> exatos = exata("metadata->>'arquivo' LIKE ?", "%adr/" + adr.group(1) + "%", termo, quantos);
                if (!exatos.isEmpty()) {
                    return exatos;
                }
            } else if (IDENTIFICADOR.matcher(termo).matches()) {
                // Palavra inteira: R1 não pode trazer R15.
                List<Achado> exatos = exata("content ~ ?", "(^|[^A-Za-z0-9_])" + termo + "([^A-Za-z0-9_]|$)", termo, quantos);
                if (!exatos.isEmpty()) {
                    return exatos;
                }
            }
        }
        return vectorStore.similaritySearch(SearchRequest.builder().query(termo).topK(quantos).build()).stream()
                .map((Document d) -> new Achado(String.valueOf(d.getMetadata().get("arquivo")),
                        String.valueOf(d.getMetadata().get("titulo")), String.valueOf(d.getMetadata().get("linha")),
                        d.getText(), "vetor", d.getScore()))
                .toList();
    }

    /**
     * Busca exata. Primeiro o trecho que DEFINE o identificador (linha de tabela que começa com ele: "| R15 |"),
     * depois os que mais o citam.
     */
    private List<Achado> exata(String filtro, String parametro, String termo, int quantos) {
        String sql = """
                SELECT metadata->>'arquivo', metadata->>'titulo', metadata->>'linha', content
                FROM vector_store
                WHERE %s
                ORDER BY (position('| ' || ? || ' |' in content) > 0) DESC,
                         (length(content) - length(replace(content, ?, ''))) DESC
                LIMIT ?
                """.formatted(filtro);
        return jdbc.query(sql, (rs, i) -> new Achado(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                "exata", null), parametro, termo, termo, quantos);
    }

    void imprimir(String pergunta, int quantos) {
        if (pergunta.isBlank()) {
            System.out.println("uso: buscar \"pergunta\"");
            return;
        }
        System.out.println("Pergunta: " + pergunta);
        int posicao = 1;
        for (Achado achado : buscar(pergunta, quantos, true)) {
            String texto = achado.texto().replaceAll("\\s+", " ");
            String pontos = achado.pontuacao() == null ? "exata" : "%.3f".formatted(achado.pontuacao());
            System.out.printf("%n%d. [%s] %s:%s · %s%n   %s%n", posicao++, pontos, achado.arquivo(), achado.linha(),
                    achado.titulo(), texto.length() > 240 ? texto.substring(0, 240) + "..." : texto);
        }
    }
}
