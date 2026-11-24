package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.cep.CepApi;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/**
 * A região do cliente a partir do CEP, SÓ com a caixinha de região marcada (Fatia 11, E1 e E2). O CEP é consultado uma vez e
 * descartado: devolve bairro, cidade, uf e município do IBGE. R31: provedor fora, CEP inexistente ou qualquer erro viram "sem
 * região"; do erro sobe só a classe, porque a mensagem de um cliente HTTP pode trazer o CEP (R30).
 */
@Component
public class RegiaoDoCliente {

    private static final Logger LOG = LoggerFactory.getLogger(RegiaoDoCliente.class);

    /** A localidade resolvida. Não imprime nada: região de uma pessoa não vai para log (R30). */
    public record Resolvida(String bairro, String cidade, String uf, String municipioIbge) {

        @Override
        public String toString() {
            return "Resolvida[omitida]";
        }
    }

    private final CepApi cepApi;

    RegiaoDoCliente(CepApi cepApi) {
        this.cepApi = cepApi;
    }

    /** A caixinha foi marcada com um CEP? Sem CEP não há o que autorizar (A5). */
    static boolean autorizou(Boolean autorizaRegiao, String cep) {
        return Boolean.TRUE.equals(autorizaRegiao) && cep != null && !cep.isBlank();
    }

    /** 400 se o cliente leu um texto que não é o vigente (A3): a prova do consentimento é o texto exato. */
    static void conferirVersao(Boolean autorizaRegiao, String versaoTextoRegiao) {
        if (Boolean.TRUE.equals(autorizaRegiao) && !Consentimentos.VERSAO_DO_TEXTO.equals(versaoTextoRegiao)) {
            throw ExcecaoDeNegocio.campoInvalido("versaoTextoRegiao",
                    "Informe a versão vigente do texto de autorização de região (" + Consentimentos.VERSAO_DO_TEXTO + ").");
        }
    }

    Optional<Resolvida> resolver(String cep) {
        try {
            return cepApi.buscar(Cep.de(cep)).filter(e -> e.cidade() != null && e.uf() != null)
                    .map(e -> new Resolvida(e.bairro(), e.cidade(), e.uf(), e.municipioIbge()));
        } catch (RuntimeException falha) {
            LOG.warn("região do cliente não resolvida ({})", falha.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
