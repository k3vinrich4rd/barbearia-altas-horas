package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;

/** A36 a A38 da Fatia 13: o selo e a lista "Novidades" do painel. Terça 01/12/2026. */
class AvisosDoPainelIT extends NotificacaoBase {

    private UUID aviso(UUID usuario, String servico, int minutosAtras, boolean lido) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO aviso_barbeiro (id, usuario_id, reserva_id, servico, inicio, criado_em, lido_em)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, usuario, UUID.randomUUID(), servico, as(10, 0),
                OffsetDateTime.now().minusMinutes(minutosAtras), lido ? OffsetDateTime.now() : null);
        return id;
    }

    @Test
    @DisplayName("A36: 3 avisos do Eduardo, 1 já lido: naoLidas = 2, do mais novo ao mais antigo, sem nome nem WhatsApp do cliente; limite de 1 a 50 (fora disso 400)")
    void listaDoPainel() throws Exception {
        Barbeiro eduardo = barbeiroComConta("ADMIN", "BARBEIRO");
        aviso(eduardo.conta().id(), "Corte Antigo", 30, true);
        aviso(eduardo.conta().id(), "Corte Meio", 20, false);
        UUID maisNovo = aviso(eduardo.conta().id(), "Corte Novo", 10, false);

        get("/admin/notificacoes", eduardo.conta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(2))
                .andExpect(jsonPath("$.itens.length()").value(3))
                .andExpect(jsonPath("$.itens[0].id").value(maisNovo.toString()))
                .andExpect(jsonPath("$.itens[0].servico").value("Corte Novo"))
                .andExpect(jsonPath("$.itens[0].lido").value(false))
                .andExpect(jsonPath("$.itens[2].servico").value("Corte Antigo"))
                .andExpect(jsonPath("$.itens[2].lido").value(true))
                .andExpect(jsonPath("$.itens[0].reservaId").exists())
                .andExpect(jsonPath("$.itens[0].inicio").exists())
                .andExpect(jsonPath("$.itens[0].criadoEm").exists())
                .andExpect(jsonPath("$.itens[0].nome").doesNotExist())
                .andExpect(jsonPath("$.itens[0].whatsapp").doesNotExist())
                .andExpect(jsonPath("$.itens[0].cliente").doesNotExist());
        get("/admin/notificacoes?limite=2", eduardo.conta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2)).andExpect(jsonPath("$.naoLidas").value(2));
        get("/admin/notificacoes?limite=0", eduardo.conta()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        get("/admin/notificacoes?limite=51", eduardo.conta()).andExpect(status().isBadRequest());
        get("/admin/notificacoes?limite=50", eduardo.conta()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A37: 100 avisos da conta: a lista faz 2 consultas ao banco (página e contagem) além da checagem do token, o mesmo número de com 1 aviso, e devolve no máximo 20 itens por padrão")
    void duasConsultas() throws Exception {
        Barbeiro pouco = barbeiroComConta("BARBEIRO");
        aviso(pouco.conta().id(), "Corte Unico", 5, false);
        Barbeiro muito = barbeiroComConta("BARBEIRO");
        jdbc.update("""
                INSERT INTO aviso_barbeiro (id, usuario_id, reserva_id, servico, inicio, criado_em)
                SELECT gen_random_uuid(), ?, gen_random_uuid(), 'Corte ' || g, now(), now() - (g || ' minutes')::interval
                FROM generate_series(1, 100) g
                """, muito.conta().id());
        get("/admin/notificacoes", pouco.conta()).andExpect(status().isOk()); // aquece o que o primeiro uso carrega

        long antes = ContadorDeSql.total();
        get("/admin/notificacoes", pouco.conta()).andExpect(status().isOk());
        long comUm = ContadorDeSql.total() - antes;
        antes = ContadorDeSql.total();
        get("/admin/notificacoes", muito.conta()).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(20)).andExpect(jsonPath("$.naoLidas").value(100));
        long comCem = ContadorDeSql.total() - antes;

        // 1 da checagem do token (credencial) + a página + a contagem: sem N+1.
        assertThat(comCem).isEqualTo(comUm).isEqualTo(3);
    }

    @Test
    @DisplayName("A38: avisos não lidos e POST /admin/notificacoes/marcar-lidas: 204 e naoLidas = 0")
    void marcarLidas() throws Exception {
        Barbeiro barbeiro = barbeiroComConta("BARBEIRO");
        aviso(barbeiro.conta().id(), "Corte Um", 10, false);
        aviso(barbeiro.conta().id(), "Corte Dois", 5, false);

        post("/admin/notificacoes/marcar-lidas", barbeiro.conta()).andExpect(status().isNoContent());

        get("/admin/notificacoes", barbeiro.conta()).andExpect(jsonPath("$.naoLidas").value(0))
                .andExpect(jsonPath("$.itens.length()").value(2));
    }
}
