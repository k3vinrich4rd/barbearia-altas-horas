package io.github.k3vinrich4rd.altashoras.cep;

/**
 * Os dois provedores (ViaCEP e BrasilAPI) falharam. Não é "CEP não existe": quem chama decide o que fazer. A rota
 * pública responde 503 e o cadastro segue sem endereço (R31). A mensagem é fixa e não tem causa: exceção de cliente
 * HTTP carrega a URL, e a URL carrega o CEP.
 */
public class CepIndisponivel extends RuntimeException {

    public CepIndisponivel() {
        super("Consulta de CEP indisponível", null, false, false);
    }
}
