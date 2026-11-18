package io.github.k3vinrich4rd.altashoras.privacidade.titular;

/** A "PoliticaPrivacidade" do contrato: a versão é a mesma que o PUT de consentimento exige. */
record PoliticaPrivacidadeResposta(String versao, String textoConsentimento, String politica, String atualizadaEm) {
}
