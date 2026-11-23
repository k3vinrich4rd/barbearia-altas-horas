package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A30 da Fatia 10B (H33 revisada, beta, a revisar pelo advogado): o nome de exibição do barbeiro desligado fica nas reservas por
 * 5 anos depois da última reserva e depois vira pseudônimo; o IP da auditoria some depois de 1 ano. O relógio do teste está em
 * terça 01/12/2026, 09:30; 5 anos antes é 01/12/2021, 09:30 (hora de Osasco).
 */
class RetencaoDeBarbeiroIT extends PrivacidadeBase {

    @Autowired
    RetencaoDeDados retencao;

    private UUID barbeiroDesligado(String nome) {
        UUID id = DadosDeTeste.barbeiro(jdbc, false);
        jdbc.update("UPDATE barbeiro SET nome_exibicao = ? WHERE id = ?", nome, id);
        return id;
    }

    private UUID reservaComFimEm(UUID barbeiro, LocalDate dia, int hora, int minuto) {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID id = UUID.randomUUID();
        var inicio = DadosDeTeste.em(dia, hora, minuto).minusMinutes(30);
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'CONCLUIDO', 4500)
                """, id, barbeiro, servico, inicio, inicio.plusMinutes(30), inicio.plusMinutes(30));
        return id;
    }

    private String nome(UUID barbeiro) {
        return jdbc.queryForObject("SELECT nome_exibicao FROM barbeiro WHERE id = ?", String.class, barbeiro);
    }

    @Test
    @DisplayName("A30: barbeiro desligado com a última reserva terminando há 5 anos e 1 minuto vira pseudônimo (as reservas ficam apontando para ele); com 1 minuto a menos o nome é mantido; ativo nunca muda; rodar de novo não muda nada")
    void pseudonimoDepoisDeCincoAnos() throws Exception {
        UUID vencido = barbeiroDesligado("Nome Antigo Teste");
        UUID noPrazo = barbeiroDesligado("Nome Recente Teste");
        UUID ativoAntigo = DadosDeTeste.barbeiro(jdbc, true);
        jdbc.update("UPDATE barbeiro SET nome_exibicao = 'Nome Ativo Teste' WHERE id = ?", ativoAntigo);
        UUID reservaVencida = reservaComFimEm(vencido, LocalDate.of(2021, 12, 1), 9, 29);
        reservaComFimEm(noPrazo, LocalDate.of(2021, 12, 1), 9, 31);
        reservaComFimEm(ativoAntigo, LocalDate.of(2020, 1, 10), 10, 0);

        assertThat(retencao.executarEm(relogio.instant()).get("barbeiro_desligado")).isEqualTo(1);

        assertThat(nome(vencido)).startsWith("Barbeiro desligado ").doesNotContain("Antigo");
        assertThat(nome(noPrazo)).isEqualTo("Nome Recente Teste");
        assertThat(nome(ativoAntigo)).isEqualTo("Nome Ativo Teste");
        assertThat(jdbc.queryForObject("SELECT barbeiro_id FROM reserva WHERE id = ?", UUID.class, reservaVencida)).isEqualTo(vencido);
        String pseudonimo = nome(vencido);

        assertThat(retencao.executarEm(relogio.instant()).get("barbeiro_desligado")).isZero();
        assertThat(nome(vencido)).isEqualTo(pseudonimo);
    }

    @Test
    @DisplayName("A30: barbeiro desligado que nunca atendeu conta o prazo do desligamento (atualizado_em); e o IP da auditoria some depois de 1 ano, mantendo ator, ação e recurso")
    void semReservaEIpDaAuditoria() throws Exception {
        UUID nuncaAtendeu = barbeiroDesligado("Sem Reserva Teste");
        jdbc.update("UPDATE barbeiro SET atualizado_em = ? WHERE id = ?", DadosDeTeste.em(LocalDate.of(2021, 11, 1), 10, 0), nuncaAtendeu);
        UUID barbeiroDeAgora = barbeiroDesligado("Sem Reserva Recente");
        Conta dono = conta("ADMIN", "BARBEIRO");
        UUID velha = jdbc.queryForObject("""
                INSERT INTO auditoria_admin (ator_id, acao, recurso, recurso_id, detalhe, ip, ocorrido_em)
                VALUES (?, 'BARBEIRO_DESLIGADO', 'barbeiro', ?, '{"campos":"ativo"}'::jsonb, ?, ?) RETURNING id
                """, UUID.class, dono.id(), nuncaAtendeu, IP, DadosDeTeste.em(LocalDate.of(2025, 11, 30), 9, 0));

        retencao.executarEm(relogio.instant());

        assertThat(nome(nuncaAtendeu)).startsWith("Barbeiro desligado ");
        assertThat(nome(barbeiroDeAgora)).isEqualTo("Sem Reserva Recente");
        assertThat(jdbc.queryForObject("SELECT ip FROM auditoria_admin WHERE id = ?", String.class, velha)).isNull();
        assertThat(jdbc.queryForObject("SELECT acao || '/' || ator_id FROM auditoria_admin WHERE id = ?", String.class, velha))
                .isEqualTo("BARBEIRO_DESLIGADO/" + dono.id());
    }
}
