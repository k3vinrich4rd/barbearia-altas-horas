package io.github.k3vinrich4rd.altashoras.conta.convite;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.SENHA;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ipNovo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A16 a A22 da Fatia 10B: o convite não vira oráculo e não se adivinha. Tudo o que não é um aceite certo é o MESMO 422, o uso
 * é único mesmo com dois pedidos juntos, o prazo e os limites valem com o relógio e o corpo do erro nunca confirma nada.
 */
class ConviteSegurancaIT extends ConviteBase {

    private static final String SEM_TRACE = "\"traceId\":\"[0-9a-f]+\",?";

    private String emailDe(Conta conta) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, conta.id());
    }

    private String corpoDoErro(org.springframework.test.web.servlet.ResultActions resposta) throws Exception {
        return resposta.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CONVITE_INVALIDO"))
                .andReturn().getResponse().getContentAsString().replaceAll(SEM_TRACE, "");
    }

    @Test
    @DisplayName("A16: código já usado, vencido (+48 h), revogado, inventado, e-mail de conta de equipe e conta de cliente sem login dão 422 CONVITE_INVALIDO com o MESMO corpo, título e mensagem (byte a byte)")
    void seisCasosUmaSoResposta() throws Exception {
        Conta dono = dono();
        Conta equipe = conta("BARBEIRO");
        String cliente = clienteCadastrado();

        UUID usado = barbeiroSemConta();
        String codigoUsado = gerarCodigo(dono, usado);
        aceitar(codigoUsado, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());
        UUID revogado = barbeiroSemConta();
        String codigoRevogado = gerarCodigo(dono, revogado);
        revogar(dono, revogado).andExpect(status().isNoContent());
        String codigoVencido = gerarCodigo(dono, barbeiroSemConta());
        relogio.avancar(Duration.ofHours(48));

        List<String> corpos = new ArrayList<>();
        corpos.add(corpoDoErro(aceitar(codigoUsado, emailDoBarbeiro(), SENHA)));
        corpos.add(corpoDoErro(aceitar(codigoVencido, emailDoBarbeiro(), SENHA)));
        corpos.add(corpoDoErro(aceitar(codigoRevogado, emailDoBarbeiro(), SENHA)));
        corpos.add(corpoDoErro(aceitar(CodigoDeConvite.gerar(), emailDoBarbeiro(), SENHA)));
        // Os dois últimos têm convite que ainda vale, mas o e-mail não serve: o relógio já passou das 48 h dos outros, então
        // eles usam um convite novo, gerado agora.
        Conta donoDeNovo = comTokenNovo(dono, "ADMIN", "BARBEIRO");
        String paraEquipe = gerarCodigo(donoDeNovo, barbeiroSemConta());
        String paraCliente = gerarCodigo(donoDeNovo, barbeiroSemConta());
        corpos.add(corpoDoErro(aceitar(paraEquipe, emailDe(equipe), SENHA)));
        corpos.add(corpoDoErro(aceitar(paraCliente, cliente, SENHA)));

        assertThat(corpos).hasSize(6).doesNotContain("").containsOnly(corpos.get(0));
        assertThat(corpos.get(0)).doesNotContain(codigoUsado).doesNotContain(cliente);
    }

    @Test
    @DisplayName("A17: dois aceites ao mesmo tempo com o mesmo código: exatamente um 204 e um 422, e só uma conta é criada")
    void doisAceitesAoMesmoTempo() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono(), barbeiro);
        String email1 = emailDoBarbeiro();
        String email2 = emailDoBarbeiro();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (String email : List.of(email1, email2)) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return aceitar(codigo, email, SENHA).andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            codigos.add(f.get());
        }
        pool.shutdown();

        assertThat(codigos).containsExactlyInAnyOrder(204, 422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email IN (?, ?)", Long.class, email1, email2)).isEqualTo(1);
        assertThat(usuarioDoBarbeiro(barbeiro)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM convite_conta WHERE barbeiro_id = ? AND usado_em IS NOT NULL", Long.class,
                barbeiro)).isEqualTo(1);
    }

    @Test
    @DisplayName("A19: aceitar em +47h59m59s dá 204 e em +48h dá 422 (o relógio é o do Clock, e o instante exato do vencimento já não vale)")
    void validadeDe48Horas() throws Exception {
        Conta dono = dono();
        String dentro = gerarCodigo(dono, barbeiroSemConta());
        String fora = gerarCodigo(dono, barbeiroSemConta());

        relogio.avancar(Duration.ofHours(48).minusSeconds(1));
        aceitar(dentro, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());
        relogio.avancar(Duration.ofSeconds(1));
        aceitar(fora, emailDoBarbeiro(), SENHA).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("A20: com o limite de 10 aceites por hora por IP, do 11º pedido inválido em diante dá 429 LIMITE_EXCEDIDO com Retry-After; outro IP não é afetado")
    void limitePorIp() throws Exception {
        String ipDoAtacante = ipNovo();
        for (int i = 1; i <= 10; i++) {
            mvc.perform(MockMvcRequestBuilders.post("/auth/convite").contentType(MediaType.APPLICATION_JSON).with(ip(ipDoAtacante))
                    .content(corpoDeAceite(CodigoDeConvite.gerar(), emailDoBarbeiro(), SENHA))).andExpect(status().isUnprocessableEntity());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(MockMvcRequestBuilders.post("/auth/convite").contentType(MediaType.APPLICATION_JSON).with(ip(ipDoAtacante))
                            .content(corpoDeAceite(CodigoDeConvite.gerar(), emailDoBarbeiro(), SENHA)))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"))
                    .andExpect(header().exists("Retry-After"));
        }
        // O código certo também fica de fora enquanto o IP está bloqueado; outro IP segue normal.
        String certo = gerarCodigo(dono(), barbeiroSemConta());
        mvc.perform(MockMvcRequestBuilders.post("/auth/convite").contentType(MediaType.APPLICATION_JSON).with(ip(ipDoAtacante))
                .content(corpoDeAceite(certo, emailDoBarbeiro(), SENHA))).andExpect(status().isTooManyRequests());
        aceitar(certo, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A21: 5 aceites com o código certo e e-mail de conta que não serve dão 422 iguais; na 5ª o convite é revogado (acesso SEM_CONTA, auditoria CONVITE_REVOGADO_POR_TENTATIVAS) e o código certo também dá 422")
    void cincoTentativasRevogam() throws Exception {
        Conta dono = dono();
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono, barbeiro);
        String cliente = clienteCadastrado();

        List<String> corpos = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            corpos.add(corpoDoErro(aceitar(codigo, cliente, SENHA)));
            assertThat(jdbc.queryForObject("SELECT tentativas FROM convite_conta WHERE barbeiro_id = ?", Integer.class, barbeiro)).isEqualTo(i);
            assertThat(jdbc.queryForObject("SELECT revogado_em IS NULL FROM convite_conta WHERE barbeiro_id = ?", Boolean.class, barbeiro)).isTrue();
        }
        corpos.add(corpoDoErro(aceitar(codigo, cliente, SENHA)));

        assertThat(corpos).containsOnly(corpos.get(0));
        assertThat(jdbc.queryForObject("SELECT revogado_em IS NOT NULL FROM convite_conta WHERE barbeiro_id = ?", Boolean.class, barbeiro)).isTrue();
        assertThat(jdbc.queryForList("SELECT acao FROM auditoria_admin WHERE recurso_id = ?", String.class, barbeiro))
                .contains("CONVITE_REVOGADO_POR_TENTATIVAS");
        assertThat(jdbc.queryForList("SELECT ator_id FROM auditoria_admin WHERE acao = 'CONVITE_REVOGADO_POR_TENTATIVAS'", UUID.class))
                .containsExactly(dono.id());
        get("/admin/barbeiros", dono).andExpect(jsonPath("$[?(@.id=='" + barbeiro + "')].acesso").value("SEM_CONTA"));
        corpoDoErro(aceitar(codigo, emailDoBarbeiro(), SENHA));
        assertThat(usuarioDoBarbeiro(barbeiro)).isNull();
    }

    @Test
    @DisplayName("A21: erro de senha fraca (400) não soma tentativa, e só a falha com o código certo conta")
    void senhaFracaNaoContaTentativa() throws Exception {
        UUID barbeiro = barbeiroSemConta();
        String codigo = gerarCodigo(dono(), barbeiro);

        for (int i = 0; i < 6; i++) {
            aceitar(codigo, emailDoBarbeiro(), "curta").andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT tentativas FROM convite_conta WHERE barbeiro_id = ?", Integer.class, barbeiro)).isZero();
        aceitar(codigo, emailDoBarbeiro(), SENHA).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("A22: o convite não guarda nem devolve e-mail: a tabela não tem coluna de e-mail, a emissão tem sempre a mesma forma e nenhuma lista, auditoria ou erro traz o e-mail do barbeiro")
    void nenhumaListaDevolveEmail() throws Exception {
        Conta dono = dono();
        UUID a = barbeiroSemConta();
        UUID b = barbeiroSemConta();
        String corpoA = emitir(dono, a).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String corpoB = emitir(dono, b).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String email = emailDoBarbeiro();
        aceitar(com.jayway.jsonpath.JsonPath.read(corpoA, "$.codigo"), email, SENHA).andExpect(status().isNoContent());

        assertThat(com.jayway.jsonpath.JsonPath.<java.util.Map<String, Object>>read(corpoA, "$").keySet())
                .containsExactlyInAnyOrder("codigo", "expiraEm")
                .isEqualTo(com.jayway.jsonpath.JsonPath.<java.util.Map<String, Object>>read(corpoB, "$").keySet());
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'convite_conta'", String.class))
                .noneMatch(coluna -> coluna.contains("mail"));
        assertThat(get("/admin/barbeiros", dono).andReturn().getResponse().getContentAsString()).doesNotContain(email);
        assertThat(jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin", String.class).toString()).doesNotContain(email);
        assertThat(corpoDoErro(aceitar(com.jayway.jsonpath.JsonPath.read(corpoA, "$.codigo"), email, SENHA))).doesNotContain(email);
    }
}
