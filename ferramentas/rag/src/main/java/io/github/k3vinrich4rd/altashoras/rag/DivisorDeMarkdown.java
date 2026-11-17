package io.github.k3vinrich4rd.altashoras.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide um Markdown em trechos (chunking). Regra: cada título (#, ##, ###) abre um trecho; trecho grande é
 * cortado em blocos de até TAMANHO_MAXIMO caracteres, em quebra de linha, para uma linha de tabela nunca ficar
 * pela metade. Cada trecho carrega o título da seção: é o contexto que faz "R15" de uma tabela ser encontrado
 * por uma pergunta sobre "regras de negócio".
 * Função pura, sem Spring: testável em milissegundos (mesmo critério do ADR 0011).
 */
public final class DivisorDeMarkdown {

    static final int TAMANHO_MAXIMO = 1200;
    static final int TAMANHO_MINIMO = 40;

    private DivisorDeMarkdown() {
    }

    public static List<Trecho> dividir(String arquivo, String conteudo) {
        List<Trecho> trechos = new ArrayList<>();
        String titulo = arquivo;
        StringBuilder bloco = new StringBuilder();
        int linhaDoBloco = 1;
        boolean emCodigo = false;
        String[] linhas = conteudo.split("\n", -1);

        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i].stripTrailing();
            if (linha.startsWith("```")) {
                emCodigo = !emCodigo;
            }
            boolean ehTitulo = !emCodigo && linha.matches("^#{1,3} .+");
            if (ehTitulo || bloco.length() + linha.length() > TAMANHO_MAXIMO) {
                fechar(trechos, arquivo, titulo, linhaDoBloco, bloco);
                linhaDoBloco = i + 1;
            }
            if (ehTitulo) {
                titulo = linha.replaceFirst("^#+ ", "");
            }
            bloco.append(linha).append('\n');
        }
        fechar(trechos, arquivo, titulo, linhaDoBloco, bloco);
        return trechos;
    }

    private static void fechar(List<Trecho> trechos, String arquivo, String titulo, int linha, StringBuilder bloco) {
        String texto = bloco.toString().strip();
        bloco.setLength(0);
        if (texto.length() >= TAMANHO_MINIMO) {
            trechos.add(new Trecho(arquivo, titulo, linha, texto));
        }
    }
}
