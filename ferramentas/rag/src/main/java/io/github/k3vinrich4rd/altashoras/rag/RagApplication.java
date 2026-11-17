package io.github.k3vinrich4rd.altashoras.rag;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Linha de comando do RAG da documentação.
 *   indexar            lê os Markdown do repositório, divide em trechos, gera os vetores e grava no pgvector
 *   buscar "pergunta"  devolve os trechos mais parecidos em significado com a pergunta
 *   avaliar            roda as perguntas de avaliacao.tsv e mede quantas acham o documento esperado
 */
@SpringBootApplication
public class RagApplication implements ApplicationRunner {

    private final Indexador indexador;
    private final Buscador buscador;
    private final Avaliador avaliador;

    RagApplication(Indexador indexador, Buscador buscador, Avaliador avaliador) {
        this.indexador = indexador;
        this.buscador = buscador;
        this.avaliador = avaliador;
    }

    public static void main(String[] args) {
        // Saída em UTF-8 mesmo no console do Windows (acentos legíveis no Git Bash e em arquivo).
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        SpringApplication.run(RagApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> palavras = args.getNonOptionArgs();
        String comando = palavras.isEmpty() ? "" : palavras.getFirst();
        switch (comando) {
            case "indexar" -> indexador.indexar();
            case "buscar" -> buscador.imprimir(String.join(" ", palavras.subList(1, palavras.size())), 5);
            case "avaliar" -> avaliador.avaliar();
            default -> System.out.println("uso: indexar | buscar \"pergunta\" | avaliar");
        }
    }
}
