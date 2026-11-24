package io.github.k3vinrich4rd.altashoras.agenda.titular;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.privacidade.LimpezaPorRetencao;

/**
 * A agenda limpando o contato do visitante sem conta (R29, E19). Passou o prazo ({@code app.privacidade.retencao-guest-dias},
 * padrão 90) do FIM da reserva, inclusive no dia exato, e ainda não anonimizado: o nome, o e-mail e o WhatsApp viram
 * valores sem dono e o token do link vira o SHA-256 de um valor aleatório (o link guardado pelo visitante passa a dar 404).
 * O CEP não é mais guardado (Fatia 11); a região da reserva (bairro, cidade e uf, sem nome nem contato) agora também some aqui (E3)
 * fica, porque a estatística de alcance não identifica ninguém. A reserva e a cobrança não mudam. Idempotente ({@code anonimizado_em IS NULL}), em lotes de até 500 linhas por
 * transação e sem travar quem estiver sendo usado ({@code SKIP LOCKED}).
 */
@Component
class RetencaoDeVisitantes implements LimpezaPorRetencao {

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final Duration prazo;

    RetencaoDeVisitantes(JdbcClient jdbc, PlatformTransactionManager gerenciador,
            @Value("${app.privacidade.retencao-guest-dias:90}") int dias) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);
        this.prazo = Duration.ofDays(dias);
    }

    @Override
    public String nome() {
        return "reserva_guest";
    }

    @Override
    public int executar(Instant agora) {
        OffsetDateTime limite = OffsetDateTime.ofInstant(agora.minus(prazo), ZoneOffset.UTC);
        OffsetDateTime hoje = OffsetDateTime.ofInstant(agora, ZoneOffset.UTC);
        // A região da reserva sai junto com o contato (Fatia 11, E3, A10): a mesma passada, no mesmo lote, na mesma transação.
        return LimpezaPorRetencao.emLotes(transacao, () -> jdbc.sql("""
                WITH lote AS (
                    SELECT g.reserva_id FROM reserva_guest g JOIN reserva r ON r.id = g.reserva_id
                    WHERE g.anonimizado_em IS NULL AND r.fim <= :limite
                    ORDER BY r.fim, g.reserva_id LIMIT :lote FOR UPDATE OF g SKIP LOCKED),
                regiao AS (
                    UPDATE reserva SET regiao_bairro = NULL, regiao_cidade = NULL, regiao_uf = NULL,
                                       regiao_municipio_ibge = NULL, regiao_consentimento_versao = NULL
                    WHERE id IN (SELECT reserva_id FROM lote))
                UPDATE reserva_guest SET nome = 'Visitante anonimizado',
                       email = 'anonimizado-' || reserva_id || '@anonimo.invalid',
                       whatsapp = '00000000000',
                       token_hash = encode(sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')), 'hex'),
                       anonimizado_em = :agora
                WHERE reserva_id IN (SELECT reserva_id FROM lote)
                """)
                .param("agora", hoje).param("limite", limite).param("lote", LimpezaPorRetencao.TAMANHO_DO_LOTE)
                .update());
    }
}
