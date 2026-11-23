package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * O caminho feliz do convite do barbeiro (Fatia 10B): o dono gera, o barbeiro digita o próprio e-mail e cria a senha, e vê só a
 * própria agenda. Terça 01/12/2026, 09:30 (o convite vale 48 h, até 03/12 às 09:30). Contas e e-mails fictícios.
 */
class ConviteDoBarbeiroIT extends ConviteBase {

    private String corpoDeOutroBarbeiro(UUID servico, UUID barbeiro, String hora) {
        return "{ \"servicoId\": \"" + servico + "\", \"barbeiroId\": \"" + barbeiro + "\", \"inicio\": \"2026-12-01T" + hora
                + ":00-03:00\", \"nomeCliente\": \"Fulano de Tal\" }";
    }

    @Test
    @DisplayName("A13: o ADMIN gera o convite (sem e-mail): 201 com o código e o prazo, no-store; no banco só o SHA-256 (o código não está em coluna nenhuma) e nenhuma conta nasce ainda")
    void emitirConvite() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        Conta dono = dono();
        long contas = jdbc.queryForObject("SELECT count(*) FROM usuario", Long.class);

        String corpo = emitir(dono, barbeiro).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("^[A-Za-z0-9_-]{43}$")))
                .andExpect(jsonPath("$.expiraEm").value("2026-12-03T09:30:00-03:00"))
                .andReturn().getResponse().getContentAsString();

        String codigo = JsonPath.read(corpo, "$.codigo");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM convite_conta WHERE barbeiro_id = ?", String.class, barbeiro))
                .isEqualTo(CodigoDeConvite.hash(codigo)).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT convite_conta::text FROM convite_conta WHERE barbeiro_id = ?", String.class, barbeiro))
                .doesNotContain(codigo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Long.class)).isEqualTo(contas);
        assertThat(jdbc.queryForObject("SELECT criado_por FROM convite_conta WHERE barbeiro_id = ?", UUID.class, barbeiro))
                .isEqualTo(dono.id());
        assertThat(jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin WHERE acao = 'CONVITE_EMITIDO' AND recurso_id = ?",
                String.class, barbeiro)).singleElement().asString().doesNotContain(codigo);
    }

    @Test
    @DisplayName("A13: barbeiro inexistente dá 404 e barbeiro que já tem acesso ativo dá 409 USUARIO_JA_VINCULADO")
    void emitirParaQuemNaoPode() throws Exception {
        Conta dono = dono();
        UUID comAcesso = barbeiroSemConta();
        convidarEAceitar(dono, comAcesso);

        emitir(dono, UUID.randomUUID()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
        emitir(dono, comAcesso).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USUARIO_JA_VINCULADO"));
    }

    @Test
    @DisplayName("A14: o barbeiro aceita com o próprio e-mail e a senha: 204, conta ativa só com BARBEIRO e ligada ao barbeiro; entra com o e-mail e a senha e vê só a própria agenda; a auditoria tem o aceite sem dado pessoal")
    void aceitarEVerSoAPropriaAgenda() throws Exception {
        UUID servico = io.github.k3vinrich4rd.altashoras.DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = barbeiroSemConta();
        UUID outro = barbeiroSemConta();
        UUID minha = reserva(barbeiro, servico, as(9, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        UUID dele = reserva(outro, servico, as(10, 0), "CONFIRMADO", null, cliente("Ana Costa Teste", "11988880006"), null);
        Conta dono = dono();
        String codigo = gerarCodigo(dono, barbeiro);
        String email = emailDoBarbeiro();

        aceitar(codigo, email, SENHA).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));

        var conta = jdbc.queryForMap("SELECT id, ativo, senha_hash, nome FROM usuario WHERE email = ?", email);
        assertThat(conta.get("ativo")).isEqualTo(true);
        assertThat((String) conta.get("senha_hash")).isNotBlank().isNotEqualTo(SENHA);
        assertThat(conta.get("nome")).isEqualTo(jdbc.queryForObject("SELECT nome_exibicao FROM barbeiro WHERE id = ?", String.class, barbeiro));
        assertThat(jdbc.queryForList("SELECT papel FROM usuario_papel WHERE usuario_id = ?", String.class, conta.get("id")))
                .containsExactly("BARBEIRO");
        assertThat(usuarioDoBarbeiro(barbeiro)).isEqualTo(conta.get("id"));

        String agenda = get("/admin/agenda?data=2026-12-01", tokenDe(email)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat((List<String>) JsonPath.read(agenda, "$[*].id")).containsExactly(minha.toString()).doesNotContain(dele.toString());
        get("/admin/agenda?data=2026-12-01&barbeiroId=" + outro, tokenDe(email)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForList("SELECT ator_id FROM auditoria_admin WHERE acao = 'CONVITE_ACEITO' AND recurso_id = ?",
                UUID.class, barbeiro)).containsExactly((UUID) conta.get("id"));
        assertThat(jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin WHERE recurso_id = ?", String.class, barbeiro)
                .toString()).doesNotContain(email).doesNotContain(codigo);
    }

    @Test
    @DisplayName("A15: senha com 9 bytes, 73 bytes e 40 letras acentuadas (80 bytes) dá 400 no campo senha e NÃO consome o convite: o mesmo código ainda aceita uma senha boa")
    void senhaForaDaRegraNaoConsome() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono(), barbeiro);
        String email = emailDoBarbeiro();

        for (String ruim : List.of("123456789", "a".repeat(73), "é".repeat(40))) {
            aceitar(codigo, email, ruim).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value("senha"));
        }
        assertThat(jdbc.queryForObject("SELECT usado_em IS NULL AND tentativas = 0 FROM convite_conta WHERE barbeiro_id = ?",
                Boolean.class, barbeiro)).isTrue();

        aceitar(codigo, email, SENHA).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A15: sem senha, para e-mail que não tem conta, dá 400 no campo senha e o convite continua valendo")
    void semSenhaParaContaNova() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono(), barbeiro);

        aceitar(codigo, emailDoBarbeiro(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senha"));

        aceitar(codigo, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A18: reenviar revoga o convite antigo (o código velho dá 422 e o novo dá 204); DELETE revoga e é idempotente; DELETE sem convite dá 204; barbeiro inexistente dá 404")
    void reenviarERevogar() throws Exception {
        Conta dono = dono();
        UUID barbeiro = barbeiroSemConta();
        String antigo = gerarCodigo(dono, barbeiro);
        String novo = gerarCodigo(dono, barbeiro);

        assertThat(novo).isNotEqualTo(antigo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM convite_conta WHERE barbeiro_id = ? AND usado_em IS NULL AND revogado_em IS NULL",
                Long.class, barbeiro)).isEqualTo(1);
        aceitar(antigo, emailDoBarbeiro(), SENHA).andExpect(status().isUnprocessableEntity());
        aceitar(novo, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());

        UUID outro = barbeiroSemConta();
        String codigo = gerarCodigo(dono, outro);
        revogar(dono, outro).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        revogar(dono, outro).andExpect(status().isNoContent());
        revogar(dono, barbeiroSemConta()).andExpect(status().isNoContent());
        revogar(dono, UUID.randomUUID()).andExpect(status().isNotFound());
        aceitar(codigo, emailDoBarbeiro(), SENHA).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_admin WHERE recurso_id = ? ORDER BY ocorrido_em, acao", String.class, outro))
                .containsExactlyInAnyOrder("CONVITE_EMITIDO", "CONVITE_REVOGADO");
    }

    @Test
    @DisplayName("A25: gerar e revogar convite: BARBEIRO 403, CLIENTE 403, sem token 401 e ADMIN com a senha inicial ainda em uso 403 TROCA_DE_SENHA_OBRIGATORIA")
    void quemPodeGerarConvite() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        String rota = "/admin/barbeiros/" + barbeiro + "/convite";
        record Quem(String nome, String token, int status, String code) { }
        List<Quem> casos = List.of(
                new Quem("barbeiro", conta("BARBEIRO").token(), 403, "ACESSO_NEGADO"),
                new Quem("cliente", conta("CLIENTE").token(), 403, "ACESSO_NEGADO"),
                new Quem("sem token", null, 401, "NAO_AUTENTICADO"),
                new Quem("admin com senha inicial", conta(true, "ADMIN", "BARBEIRO").token(), 403, "TROCA_DE_SENHA_OBRIGATORIA"));
        for (Quem caso : casos) {
            for (var pedido : List.of(MockMvcRequestBuilders.post(rota), MockMvcRequestBuilders.delete(rota))) {
                if (caso.token() != null) {
                    pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + caso.token());
                }
                mvc.perform(pedido).andExpect(status().is(caso.status())).andExpect(jsonPath("$.code").value(caso.code()));
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM convite_conta WHERE barbeiro_id = ?", Long.class, barbeiro)).isZero();
    }

    @Test
    @DisplayName("A32: GET /admin/barbeiros mostra acesso SEM_CONTA, CONVITE_PENDENTE (com conviteExpiraEm), ATIVO e DESLIGADO, e nenhum e-mail no JSON")
    void estadoDoAcesso() throws Exception {
        Conta dono = dono();
        UUID semConta = barbeiroSemConta();
        UUID pendente = barbeiroSemConta();
        UUID ativo = barbeiroSemConta();
        UUID desligado = barbeiroSemConta();
        gerarCodigo(dono, pendente);
        String emailAtivo = convidarEAceitar(dono, ativo);
        String emailDesligado = convidarEAceitar(dono, desligado);
        desligar(dono, desligado).andExpect(status().isOk());

        String lista = get("/admin/barbeiros", dono).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(lista, "$[?(@.id=='" + semConta + "')].acesso")).containsExactly("SEM_CONTA");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.id=='" + pendente + "')].acesso")).containsExactly("CONVITE_PENDENTE");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.id=='" + pendente + "')].conviteExpiraEm"))
                .containsExactly("2026-12-03T09:30:00-03:00");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.id=='" + ativo + "')].acesso")).containsExactly("ATIVO");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.id=='" + desligado + "')].acesso")).containsExactly("DESLIGADO");
        assertThat((List<Object>) JsonPath.read(lista, "$[?(@.id=='" + semConta + "')].conviteExpiraEm")).containsExactly((Object) null);
        assertThat(lista).doesNotContain(emailAtivo).doesNotContain(emailDesligado).doesNotContain("@exemplo.test");
    }

    @Test
    @DisplayName("A41: o barbeiro convidado, logo depois de aceitar, faz reserva manual, conclui e cancela na PRÓPRIA agenda (cancelar é só do dono: 403) e recebe 404 nos mesmos casos na agenda de outro barbeiro, sem nada mudar")
    void convidadoNuncaMexeNaAgendaDeOutro() throws Exception {
        UUID servico = io.github.k3vinrich4rd.altashoras.DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta dono = dono();
        UUID convidado = barbeiroSemConta();
        UUID outro = barbeiroSemConta();
        UUID reservaDoOutro = reserva(outro, servico, as(10, 0), "CONFIRMADO", null, cliente("Ana Costa Teste", "11988880006"), null);
        UUID reservaPropria = reserva(convidado, servico, as(9, 0), "CONFIRMADO", null, cliente("Maria Souza Teste", "11988880001"), null);
        String token = tokenDe(convidarEAceitar(dono, convidado));
        long versao = versaoDa(reservaDoOutro);
        long auditoria = linhasDeAuditoria();

        // Na agenda de outro: 404 em tudo, e nada muda.
        mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content(corpoDeOutroBarbeiro(servico, outro, "16:00")))
                .andExpect(status().isNotFound());
        for (String corpo : List.of(corpoDeStatus("CONCLUIDO", versao), corpoDeStatus("NAO_COMPARECEU", versao),
                corpoDeCancelamento("Tentativa", versao))) {
            mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reservaDoOutro + "/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isNotFound());
        }
        assertThat(statusDaReserva(reservaDoOutro)).isEqualTo("CONFIRMADO");
        assertThat(versaoDa(reservaDoOutro)).isEqualTo(versao);
        assertThat(linhasDeAuditoria()).isEqualTo(auditoria);

        // Na própria: reserva manual e conclusão funcionam; cancelar é do dono (403, depois do teste de dono).
        mvc.perform(MockMvcRequestBuilders.post("/admin/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content(corpoDeOutroBarbeiro(servico, convidado, "16:00")))
                .andExpect(status().isCreated());
        mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reservaPropria + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCancelamento("Quero cancelar", versaoDa(reservaPropria))))
                .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reservaPropria + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeStatus("CONCLUIDO", versaoDa(reservaPropria))))
                .andExpect(status().isOk());
    }
}
