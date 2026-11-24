package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.cep.CepApi;
import io.github.k3vinrich4rd.altashoras.cep.CepIndisponivel;
import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;

/**
 * Caso de uso do módulo: cache primeiro, depois ViaCEP e, só se ele falhar, BrasilAPI (E3 e E5). Sem retry: o fallback
 * já é a segunda tentativa. Resposta de provedor entra no cache (encontrado 7 dias, inexistente 1 hora); falha nunca.
 * O log diz só "resolvido: sim/não" e a fonte; o CEP, nem parcial, não vai para log (E8, R30).
 */
@Service
class BuscarEndereco implements CepApi {

    private static final Logger LOG = LoggerFactory.getLogger(BuscarEndereco.class);

    private final ResolverRegiao regiao;
    private final ProvedorDeCep viaCep;
    private final ProvedorDeCep brasilApi;
    private final Cache encontrados;
    private final Cache inexistentes;

    BuscarEndereco(ViaCepCliente viaCep, BrasilApiCliente brasilApi, CacheManager cacheDoCep, ResolverRegiao regiao) {
        this.regiao = regiao;
        this.viaCep = viaCep;
        this.brasilApi = brasilApi;
        this.encontrados = cacheDoCep.getCache(CepConfig.CACHE_ENCONTRADO);
        this.inexistentes = cacheDoCep.getCache(CepConfig.CACHE_INEXISTENTE);
    }

    @Override
    public Optional<Endereco> buscar(Cep cep) {
        if (!cep.valido()) {
            throw new IllegalArgumentException("CEP deve ter 8 dígitos");
        }
        String chave = cep.digitos();
        Endereco guardado = encontrados.get(chave, Endereco.class);
        if (guardado != null) {
            LOG.info("cep resolvido: sim, fonte=CACHE");
            return Optional.of(guardado.comFonte(Endereco.Fonte.CACHE));
        }
        if (inexistentes.get(chave) != null) {
            LOG.info("cep resolvido: não, fonte=CACHE");
            return Optional.empty();
        }

        Optional<Endereco> resultado = consultarProvedores(cep).map(regiao::completar);
        resultado.ifPresentOrElse(endereco -> encontrados.put(chave, endereco),
                () -> inexistentes.put(chave, Boolean.TRUE));
        LOG.info("cep resolvido: {}, fonte={}", resultado.isPresent() ? "sim" : "não",
                resultado.map(e -> e.fonte().name()).orElse("PROVEDOR"));
        return resultado;
    }

    private Optional<Endereco> consultarProvedores(Cep cep) {
        try {
            return viaCep.consultar(cep);
        } catch (FalhaDoProvedor falha) {
            avisar(falha);
        }
        try {
            return brasilApi.consultar(cep);
        } catch (FalhaDoProvedor falha) {
            avisar(falha);
            throw new CepIndisponivel();
        }
    }

    private static void avisar(FalhaDoProvedor falha) {
        LOG.warn("provedor de CEP falhou: provedor={} tipo={}", falha.provedor(), falha.tipo());
    }
}
