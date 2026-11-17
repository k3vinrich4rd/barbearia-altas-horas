package io.github.k3vinrich4rd.altashoras.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lê os Markdown públicos do repositório e grava os trechos no pgvector. Reindexar apaga tudo antes. */
@Component
class Indexador {

    private static final Logger LOG = LoggerFactory.getLogger(Indexador.class);
    private static final int LOTE = 32;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final Path raiz;
    private final List<String> pastas;

    Indexador(VectorStore vectorStore, JdbcTemplate jdbc, @Value("${rag.raiz}") String raiz,
            @Value("${rag.pastas}") List<String> pastas) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.raiz = Path.of(raiz).toAbsolutePath().normalize();
        this.pastas = pastas;
    }

    void indexar() throws IOException {
        List<Trecho> trechos = new ArrayList<>();
        int arquivos = 0;
        for (String pasta : pastas) {
            Path inicio = raiz.resolve(pasta);
            if (!Files.exists(inicio)) {
                continue;
            }
            try (Stream<Path> caminho = Files.walk(inicio)) {
                for (Path arquivo : caminho.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                    String relativo = raiz.relativize(arquivo).toString().replace('\\', '/');
                    trechos.addAll(DivisorDeMarkdown.dividir(relativo, Files.readString(arquivo, StandardCharsets.UTF_8)));
                    arquivos++;
                }
            }
        }

        jdbc.execute("TRUNCATE TABLE vector_store");
        long inicio = System.nanoTime();
        for (int i = 0; i < trechos.size(); i += LOTE) {
            List<Document> lote = trechos.subList(i, Math.min(i + LOTE, trechos.size())).stream()
                    // O título vai junto do texto: é ele que dá contexto ao vetor de uma linha solta de tabela.
                    .map(t -> new Document(t.titulo() + "\n" + t.texto(),
                            Map.<String, Object>of("arquivo", t.arquivo(), "titulo", t.titulo(), "linha", t.linha())))
                    .toList();
            vectorStore.add(lote);
        }
        LOG.info("Indexados {} trechos de {} arquivos em {} s", trechos.size(), arquivos,
                (System.nanoTime() - inicio) / 1_000_000_000);
        System.out.printf("indexados %d trechos de %d arquivos%n", trechos.size(), arquivos);
    }
}
