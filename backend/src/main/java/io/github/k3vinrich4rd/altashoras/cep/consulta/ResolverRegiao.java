package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.cep.TabelaIbge;

/**
 * Completa o endereço com o município do IBGE (Fatia 11, A26). Ordem: o código que o provedor devolveu, se existir na
 * tabela embutida; senão o município pelo nome (sem acento) e UF, só se houver exatamente um; senão fica sem município e o
 * cliente entra no alcance só pela UF. Nada aqui chama a rede: é consulta à tabela em memória (E16). Nunca lança: a região
 * nunca derruba o cadastro nem a reserva (R31).
 */
@Component
class ResolverRegiao {

    private final TabelaIbge tabela;

    ResolverRegiao(TabelaIbge tabela) {
        this.tabela = tabela;
    }

    Endereco completar(Endereco endereco) {
        Optional<TabelaIbge.Municipio> municipio = Optional.ofNullable(endereco.municipioIbge()).flatMap(tabela::porCodigo)
                .filter(m -> endereco.uf() == null || m.uf().equals(endereco.uf()));
        if (municipio.isEmpty() && endereco.cidade() != null && endereco.uf() != null) {
            municipio = tabela.porNomeEUf(endereco.cidade(), endereco.uf());
        }
        return new Endereco(endereco.cep(), endereco.logradouro(), endereco.bairro(), endereco.cidade(), endereco.uf(),
                municipio.map(TabelaIbge.Municipio::codigo).orElse(null), endereco.fonte());
    }
}
