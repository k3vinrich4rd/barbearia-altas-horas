package io.github.k3vinrich4rd.altashoras.rag;

/** Um pedaço de um documento: o que vira um vetor. */
public record Trecho(String arquivo, String titulo, int linha, String texto) {
}
