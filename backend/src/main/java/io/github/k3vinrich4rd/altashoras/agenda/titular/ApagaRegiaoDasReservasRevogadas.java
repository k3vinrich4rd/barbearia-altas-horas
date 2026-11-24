package io.github.k3vinrich4rd.altashoras.agenda.titular;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.privacidade.RegiaoRevogada;

/**
 * A agenda ouvindo a privacidade (Fatia 11, E3, A4): quando a conta retira a autorização de região, a região some de TODAS as
 * reservas dela, e a pessoa passa a "sem região" no alcance. Listener SÍNCRONO, na mesma transação da revogação. Idempotente.
 * A reserva em si (serviço, horário, status) não muda.
 */
@Component
class ApagaRegiaoDasReservasRevogadas {

    private final JdbcClient jdbc;

    ApagaRegiaoDasReservasRevogadas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void aoRevogarRegiao(RegiaoRevogada evento) {
        jdbc.sql("""
                UPDATE reserva SET regiao_bairro = NULL, regiao_cidade = NULL, regiao_uf = NULL,
                                   regiao_municipio_ibge = NULL, regiao_consentimento_versao = NULL
                WHERE cliente_id = :cliente
                  AND (regiao_cidade IS NOT NULL OR regiao_uf IS NOT NULL OR regiao_consentimento_versao IS NOT NULL)
                """).param("cliente", evento.usuarioId()).update();
    }
}
