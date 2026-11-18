package io.github.k3vinrich4rd.altashoras.cep;

import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_INEXISTENTE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_ERRO;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.VIACEP_SE;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.json;
import static io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.viaCepDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cache.caffeine.CaffeineCache;

import io.github.k3vinrich4rd.altashoras.cep.consulta.CepProps;

/**
 * A8 a A12: o cache em memória (E5). O tempo é o do {@code TickerFalso}: "passar 7 dias" é uma linha, e nenhum teste
 * espera de verdade. O que vale nos testes é o que está no ADR 0008: 7 dias para encontrado, 1 hora para inexistente.
 */
@CepTeste
class CepCacheIT extends CepTesteBase {

    @Test
    @DisplayName("A8: ViaCEP já respondeu 01001000: a consulta seguinte dá 200 com fonte CACHE e nenhuma nova chamada externa")
    void segundaConsultaVemDoCache() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE)); // uma expectativa só: a segunda chamada à rede lançaria erro

        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("VIACEP"));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("CACHE"))
                .andExpect(jsonPath("$.bairro").value("Sé")).andExpect(jsonPath("$.uf").value("SP"));
        provedores.verificar();
    }

    @Test
    @DisplayName("A9: encontrado vale 7 dias: com 6 dias, 23 h, 59 min e 59 s ainda é CACHE sem chamada; com 7 dias consulta o ViaCEP de novo")
    void encontradoVale7Dias() throws Exception {
        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        consultar(CEP_SE).andExpect(jsonPath("$.fonte").value("VIACEP"));
        provedores.verificar();
        provedores.reiniciar(); // zera as expectativas já cumpridas: daqui em diante, nenhuma chamada é esperada

        ticker.avancar(Duration.ofDays(6).plusHours(23).plusMinutes(59).plusSeconds(59));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("CACHE"));
        provedores.verificar();

        ticker.avancar(Duration.ofSeconds(1)); // agora fecham 7 dias
        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("VIACEP"));
    }

    @Test
    @DisplayName("A10: inexistente vale 1 hora: com 59 min o 404 vem do cache sem chamada; com 1 h consulta o ViaCEP de novo")
    void inexistenteVale1Hora() throws Exception {
        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));
        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound());
        provedores.verificar();
        provedores.reiniciar();

        ticker.avancar(Duration.ofMinutes(59));
        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CEP_NAO_ENCONTRADO"));
        provedores.verificar();

        ticker.avancar(Duration.ofMinutes(1)); // fecha 1 hora
        provedores.viaCep(CEP_INEXISTENTE, json(VIACEP_ERRO));
        consultar(CEP_INEXISTENTE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A11: os dois provedores fora e depois o ViaCEP volta: 503 e em seguida 200 com fonte VIACEP (a falha nunca ficou em cache)")
    void falhaNuncaEntraNoCache() throws Exception {
        provedores.osDoisFora(CEP_SE);
        consultar(CEP_SE).andExpect(status().isServiceUnavailable());
        provedores.verificar();
        provedores.reiniciar();

        provedores.viaCep(CEP_SE, json(VIACEP_SE));
        consultar(CEP_SE).andExpect(status().isOk()).andExpect(jsonPath("$.fonte").value("VIACEP"));
    }

    @Test
    @DisplayName("A12: com o teto em 3 entradas, 4 CEPs diferentes e cleanUp deixam no máximo 3 guardadas; o padrão de produção é 10 000")
    void tetoDoCache() throws Exception {
        String[] ceps = {"01001000", "01002000", "01003000", "01004000"};
        for (String cep : ceps) {
            provedores.viaCep(cep, json(viaCepDe(cep, "Bairro " + cep.substring(0, 5))));
        }
        for (String cep : ceps) {
            consultar(cep).andExpect(status().isOk());
        }

        CaffeineCache encontrados = (CaffeineCache) caches.getCache("cep-encontrado");
        encontrados.getNativeCache().cleanUp();
        assertThat(encontrados.getNativeCache().estimatedSize()).isLessThanOrEqualTo(3);

        // O teto de 3 é só destes testes; sem nenhuma propriedade, o binding dá o número do ADR 0008.
        CepProps padrao = new Binder(new MapConfigurationPropertySource(Map.of())).bindOrCreate("app.cep", CepProps.class);
        assertThat(padrao.cacheMax()).isEqualTo(10_000);
        assertThat(padrao.cacheEncontradoTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(padrao.cacheInexistenteTtl()).isEqualTo(Duration.ofHours(1));
    }
}
