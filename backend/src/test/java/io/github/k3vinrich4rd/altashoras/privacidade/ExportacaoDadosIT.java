package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A33 a A37: a exportação do titular (LGPD art. 18, E14): completa, só dele, sem segredo e sem cache. */
class ExportacaoDadosIT extends PrivacidadeBase {

    private static final LocalDate QUARTA = LocalDate.of(2026, 12, 2);
    private static final LocalDate QUINTA = LocalDate.of(2026, 12, 3);

    private record Cenario(Cliente cliente, UUID servico, UUID confirmada, UUID cancelada) {
    }

    /** Cliente com 3 linhas de consentimento (como no A26) e 2 reservas: uma CONFIRMADO e uma CANCELADO. */
    private Cenario clienteComHistorico() throws Exception {
        relogio.reiniciar();
        String email = cadastrar(false); // linha 1: 17/11 08:00, aceito=false
        irPara(TERCA, 9, 30);
        Cliente cliente = entrar(email);
        consentimentoEm(cliente.id(), true, DadosDeTeste.em(TERCA, 9, 0)); // linha 2
        consentimentoEm(cliente.id(), false, DadosDeTeste.em(TERCA, 9, 15)); // linha 3
        jdbc.update("UPDATE usuario SET cep = '01001000', bairro = 'Sé', cidade = 'São Paulo', uf = 'SP' WHERE id = ?",
                cliente.id());

        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID confirmada = reserva(barbeiro, servico, DadosDeTeste.em(QUARTA, 10, 0), "CONFIRMADO", null, cliente.id(), null);
        UUID cancelada = reserva(barbeiro, servico, DadosDeTeste.em(QUINTA, 11, 0), "CANCELADO", null, cliente.id(), null);
        jdbc.update("UPDATE reserva SET motivo_cancelamento = 'Barbeiro doente' WHERE id = ?", cancelada);
        return new Cenario(cliente, servico, confirmada, cancelada);
    }

    @Test
    @DisplayName("A33: GET /me/dados devolve geradoEm de 01/12 09:30, a conta, os 3 consentimentos do mais antigo ao mais novo e as 2 reservas com id, início, fim, status, serviço, preço e motivo")
    void exportacaoCompleta() throws Exception {
        Cenario c = clienteComHistorico();

        get("/me/dados", c.cliente().acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.geradoEm").value("2026-12-01T09:30:00-03:00"))
                .andExpect(jsonPath("$.conta.nome").value("Cliente Teste"))
                .andExpect(jsonPath("$.conta.email").value(c.cliente().email()))
                .andExpect(jsonPath("$.conta.whatsapp").value("11900000001"))
                .andExpect(jsonPath("$.conta.endereco.cep").value("01001000"))
                .andExpect(jsonPath("$.conta.endereco.bairro").value("Sé"))
                .andExpect(jsonPath("$.conta.endereco.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.conta.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.consentimentos.length()").value(3))
                .andExpect(jsonPath("$.consentimentos[0].aceito").value(false))
                .andExpect(jsonPath("$.consentimentos[0].registradoEm").value("2026-11-17T08:00:00-03:00"))
                .andExpect(jsonPath("$.consentimentos[1].aceito").value(true))
                .andExpect(jsonPath("$.consentimentos[2].aceito").value(false))
                .andExpect(jsonPath("$.consentimentos[2].versaoTexto").value("1.0"))
                .andExpect(jsonPath("$.agendamentos.length()").value(2))
                .andExpect(jsonPath("$.agendamentos[0].id").value(c.confirmada().toString()))
                .andExpect(jsonPath("$.agendamentos[0].inicio").value("2026-12-02T10:00:00-03:00"))
                .andExpect(jsonPath("$.agendamentos[0].fim").value("2026-12-02T10:30:00-03:00"))
                .andExpect(jsonPath("$.agendamentos[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.agendamentos[0].servico").value("Corte Clássico " + c.servico()))
                .andExpect(jsonPath("$.agendamentos[0].precoCentavos").value(4500))
                .andExpect(jsonPath("$.agendamentos[0].motivoCancelamento").doesNotExist())
                .andExpect(jsonPath("$.agendamentos[1].id").value(c.cancelada().toString()))
                .andExpect(jsonPath("$.agendamentos[1].status").value("CANCELADO"))
                .andExpect(jsonPath("$.agendamentos[1].motivoCancelamento").value("Barbeiro doente"));
    }

    @Test
    @DisplayName("A34: o JSON não tem senha, senhaHash, versaoCredencial, token, cpf, ip nem usuarioId (conferido no texto cru, inclusive o valor do hash e do refresh)")
    void semSegredoNoTextoCru() throws Exception {
        Cenario c = clienteComHistorico();
        UUID outro = novoCliente(true).id();
        String hashDaSenha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id = ?", String.class,
                c.cliente().id());

        String corpo = get("/me/dados", c.cliente().acesso()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("senha").doesNotContain("Senha").doesNotContain("versaoCredencial")
                .doesNotContain("token").doesNotContain("Token").doesNotContain("cpf").doesNotContain("Cpf")
                .doesNotContain("\"ip\"").doesNotContain("usuarioId").doesNotContain("ativo")
                .doesNotContain(hashDaSenha).doesNotContain(c.cliente().refresh()).doesNotContain(outro.toString());
    }

    @Test
    @DisplayName("A35: como A, só aparecem as reservas de A; as do cliente B e a do visitante (no mesmo horário de outro dia) não")
    void soAsReservasDoTitular() throws Exception {
        Cenario a = clienteComHistorico();
        Cliente b = novoCliente(false);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID reservaDeB = reserva(barbeiro, a.servico(), DadosDeTeste.em(QUARTA, 10, 0), "CONFIRMADO", null, b.id(), null);
        UUID reservaDoVisitante = reserva(barbeiro, a.servico(), DadosDeTeste.em(QUINTA, 11, 0), "CONFIRMADO", null, null,
                "Visitante Fictício");

        String corpo = get("/me/dados", a.cliente().acesso()).andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).contains(a.confirmada().toString(), a.cancelada().toString())
                .doesNotContain(reservaDeB.toString()).doesNotContain(reservaDoVisitante.toString())
                .doesNotContain("Visitante Fictício").doesNotContain(b.email());
    }

    @Test
    @DisplayName("A36: a resposta traz Cache-Control no-store; o banco ganha 1 linha EXPORTACAO em solicitacao_titular (concluida_em 01/12 09:30) e nenhuma em auditoria_admin")
    void semCacheERegistraOPedido() throws Exception {
        Cliente cliente = novoCliente(false);
        long auditoriaAntes = linhasDeAuditoria();

        get("/me/dados", cliente.acesso()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        var pedidos = jdbc.queryForList("SELECT tipo, solicitada_em, concluida_em FROM solicitacao_titular WHERE usuario_id = ?",
                cliente.id());
        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.getFirst()).containsEntry("tipo", "EXPORTACAO");
        assertThat(instante(pedidos.getFirst().get("concluida_em"))).isEqualTo(relogio.instant());
        assertThat(linhasDeAuditoria()).isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("A37: sem token e com token vencido, GET /me/dados dá 401")
    void semLoginOu401() throws Exception {
        Cliente cliente = novoCliente(false);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/me/dados"))
                .andExpect(status().isUnauthorized());
        relogio.avancar(Duration.ofMinutes(16)); // o token de acesso vale 15 minutos
        get("/me/dados", cliente.acesso()).andExpect(status().isUnauthorized());
    }
}
