package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ChavesDeTeste;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/** A24 a A33 da Fatia 13: a inscrição do aparelho, a chave pública e o aviso de teste. Só hosts de serviço de push; só a conta do token. */
class InscricaoDePushIT extends NotificacaoBase {

    private static final String ROTA = "/admin/notificacoes/inscricao";

    @Autowired
    Inscricoes inscricoes;

    @Autowired
    Despachante despachante;

    @Autowired
    LimitadorDeTentativas limitador;

    @Autowired
    PlatformTransactionManager gerenciador;

    private final ChavesDeTeste.Aparelho aparelho = ChavesDeTeste.aparelho();

    private String corpo(String endpoint) {
        return """
                { "endpoint": "%s", "chaves": { "p256dh": "%s", "auth": "%s" } }
                """.formatted(endpoint, aparelho.p256dh(), aparelho.auth());
    }

    private ResultActions inscrever(Conta quem, String corpo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions remover(Conta quem, String endpoint) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token())
                .contentType(MediaType.APPLICATION_JSON).content("{ \"endpoint\": \"" + endpoint + "\" }"));
    }

    private long linhas(String endpoint) {
        return jdbc.queryForObject("SELECT count(*) FROM inscricao_push WHERE endpoint = ?", Long.class, endpoint);
    }

    @Test
    @DisplayName("A24: endpoint de host fora da lista (exemplo.interno, http no host certo, IP, localhost, servidor local) é 400 VALIDACAO_INVALIDA e nenhuma chamada de rede chega ao host")
    void hostForaDaListaEhRecusado() throws Exception {
        AtomicInteger requisicoes = new AtomicInteger();
        HttpServer espiao = HttpServer.create(new InetSocketAddress(0), 0);
        espiao.createContext("/", troca -> {
            requisicoes.incrementAndGet();
            troca.sendResponseHeaders(201, -1);
            troca.close();
        });
        espiao.start();
        try {
            Conta barbeiro = conta("BARBEIRO");
            for (String endpoint : List.of("https://exemplo.interno/x", "http://fcm.googleapis.com/fcm/send/abc",
                    "https://10.0.0.1/x", "https://localhost/x", "https://[::1]/x", "https://fcm.googleapis.com.mal.exemplo/x",
                    "https://usuario@fcm.googleapis.com/x", "https://fcm.googleapis.com:8443/x",
                    "http://localhost:" + espiao.getAddress().getPort() + "/push")) {
                inscrever(barbeiro, corpo(endpoint)).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push", Long.class)).isZero();
            assertThat(requisicoes.get()).isZero();
        } finally {
            espiao.stop(0);
        }
    }

    @Test
    @DisplayName("A25: BARBEIRO e ADMIN autenticados inscrevem o aparelho com endpoint permitido e chaves válidas: 204 e uma linha de inscricao_push da conta do token")
    void inscreveNaContaDoToken() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        Conta admin = conta("ADMIN", "BARBEIRO");

        inscrever(barbeiro, corpo("https://fcm.googleapis.com/fcm/send/barbeiro")).andExpect(status().isNoContent());
        inscrever(admin, corpo("https://updates.push.services.mozilla.com/wpush/v2/admin")).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT usuario_id FROM inscricao_push WHERE endpoint = ?", UUID.class,
                "https://fcm.googleapis.com/fcm/send/barbeiro")).isEqualTo(barbeiro.id());
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM inscricao_push WHERE endpoint = ?", UUID.class,
                "https://updates.push.services.mozilla.com/wpush/v2/admin")).isEqualTo(admin.id());
        // O corpo não escolhe a conta: um usuarioId a mais é um campo desconhecido.
        inscrever(barbeiro, """
                { "usuarioId": "%s", "endpoint": "https://fcm.googleapis.com/fcm/send/x", "chaves": { "p256dh": "%s", "auth": "%s" } }
                """.formatted(admin.id(), aparelho.p256dh(), aparelho.auth())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPO_DESCONHECIDO"));
    }

    @Test
    @DisplayName("A26: a mesma inscrição enviada duas vezes: 204 nas duas e uma linha só")
    void inscricaoIdempotente() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        String endpoint = "https://fcm.googleapis.com/fcm/send/idempotente";

        inscrever(barbeiro, corpo(endpoint)).andExpect(status().isNoContent());
        inscrever(barbeiro, corpo(endpoint)).andExpect(status().isNoContent());

        assertThat(linhas(endpoint)).isEqualTo(1);
    }

    @Test
    @DisplayName("A27: endpoint da conta A inscrito pela conta B (o mesmo aparelho, outro login): passa a ser de B e uma confirmação de reserva de A não é mais enviada a ele")
    void aparelhoPassaParaAContaNova() throws Exception {
        Barbeiro a = barbeiroComConta("BARBEIRO");
        Conta b = conta("BARBEIRO");
        String endpoint = "https://fcm.googleapis.com/fcm/send/aparelho-compartilhado";
        inscrever(a.conta(), corpo(endpoint)).andExpect(status().isNoContent());

        inscrever(b, corpo(endpoint)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT usuario_id FROM inscricao_push WHERE endpoint = ?", UUID.class, endpoint))
                .isEqualTo(b.id());
        UUID reserva = reservaAguardando(a.id(), servicoDeTeste("Corte Clássico"), as(10, 0));
        reservas.confirmarPagamento(reserva);
        esperarOutboxVazio();
        assertThat(avisosDa(a.conta().id())).isEqualTo(1);
        assertThat(falsoWebPush.envios()).isEmpty();
    }

    @Test
    @DisplayName("A28: corpo sem chaves, p256dh ou auth vazios ou gigantes, chave que não decodifica e campo desconhecido: 400 VALIDACAO_INVALIDA ou CAMPO_DESCONHECIDO, sem ecoar os valores")
    void corpoInvalido() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        String marcador = "MARCADOR-SECRETO-QUE-NAO-PODE-VOLTAR";
        String endpoint = "https://fcm.googleapis.com/fcm/send/invalido";

        List<String> corpos = List.of(
                "{ \"endpoint\": \"" + endpoint + "\" }",
                "{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"\", \"auth\": \"" + marcador + "\" } }",
                "{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"" + marcador + "\", \"auth\": \"" + aparelho.auth() + "\" } }",
                "{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"" + "A".repeat(300) + "\", \"auth\": \"" + marcador + "\" } }",
                "{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"" + aparelho.p256dh() + "\", \"auth\": \"" + marcador + "\" } }",
                "{ \"endpoint\": \"" + endpoint + "\", \"chaves\": { \"p256dh\": \"" + aparelho.p256dh() + "\", \"auth\": \"" + aparelho.auth()
                        + "\", \"extra\": \"" + marcador + "\" } }",
                "{ \"endpoint\": \"" + "h".repeat(700) + "\", \"chaves\": { \"p256dh\": \"" + aparelho.p256dh() + "\", \"auth\": \"" + aparelho.auth() + "\" } }");
        for (String corpo : corpos) {
            String resposta = inscrever(barbeiro, corpo).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(resposta).doesNotContain(marcador).doesNotContain("AAAAAAAAAA").doesNotContain("hhhhhhhhhh")
                    .doesNotContain(aparelho.p256dh()).doesNotContain(aparelho.auth());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push", Long.class)).isZero();
    }

    @Test
    @DisplayName("A29: conta com 10 aparelhos: o 11º se inscreve com 204 e o mais antigo é substituído (continuam 10)")
    void limiteDeDezAparelhos() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        for (int i = 0; i < 10; i++) {
            inscrever(barbeiro, corpo("https://fcm.googleapis.com/fcm/send/aparelho-" + i)).andExpect(status().isNoContent());
            jdbc.update("UPDATE inscricao_push SET criada_em = now() - (? || ' hours')::interval WHERE endpoint = ?", 100 - i,
                    "https://fcm.googleapis.com/fcm/send/aparelho-" + i);
        }

        inscrever(barbeiro, corpo("https://fcm.googleapis.com/fcm/send/aparelho-novo")).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push WHERE usuario_id = ?", Long.class, barbeiro.id()))
                .isEqualTo(10);
        assertThat(linhas("https://fcm.googleapis.com/fcm/send/aparelho-0")).isZero();
        assertThat(linhas("https://fcm.googleapis.com/fcm/send/aparelho-novo")).isEqualTo(1);
    }

    @Test
    @DisplayName("A30: DELETE da própria inscrição: 204 e a linha some; com o endpoint de outra conta ou inexistente: 204 igual e nada é apagado")
    void removerInscricao() throws Exception {
        Conta a = conta("BARBEIRO");
        Conta b = conta("BARBEIRO");
        String deA = "https://fcm.googleapis.com/fcm/send/de-a";
        String deB = "https://fcm.googleapis.com/fcm/send/de-b";
        inscrever(a, corpo(deA)).andExpect(status().isNoContent());
        inscrever(b, corpo(deB)).andExpect(status().isNoContent());

        remover(a, deB).andExpect(status().isNoContent());
        remover(a, "https://fcm.googleapis.com/fcm/send/nao-existe").andExpect(status().isNoContent());
        assertThat(linhas(deB)).isEqualTo(1);
        assertThat(linhas(deA)).isEqualTo(1);

        remover(a, deA).andExpect(status().isNoContent());
        assertThat(linhas(deA)).isZero();
        assertThat(linhas(deB)).isEqualTo(1);
    }

    @Test
    @DisplayName("A31: CLIENTE logado recebe 403 ACESSO_NEGADO e quem está sem token recebe 401, em cada rota de /admin/notificacoes, sem corpo de dado")
    void clienteESemTokenNaoPassam() throws Exception {
        Conta cliente = conta("CLIENTE");
        record Rota(String metodo, String caminho, String corpo) {
        }
        List<Rota> rotas = List.of(new Rota("GET", "/admin/notificacoes/chave-publica", null),
                new Rota("POST", ROTA, corpo("https://fcm.googleapis.com/fcm/send/x")),
                new Rota("DELETE", ROTA, "{ \"endpoint\": \"https://fcm.googleapis.com/fcm/send/x\" }"),
                new Rota("GET", "/admin/notificacoes", null), new Rota("POST", "/admin/notificacoes/marcar-lidas", null),
                new Rota("POST", "/admin/notificacoes/teste", null));
        for (Rota rota : rotas) {
            var pedido = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(rota.metodo()), rota.caminho());
            if (rota.corpo() != null) {
                pedido.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
            }
            String negado = mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO")).andReturn().getResponse()
                    .getContentAsString();
            assertThat(negado).doesNotContain("naoLidas").doesNotContain("chavePublica");
            var semToken = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(rota.metodo()), rota.caminho());
            if (rota.corpo() != null) {
                semToken.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
            }
            mvc.perform(semToken).andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inscricao_push", Long.class)).isZero();
    }

    @Test
    @DisplayName("A32: com VAPID configurada a chave pública sai com disponivel=true; sem VAPID sai só disponivel=false; a chave privada nunca aparece em resposta nem em toString")
    void chavePublica() throws Exception {
        Conta barbeiro = conta("BARBEIRO");

        String semVapid = get("/admin/notificacoes/chave-publica", barbeiro).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(false)).andExpect(jsonPath("$.chavePublica").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(semVapid).doesNotContain("rivada");

        ChavesDeTeste.Par par = ChavesDeTeste.par();
        var parametros = new ParametrosDeNotificacao("webpush", par.publica(), par.privada(), "https://site.exemplo.test",
                "fcm.googleapis.com", false, 30, 90, 5, java.time.Duration.ofHours(1));
        var comVapid = new AparelhosDoBarbeiro(inscricoes, despachante, parametros, limitador, Clock.systemUTC(), gerenciador);

        assertThat(comVapid.chavePublica().disponivel()).isTrue();
        assertThat(comVapid.chavePublica().chavePublica()).isEqualTo(par.publica());
        assertThat(comVapid.chavePublica().toString()).doesNotContain(par.privada());
        assertThat(parametros.toString()).doesNotContain(par.privada()).doesNotContain(par.publica());
        // Webpush desligado em canais: indisponível mesmo com as chaves.
        var desligado = new ParametrosDeNotificacao("", par.publica(), par.privada(), "https://site.exemplo.test",
                "fcm.googleapis.com", false, 30, 90, 5, java.time.Duration.ofHours(1));
        assertThat(new AparelhosDoBarbeiro(inscricoes, despachante, desligado, limitador, Clock.systemUTC(), gerenciador)
                .chavePublica().disponivel()).isFalse();
    }

    @Test
    @DisplayName("A33: POST /admin/notificacoes/teste: 204 e o canal recebe 'Notificações ativadas neste aparelho' só para os aparelhos da conta; o 6º em uma hora é 429 LIMITE_EXCEDIDO")
    void envioDeTeste() throws Exception {
        Conta barbeiro = conta("BARBEIRO");
        Conta outra = conta("BARBEIRO");
        inscrever(barbeiro, corpo("https://fcm.googleapis.com/fcm/send/meu-1")).andExpect(status().isNoContent());
        inscrever(barbeiro, corpo("https://fcm.googleapis.com/fcm/send/meu-2")).andExpect(status().isNoContent());
        inscrever(outra, corpo("https://fcm.googleapis.com/fcm/send/alheio")).andExpect(status().isNoContent());

        post("/admin/notificacoes/teste", barbeiro).andExpect(status().isNoContent());

        assertThat(falsoWebPush.envios()).hasSize(2).allSatisfy(e -> {
            assertThat(e.aviso().texto()).isEqualTo("Notificações ativadas neste aparelho");
            assertThat(e.destino().usuarioId()).isEqualTo(barbeiro.id());
            assertThat(e.emTransacao()).isFalse();
        });
        for (int i = 0; i < 4; i++) {
            post("/admin/notificacoes/teste", barbeiro).andExpect(status().isNoContent());
        }
        post("/admin/notificacoes/teste", barbeiro).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMITE_EXCEDIDO"));
    }
}
