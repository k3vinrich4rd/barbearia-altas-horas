package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Duration;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * {@code DELETE /agendamentos/guest/{id}/regiao} (Fatia 11, A6, LGPD art. 8º, § 5º): o visitante retira a autorização de
 * região com o {@code X-Guest-Token} da própria reserva e a região some na hora. Token errado, ausente ou de outra reserva
 * responde 404 com o mesmo corpo de "não existe" (R23), e o tempo de resposta não separa os casos. Idempotente: retirar de novo
 * é 204. A reserva em si não muda. Limite por IP, o mesmo mecanismo da vitrine (429 com Retry-After).
 */
@Service
class RetirarRegiaoDoVisitante {

    private static final String HASH_INEXISTENTE = "0".repeat(64);

    private final ReservaGuestRepository visitantes;
    private final JdbcClient jdbc;
    private final LimitadorDeTentativas limitador;
    private final int limite;
    private final Duration janela;

    RetirarRegiaoDoVisitante(ReservaGuestRepository visitantes, JdbcClient jdbc, LimitadorDeTentativas limitador,
            @Value("${app.limites.regiao-visitante-max:30}") int limite,
            @Value("${app.limites.regiao-visitante-janela:1m}") Duration janela) {
        this.visitantes = visitantes;
        this.jdbc = jdbc;
        this.limitador = limitador;
        this.limite = limite;
        this.janela = janela;
    }

    @Transactional
    void retirar(UUID reservaId, @Nullable String token, String ip) {
        String chave = "regiao-visitante|" + ip;
        long bloqueado = limitador.segundosBloqueado(chave, limite, janela);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        limitador.registrar(chave, janela);
        // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
        String hashGuardado = visitantes.findById(reservaId).map(ReservaGuest::getTokenHash).orElse(HASH_INEXISTENTE);
        if (!TokenGuest.confere(token, hashGuardado) || HASH_INEXISTENTE.equals(hashGuardado)) {
            throw new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
        }
        jdbc.sql("""
                UPDATE reserva SET regiao_bairro = NULL, regiao_cidade = NULL, regiao_uf = NULL,
                                   regiao_municipio_ibge = NULL, regiao_consentimento_versao = NULL
                WHERE id = :id
                """).param("id", reservaId).update();
    }
}
