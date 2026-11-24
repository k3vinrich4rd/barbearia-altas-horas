package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas;

/**
 * Base dos testes da Fatia 13: o contexto do painel mais os dois canais falsos (nenhum teste fala com a rede real) e
 * as tabelas de notificação vazias a cada teste. Nomes, contas e endpoints são fictícios.
 */
@SpringBootTest(properties = "app.teste.contexto=notificacao")
@Import(CanaisFalsos.class)
public abstract class NotificacaoBase extends PainelBase {

    @Autowired
    protected CanalFalso falsoWebPush;

    @Autowired
    protected CanalFalso falsoEmail;

    @Autowired
    protected Reservas reservas;

    /** Um barbeiro com a conta ligada (a conta tem o papel BARBEIRO) e o token dela. */
    protected record Barbeiro(UUID id, Conta conta) {
    }

    @BeforeEach
    void limparNotificacoes() {
        jdbc.update("DELETE FROM aviso_barbeiro");
        jdbc.update("DELETE FROM inscricao_push");
        falsoWebPush.limpar();
        falsoEmail.limpar();
    }

    protected Barbeiro barbeiroComConta(String... papeis) {
        Conta conta = conta(papeis);
        return new Barbeiro(barbeiroDaConta(conta.id()), conta);
    }

    protected UUID servicoDeTeste(String nome) {
        return DadosDeTeste.servico(jdbc, nome, 30, 4500, true);
    }

    /** Reserva aguardando pagamento do visitante, no horário dado, com 20 minutos de prazo no relógio de teste. */
    protected UUID reservaAguardando(UUID barbeiro, UUID servico, OffsetDateTime inicio) {
        return comAceite(reserva(barbeiro, servico, inicio, "AGUARDANDO_PAGAMENTO",
                relogio.instant().plusSeconds(20 * 60), null, "Visitante Teste"));
    }

    /** O aceite da política que o pagamento real grava antes de confirmar (R18); a V1 exige para CONFIRMADO. */
    protected UUID comAceite(UUID reserva) {
        jdbc.update("UPDATE reserva SET aceite_politica_versao = 1, aceite_politica_em = now() WHERE id = ?", reserva);
        return reserva;
    }

    protected void inscrever(UUID usuarioId, String endpoint) {
        jdbc.update("""
                INSERT INTO inscricao_push (id, usuario_id, endpoint, chave_p256dh, chave_auth, criada_em)
                VALUES (?, ?, ?, 'chave-p256dh-de-teste', 'chave-auth-de-teste', now())
                """, UUID.randomUUID(), usuarioId, endpoint);
    }

    protected long avisosDa(UUID usuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM aviso_barbeiro WHERE usuario_id = ?", Long.class, usuarioId);
    }

    protected long publicacoesDeReservaConfirmada() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE event_type LIKE '%ReservaConfirmada'",
                Long.class);
    }
}
