package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A11, A12, A13 e A27 da Fatia 11B: o alcance conta PESSOAS, não atendimentos (ADR 0014, item 4). */
class PessoasDistintasIT extends AlcanceBase {

    private static final LocalDate ANTES = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("A11: uma pessoa com 5 atendimentos no mesmo bairro conta 1 cliente; com mais 3 pessoas são 4 (e não 8): sozinha não libera o município, o bairro nem a faixa pública")
    void cincoAtendimentosDeUmaPessoaSaoUmCliente() throws Exception {
        Catalogo c = catalogo();
        Contato dela = Contato.novo();
        for (int i = 0; i < 5; i++) {
            atendimentoDe(dela, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO", CENTRO);
        }
        pessoas(3, c.barbeiro(), c.servico(), CENTRO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[?(@.tipo == 'MUNICIPIO')]")).isEmpty(); // 4 pessoas < k
        assertThat(this.<Boolean>ler(corpo, "$.totalVisivel")).isFalse();

        vitrineLimpa();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/alcance")
                .with(io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip(io.github.k3vinrich4rd.altashoras.ContaDeTeste.ipNovo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.regioes").isEmpty());
    }

    private void vitrineLimpa() {
        caches.getCacheNames().forEach(nome -> caches.getCache(nome).clear());
        vitrine.esquecer();
    }

    /** Cinco outras pessoas em Osasco/Centro, para o par que será testado fechar 5 se contar como UMA pessoa (6 se contar como duas). */
    private void quatroOutras(Catalogo c) {
        pessoas(4, c.barbeiro(), c.servico(), CENTRO);
    }

    private void limparReservas() {
        jdbc.update("DELETE FROM reserva");
    }

    @Test
    @DisplayName("A12: conta e visitante com o mesmo WhatsApp, com o mesmo e-mail, e e-mails que só diferem em maiúsculas: UMA pessoa nos três casos; nenhum hash de pessoa vai para uma coluna")
    void mesmaPessoaNaContaENoVisitante() throws Exception {
        Catalogo c = catalogo();

        // 1) o mesmo WhatsApp
        UUID conta = clienteComRegiao(CENTRO);
        jdbc.update("UPDATE usuario SET whatsapp = '11955550001' WHERE id = ?", conta);
        atendimentoDaConta(conta, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO");
        atendimentoDe(new Contato("11955550001", "outro.email.1@exemplo.test"), c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO,
                "CONCLUIDO", CENTRO);
        quatroOutras(c);
        assertThat(this.<Integer>ler(painel("?nivel=MUNICIPIO"), "$.grupos[0].clientes")).as("mesmo WhatsApp").isEqualTo(5);

        // 2) o mesmo e-mail
        limparReservas();
        UUID conta2 = clienteComRegiao(CENTRO);
        jdbc.update("UPDATE usuario SET email = 'mesma.pessoa@exemplo.test', whatsapp = '11955550002' WHERE id = ?", conta2);
        atendimentoDaConta(conta2, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO");
        atendimentoDe(new Contato("11955559999", "mesma.pessoa@exemplo.test"), c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO,
                "CONCLUIDO", CENTRO);
        quatroOutras(c);
        assertThat(this.<Integer>ler(painel("?nivel=MUNICIPIO"), "$.grupos[0].clientes")).as("mesmo e-mail").isEqualTo(5);

        // 3) e-mails que só diferem em maiúsculas (dois visitantes, WhatsApp diferentes)
        limparReservas();
        atendimentoDe(new Contato("11955550003", "Fulano.Teste@Exemplo.Test"), c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO,
                "CONCLUIDO", CENTRO);
        atendimentoDe(new Contato("11955550004", "fulano.teste@exemplo.test"), c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO,
                "CONCLUIDO", CENTRO);
        quatroOutras(c);
        assertThat(this.<Integer>ler(painel("?nivel=MUNICIPIO"), "$.grupos[0].clientes")).as("e-mail em maiúsculas").isEqualTo(5);

        // Nenhuma coluna nova guarda o identificador de pessoa: as colunas de hash do esquema são só as de sempre
        assertThat(jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND (column_name LIKE '%hash%' OR column_name LIKE '%hmac%' OR column_name LIKE '%pessoa%')
                  AND table_name <> 'flyway_schema_history'
                ORDER BY 1""", String.class)).containsExactlyInAnyOrder("cobranca.chave_idempotencia_hash", "cobranca.hash_corpo",
                "convite_conta.token_hash", "refresh_token.hash", "reserva_guest.token_hash", "usuario.senha_hash");
    }

    @Test
    @DisplayName("A13: pessoa com reserva no Centro e, depois, outra em Presidente Altino: conta uma vez, em Presidente Altino (a mais recente com região)")
    void contaNoBairroDaReservaMaisRecente() throws Exception {
        Catalogo c = catalogo();
        Contato dela = Contato.novo();
        atendimentoDe(dela, c.barbeiro(), c.servico(), ANTES, "CONCLUIDO", CENTRO);
        atendimentoDe(dela, c.barbeiro(), c.servico(), DIA_DO_ATENDIMENTO, "CONCLUIDO", ALTINO);
        pessoas(4, c.barbeiro(), c.servico(), ALTINO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(5);
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[?(@.nome == 'Presidente Altino')].clientes")).containsExactly(5);
        assertThat(this.<List<Object>>ler(corpo, "$.grupos[0].filhos[?(@.nome == 'Centro')]")).isEmpty();
    }

    @Test
    @DisplayName("A27: reserva manual, EXPIRADO e CANCELADO não contam (D127, E31): 5 pessoas atendidas e mais 3 manuais, 1 expirada e 1 cancelada em Osasco/Centro continuam sendo 5")
    void soConfirmadoEConcluidoContam() throws Exception {
        Catalogo c = catalogo();
        pessoas(5, c.barbeiro(), c.servico(), CENTRO);
        atendimento(c.barbeiro(), c.servico(), "CONFIRMADO", null); // sem região: só entra em "sem região"
        for (int i = 0; i < 3; i++) {
            OffsetDateTime inicio = DadosDeTeste.em(DIA_DO_ATENDIMENTO.plusDays(1), 7, 0).plusMinutes(30L * i);
            jdbc.update("""
                    INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos, nome_cliente,
                                         regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge, regiao_consentimento_versao)
                    VALUES (?, ?, 'MANUAL', ?, ?, ?, 'CONFIRMADO', 5500, 'Fulano Manual', 'Centro', 'Osasco', 'SP', '3534401', '1.2')
                    """, c.barbeiro(), c.servico(), inicio, inicio.plusMinutes(30), inicio.plusMinutes(30));
        }
        atendimento(c.barbeiro(), c.servico(), "EXPIRADO", CENTRO);
        atendimento(c.barbeiro(), c.servico(), "CANCELADO", CENTRO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(5);
        assertThat(this.<Integer>ler(corpo, "$.semRegiao.clientes")).isNull(); // 1 pessoa sem região: escondida (menos de k)
        assertThat(this.<Boolean>ler(corpo, "$.semRegiao.abaixoDoLimite")).isTrue();
    }
}
