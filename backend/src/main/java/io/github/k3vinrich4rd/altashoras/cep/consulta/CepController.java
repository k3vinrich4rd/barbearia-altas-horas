package io.github.k3vinrich4rd.altashoras.cep.consulta;

import java.time.Duration;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.cep.CepApi;
import io.github.k3vinrich4rd.altashoras.cep.CepIndisponivel;
import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * {@code GET /cep/{cep}}, pública. Só faz o que a E1 manda: sanitizar, limitar, chamar o {@link CepApi} e traduzir
 * (vazio vira 404, {@link CepIndisponivel} vira 503). O formato é conferido ANTES de qualquer limite ou chamada externa,
 * e só 8 dígitos chegam à URL do provedor (E2).
 */
@RestController
class CepController {

    private static final Pattern FORMATO = Pattern.compile("[0-9]{5}-?[0-9]{3}");

    private final CepApi cepApi;
    private final LimitadorDeTentativas limitador;
    private final int limite;
    private final Duration janela;

    CepController(CepApi cepApi, LimitadorDeTentativas limitador, @Value("${app.limites.cep-max:30}") int limite,
            @Value("${app.limites.cep-janela:1m}") Duration janela) {
        this.cepApi = cepApi;
        this.limitador = limitador;
        this.limite = limite;
        this.janela = janela;
    }

    @GetMapping("/cep/{cep}")
    Endereco buscar(@PathVariable String cep, HttpServletRequest request) {
        if (!FORMATO.matcher(cep).matches()) {
            throw new ExcecaoDeNegocio(CodigoErro.CEP_INVALIDO);
        }
        String chave = "cep|" + request.getRemoteAddr();
        long bloqueado = limitador.segundosBloqueado(chave, limite, janela);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        limitador.registrar(chave, janela);
        try {
            return cepApi.buscar(Cep.de(cep)).orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.CEP_NAO_ENCONTRADO));
        } catch (CepIndisponivel indisponivel) {
            throw new ExcecaoDeNegocio(CodigoErro.CEP_INDISPONIVEL);
        }
    }
}
