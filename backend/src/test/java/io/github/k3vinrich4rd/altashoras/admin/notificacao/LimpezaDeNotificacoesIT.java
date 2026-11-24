package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A34 e A35 da Fatia 13: as notificações têm o ciclo de vida da conta do barbeiro (desligar, excluir) e a retenção por prazo (LGPD). */
class LimpezaDeNotificacoesIT extends NotificacaoBase {

    @Autowired
    LimpezaDeNotificacoes limpeza;

    private void aviso(UUID usuario, int diasAtras) {
        jdbc.update("""
                INSERT INTO aviso_barbeiro (id, usuario_id, reserva_id, servico, inicio, criado_em)
                VALUES (?, ?, ?, 'Corte Teste', now(), ?)
                """, UUID.randomUUID(), usuario, UUID.randomUUID(),
                java.time.OffsetDateTime.ofInstant(relogio.instant().minus(java.time.Duration.ofDays(diasAtras)), java.time.ZoneOffset.UTC));
    }

    private void inscricao(UUID usuario, String endpoint, Integer diasDesdeOEnvio, int diasDesdeACriacao) {
        jdbc.update("""
                INSERT INTO inscricao_push (id, usuario_id, endpoint, chave_p256dh, chave_auth, criada_em, ultimo_envio_ok)
                VALUES (?, ?, ?, 'p', 'a', ?, ?)
                """, UUID.randomUUID(), usuario, endpoint,
                java.time.OffsetDateTime.ofInstant(relogio.instant().minus(java.time.Duration.ofDays(diasDesdeACriacao)), java.time.ZoneOffset.UTC),
                diasDesdeOEnvio == null ? null : java.time.OffsetDateTime.ofInstant(
                        relogio.instant().minus(java.time.Duration.ofDays(diasDesdeOEnvio)), java.time.ZoneOffset.UTC));
    }

    @Test
    @DisplayName("A34: barbeiro desligado (Fatia 10) e conta excluída (LGPD): as inscrições e os avisos dessas contas não existem mais")
    void desligarEExcluirApagam() throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        Barbeiro desligado = barbeiroComConta("BARBEIRO");
        aviso(desligado.conta().id(), 1);
        inscricao(desligado.conta().id(), "https://fcm.googleapis.com/fcm/send/desligado", 1, 5);
        Conta cliente = conta("CLIENTE");
        aviso(cliente.id(), 1);
        inscricao(cliente.id(), "https://fcm.googleapis.com/fcm/send/excluido", 1, 5);
        Barbeiro quemFica = barbeiroComConta("BARBEIRO");
        aviso(quemFica.conta().id(), 1);
        inscricao(quemFica.conta().id(), "https://fcm.googleapis.com/fcm/send/fica", 1, 5);

        mvc.perform(MockMvcRequestBuilders.put("/admin/barbeiros/" + desligado.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"nomeExibicao\": \"Barbeiro Desligado\", \"usuarioId\": \"" + desligado.conta().id()
                        + "\", \"ativo\": false, \"version\": 0 }"))
                .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.delete("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isNoContent());

        for (UUID conta : List.of(desligado.conta().id(), cliente.id())) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push WHERE usuario_id = ?", Long.class, conta)).isZero();
            assertThat(avisosDa(conta)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push WHERE usuario_id = ?", Long.class, quemFica.conta().id()))
                .isEqualTo(1);
        assertThat(avisosDa(quemFica.conta().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("A35: avisos com 31 dias e inscrição sem envio ok há 91 dias, mais itens recentes: o job diário apaga só os velhos")
    void retencao() {
        Conta barbeiro = conta("BARBEIRO");
        aviso(barbeiro.id(), 31);
        aviso(barbeiro.id(), 29);
        aviso(barbeiro.id(), 1);
        inscricao(barbeiro.id(), "https://fcm.googleapis.com/fcm/send/envio-velho", 91, 200);
        inscricao(barbeiro.id(), "https://fcm.googleapis.com/fcm/send/nunca-enviou-velha", null, 91);
        inscricao(barbeiro.id(), "https://fcm.googleapis.com/fcm/send/envio-recente", 10, 200);
        inscricao(barbeiro.id(), "https://fcm.googleapis.com/fcm/send/nova", null, 10);

        int apagadas = limpeza.executar(relogio.instant());

        assertThat(apagadas).isEqualTo(3);
        assertThat(avisosDa(barbeiro.id())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT endpoint FROM inscricao_push ORDER BY endpoint", String.class)).containsExactly(
                "https://fcm.googleapis.com/fcm/send/envio-recente", "https://fcm.googleapis.com/fcm/send/nova");
        assertThat(limpeza.executar(relogio.instant())).as("idempotente").isZero();
        assertThat(limpeza.nome()).isEqualTo("notificacoes");
    }
}
