package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.CanalFalso.Envio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ResultadoDoEnvio;
import io.github.k3vinrich4rd.altashoras.agenda.ReservaConfirmada;

/**
 * A8 a A14 da Fatia 13: o ouvinte {@code AvisaBarbeiroDaReserva}. Terça 01/12/2026 (a data do texto confere o dia da semana).
 * O {@code CanalFalso} grava o que recebeu; nenhuma chamada sai para a rede.
 */
class AvisaBarbeiroDaReservaIT extends NotificacaoBase {

    @Autowired
    IncompleteEventPublications incompletas;

    @Autowired
    org.springframework.context.ApplicationEventPublisher eventos;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager gerenciador;

    private UUID reservaConfirmada(Barbeiro barbeiro, String servico) {
        UUID reserva = reserva(barbeiro.id(), servicoDeTeste(servico), as(10, 0), "CONFIRMADO", null, null, "Visitante Teste");
        return reserva;
    }

    private void publicar(UUID reserva, UUID barbeiro) {
        new org.springframework.transaction.support.TransactionTemplate(gerenciador).executeWithoutResult(
                s -> eventos.publishEvent(new ReservaConfirmada(reserva, barbeiro, as(10, 0).toInstant())));
    }

    @Test
    @DisplayName("A8: ReservaConfirmada de uma reserva do Eduardo: um aviso para a conta dele com o texto 'Novo horário: Corte Degradê, sex 27/11 às 10:00' (27/11/2026 é sexta)")
    void gravaOAvisoComOTexto() throws Exception {
        Barbeiro eduardo = barbeiroComConta("ADMIN", "BARBEIRO");
        jdbc.update("INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo) VALUES (?, 'Corte Degradê', 30, 4500, true)",
                UUID.randomUUID());
        UUID servico = jdbc.queryForObject("SELECT id FROM servico WHERE nome = 'Corte Degradê'", UUID.class);
        irPara(java.time.LocalDate.of(2026, 11, 24), 9, 0);
        UUID reserva = comAceite(reserva(eduardo.id(), servico, io.github.k3vinrich4rd.altashoras.DadosDeTeste.em(
                java.time.LocalDate.of(2026, 11, 27), 10, 0), "AGUARDANDO_PAGAMENTO", relogio.instant().plusSeconds(1200), null, "Visitante Teste"));
        inscrever(eduardo.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");

        reservas.confirmarPagamento(reserva);
        esperarOutboxVazio();

        assertThat(jdbc.queryForList("SELECT servico FROM aviso_barbeiro WHERE usuario_id = ?", String.class, eduardo.conta().id()))
                .containsExactly("Corte Degradê");
        assertThat(falsoWebPush.envios()).hasSize(1);
        assertThat(falsoWebPush.envios().getFirst().aviso().texto()).isEqualTo("Novo horário: Corte Degradê, sex 27/11 às 10:00");
    }

    @Test
    @DisplayName("A9: o mesmo evento entregue duas vezes pelo outbox: uma linha em aviso_barbeiro e um envio por canal")
    void entregaDuplicadaNaoRepete() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaConfirmada(barbeiro, "Corte Clássico");
        inscrever(barbeiro.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");

        publicar(reserva, barbeiro.id());
        publicar(reserva, barbeiro.id());
        esperarOutboxVazio();

        assertThat(avisosDa(barbeiro.conta().id())).isEqualTo(1);
        assertThat(falsoWebPush.envios()).hasSize(1);
    }

    @Test
    @DisplayName("A10: reserva de barbeiro desligado ou sem conta ligada: nenhum aviso, nenhuma exceção, evento concluído")
    void semDestinatario() throws Exception {
        Barbeiro desligado = barbeiroComConta("BARBEIRO");
        jdbc.update("UPDATE barbeiro SET ativo = false WHERE id = ?", desligado.id());
        UUID semConta = io.github.k3vinrich4rd.altashoras.DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = servicoDeTeste("Corte Clássico");
        UUID r1 = reserva(desligado.id(), servico, as(10, 0), "CONFIRMADO", null, null, "Visitante Um");
        UUID r2 = reserva(semConta, servico, as(11, 0), "CONFIRMADO", null, null, "Visitante Dois");
        inscrever(desligado.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");

        publicar(r1, desligado.id());
        publicar(r2, semConta);
        esperarOutboxVazio();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_barbeiro", Long.class)).isZero();
        assertThat(falsoWebPush.envios()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE status = 'FAILED'", Long.class)).isZero();
    }

    @Test
    @DisplayName("A11: barbeiro sem nenhum aparelho inscrito: o aviso do painel existe, nenhum canal é chamado, push_enviado_em preenchido e evento concluído")
    void semAparelho() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaConfirmada(barbeiro, "Corte Clássico");

        publicar(reserva, barbeiro.id());
        esperarOutboxVazio();

        assertThat(avisosDa(barbeiro.conta().id())).isEqualTo(1);
        assertThat(falsoWebPush.envios()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT push_enviado_em IS NOT NULL FROM aviso_barbeiro WHERE reserva_id = ?",
                Boolean.class, reserva)).isTrue();
    }

    @Test
    @DisplayName("A12: falha passageira no canal: o aviso do painel continua gravado, o evento fica pendente, a reserva continua CONFIRMADO e a reentrega chama o canal de novo sem criar segundo aviso")
    void falhaPassageiraFicaPendente() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaAguardando(barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0));
        inscrever(barbeiro.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");
        falsoWebPush.responder(d -> ResultadoDoEnvio.TENTAR_DEPOIS);

        reservas.confirmarPagamento(reserva);
        esperarEntregaFalhar();

        assertThat(statusDaReserva(reserva)).isEqualTo("CONFIRMADO");
        assertThat(avisosDa(barbeiro.conta().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT push_enviado_em IS NULL FROM aviso_barbeiro WHERE reserva_id = ?", Boolean.class,
                reserva)).isTrue();
        assertThat(falsoWebPush.envios()).hasSize(1);

        falsoWebPush.responder(d -> ResultadoDoEnvio.ENVIADO);
        incompletas.resubmitIncompletePublications(publicacao -> publicacao.getEvent() instanceof ReservaConfirmada);
        esperarOutboxVazio();

        assertThat(falsoWebPush.envios()).hasSize(2);
        assertThat(avisosDa(barbeiro.conta().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT push_enviado_em IS NOT NULL FROM aviso_barbeiro WHERE reserva_id = ?",
                Boolean.class, reserva)).isTrue();
    }

    @Test
    @DisplayName("A13: dois aparelhos e o primeiro devolve DESTINO_INVALIDO: o segundo recebe e a inscrição do primeiro é apagada")
    void destinoInvalidoEhApagado() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaConfirmada(barbeiro, "Corte Clássico");
        String morto = "https://fcm.googleapis.com/fcm/send/aparelho-morto";
        String vivo = "https://fcm.googleapis.com/fcm/send/aparelho-vivo";
        inscrever(barbeiro.conta().id(), morto);
        jdbc.update("UPDATE inscricao_push SET criada_em = now() - interval '1 day' WHERE endpoint = ?", morto);
        inscrever(barbeiro.conta().id(), vivo);
        falsoWebPush.responder(d -> d.aparelho().endpoint().equals(morto) ? ResultadoDoEnvio.DESTINO_INVALIDO : ResultadoDoEnvio.ENVIADO);

        publicar(reserva, barbeiro.id());
        esperarOutboxVazio();

        assertThat(falsoWebPush.envios()).hasSize(2);
        assertThat(jdbc.queryForList("SELECT endpoint FROM inscricao_push", String.class)).containsExactly(vivo);
        assertThat(jdbc.queryForObject("SELECT ultimo_envio_ok IS NOT NULL FROM inscricao_push WHERE endpoint = ?", Boolean.class,
                vivo)).isTrue();
    }

    @Test
    @DisplayName("A14: o canal nunca é chamado dentro de transação (nenhuma conexão presa por chamada de rede)")
    void envioForaDeTransacao() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaConfirmada(barbeiro, "Corte Clássico");
        inscrever(barbeiro.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");

        publicar(reserva, barbeiro.id());
        esperarOutboxVazio();

        List<Envio> envios = falsoWebPush.envios();
        assertThat(envios).isNotEmpty().allSatisfy(e -> assertThat(e.emTransacao()).isFalse());
    }

    @Test
    @DisplayName("A12: o push recusado (403) mantém a inscrição, e depois de 5 tentativas o aviso fica só em Novidades: sem reenvio infinito (D178)")
    void desisteDepoisDeCincoTentativas() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        UUID reserva = reservaAguardando(barbeiro.id(), servicoDeTeste("Corte Clássico"), as(10, 0));
        inscrever(barbeiro.conta().id(), "https://fcm.googleapis.com/fcm/send/aparelho-1");
        falsoWebPush.responder(d -> ResultadoDoEnvio.RECUSADO);

        reservas.confirmarPagamento(reserva);
        for (int rodada = 1; rodada <= 5; rodada++) {
            int esperada = rodada;
            esperar(() -> falsoWebPush.envios().size() == esperada);
            esperar(() -> jdbc.queryForObject("SELECT tentativas FROM aviso_barbeiro WHERE reserva_id = ?", Integer.class, reserva) == esperada);
            if (rodada < 5) {
                esperar(() -> jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE status = 'FAILED'", Long.class) == 1);
                incompletas.resubmitIncompletePublications(publicacao -> publicacao.getEvent() instanceof ReservaConfirmada);
            }
        }

        esperarOutboxVazio();
        assertThat(jdbc.queryForObject("SELECT push_enviado_em IS NOT NULL FROM aviso_barbeiro WHERE reserva_id = ?", Boolean.class, reserva)).isTrue();
        assertThat(avisosDa(barbeiro.conta().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push", Long.class)).isEqualTo(1);
        incompletas.resubmitIncompletePublications(publicacao -> true);
        Thread.sleep(500);
        assertThat(falsoWebPush.envios()).hasSize(5);
    }
}
