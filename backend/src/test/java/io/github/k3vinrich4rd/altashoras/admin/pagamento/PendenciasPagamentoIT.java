package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/** A25 a A27: a lista de pendências de pagamento do painel (só ADMIN). */
class PendenciasPagamentoIT extends PainelBase {

    private record Cobrancas(Conta admin, UUID paga, UUID devolucaoPendente, UUID tardia, UUID cancelada) {
    }

    /** Massa da spec: PAGA (08:00), DEVOLUCAO_PENDENTE (10:00), PAGA_APOS_EXPIRACAO (09:00) e CANCELADA. */
    private Cobrancas massaDePagamentos() {
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID cliente = cliente("Maria Souza Teste", "11988880001");
        UUID r1 = reserva(barbeiro, servico, as(12, 0), "CONFIRMADO", null, cliente, null);
        UUID r2 = reserva(barbeiro, servico, as(13, 0), "CANCELADO", null, cliente, null);
        UUID r3 = reserva(barbeiro, servico, as(14, 0), "CANCELADO", null, null, "Visitante Tardio");
        UUID r4 = reserva(barbeiro, servico, as(15, 0), "EXPIRADO", null, cliente, null);
        return new Cobrancas(conta("ADMIN", "BARBEIRO"), cobranca(r1, "PAGA", as(8, 0)),
                cobranca(r2, "DEVOLUCAO_PENDENTE", as(10, 0)), cobranca(r3, "PAGA_APOS_EXPIRACAO", as(9, 0)),
                cobranca(r4, "CANCELADA", as(7, 0)));
    }

    @Test
    @DisplayName("A25: sem status, a lista traz só as pendências, a mais antiga primeiro, com valor, reserva e contato do pagador e sem Pix, cartão, CPF ou e-mail")
    void pendencias() throws Exception {
        Cobrancas c = massaDePagamentos();

        String corpo = get("/admin/pagamentos", c.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].id").value(c.tardia().toString()))
                .andExpect(jsonPath("$.itens[0].status").value("PAGA_APOS_EXPIRACAO"))
                .andExpect(jsonPath("$.itens[0].valorCentavos").value(4500))
                .andExpect(jsonPath("$.itens[0].reserva.inicio").value("2026-12-01T14:00:00-03:00"))
                .andExpect(jsonPath("$.itens[0].reserva.servico").value(startsWith("Corte Clássico")))
                .andExpect(jsonPath("$.itens[0].reserva.statusReserva").value("CANCELADO"))
                .andExpect(jsonPath("$.itens[0].contato.nome").value("Visitante Tardio"))
                .andExpect(jsonPath("$.itens[0].contato.whatsapp").value("11988880002"))
                .andExpect(jsonPath("$.itens[1].id").value(c.devolucaoPendente().toString()))
                .andExpect(jsonPath("$.itens[1].contato.nome").value("Maria Souza Teste"))
                .andExpect(jsonPath("$.itens[1].contato.whatsapp").value("11988880001"))
                .andExpect(jsonPath("$.itens[0].pix").doesNotExist())
                .andExpect(jsonPath("$.itens[0].cartao").doesNotExist())
                .andExpect(jsonPath("$.itens[0].cpf").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("@").doesNotContainIgnoringCase("cpf").doesNotContainIgnoringCase("copiaECola")
                .doesNotContainIgnoringCase("urlCheckout").doesNotContainIgnoringCase("email");
    }

    @Test
    @DisplayName("A26: o filtro por status mostra só aquele status (mais recentes primeiro); status desconhecido, tamanho 51 e página -1 dão 400")
    void filtros() throws Exception {
        Cobrancas c = massaDePagamentos();
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        UUID outraPaga = cobranca(reserva(barbeiro, servico, as(16, 0), "CONFIRMADO", null, cliente("Ana Costa Teste", "11988880006"), null),
                "PAGA", as(8, 30));

        String pagas = get("/admin/pagamentos?status=PAGA", c.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(pagas, "$.itens[*].id"))
                .containsExactly(outraPaga.toString(), c.paga().toString());
        get("/admin/pagamentos?status=CANCELADA", c.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].id").value(c.cancelada().toString()));

        for (String rota : List.of("/admin/pagamentos?status=XYZ", "/admin/pagamentos?tamanho=51",
                "/admin/pagamentos?pagina=-1")) {
            get(rota, c.admin()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }
    }

    @Test
    @DisplayName("A27: o número de consultas SQL da lista é o mesmo com 2 e com 20 pendências (lista, contagem e resumos em lote)")
    void semNMaisUm() throws Exception {
        Cobrancas c = massaDePagamentos();
        get("/admin/pagamentos", c.admin()).andExpect(status().isOk());

        long antes = ContadorDeSql.total();
        get("/admin/pagamentos?tamanho=50", c.admin()).andExpect(jsonPath("$.itens.length()").value(2));
        long comDuas = ContadorDeSql.total() - antes;

        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);
        for (int i = 0; i < 18; i++) {
            UUID reserva = reserva(barbeiro, servico, as(0, 0).plusMinutes(30L * i), "CANCELADO", null,
                    i % 2 == 0 ? cliente("Cliente " + i, "11988880003") : null, "Visitante " + i);
            cobranca(reserva, i % 2 == 0 ? "DEVOLUCAO_PENDENTE" : "PAGA_APOS_EXPIRACAO", as(11, 0));
        }
        antes = ContadorDeSql.total();
        get("/admin/pagamentos?tamanho=50", c.admin()).andExpect(jsonPath("$.itens.length()").value(20));
        long comVinte = ContadorDeSql.total() - antes;

        // 3 consultas da lista + 1 da conferência da conta do token (Fatia 6B, E16: a conta excluída não pode usar o token ainda válido).
        assertThat(comVinte).isEqualTo(comDuas).isEqualTo(4);
    }
}
