package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ip;
import static io.github.k3vinrich4rd.altashoras.ContaDeTeste.ipNovo;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;
import io.github.k3vinrich4rd.altashoras.cep.CepDeTeste;
import io.github.k3vinrich4rd.altashoras.cep.CepDeTeste.TickerFalso;
import io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos;

/**
 * Começo comum dos testes do alcance (Fatia 6C). Aproveita a massa do {@link PainelBase} (contas, barbeiros, reservas
 * inseridas no banco), mas com os provedores de CEP e o relógio do cache falsos (nenhuma chamada de rede real, A22) e o
 * relógio na terça 17/11/2026, 08:00, o mesmo das Fatias 4 e 5. Cada teste começa com as reservas vazias, a vitrine fora do
 * cache e nenhuma chamada de provedor esperada. Contas, bairros, e-mails e IPs são fictícios.
 */
@SpringBootTest(properties = "app.teste.contexto=alcance")
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, ContadorDeSql.class, CepDeTeste.class})
public abstract class AlcanceBase extends PainelBase {

    protected static final LocalDate DIA_DO_ATENDIMENTO = LocalDate.of(2026, 11, 10);
    protected static final LocalDate TERCA_DA_RESERVA = LocalDate.of(2026, 12, 1);

    @Autowired
    protected ProvedoresFalsos provedores;

    @Autowired
    protected TickerFalso ticker;

    @Autowired
    AlcancePublicoController vitrine;

    @Autowired
    protected CacheManager caches;

    private int proximoHorario;

    @BeforeEach
    protected void prepararAlcance() {
        provedores.reiniciar();
        ticker.reiniciar();
        caches.getCacheNames().forEach(nome -> caches.getCache(nome).clear()); // o cache do CEP de outro teste
        vitrine.esquecer();
        relogio.reiniciar(); // 17/11/2026 08:00: o PainelBase começa em 01/12
        proximoHorario = 0;
    }

    @AfterEach
    protected void conferirProvedores() {
        provedores.verificar();
        ticker.reiniciar();
        vitrine.esquecer();
    }

    protected long contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }

    // ---- massa ------------------------------------------------------------------------------------------------------

    /** Um barbeiro ativo e um serviço de 30 minutos, para as reservas feitas pela API. */
    protected record Catalogo(UUID barbeiro, UUID servico) {
    }

    protected Catalogo catalogo() {
        return new Catalogo(DadosDeTeste.barbeiro(jdbc, true), DadosDeTeste.servico(jdbc, 30));
    }

    // ---- onde moram as pessoas (a tabela do IBGE de verdade traz estes códigos) -------------------------------------------

    protected static final String OSASCO = "3534401";
    protected static final String CARAPICUIBA = "3510609";
    protected static final String SAO_PAULO = "3550308";
    protected static final String CAMPINAS = "3509502";
    protected static final String RIO = "3304557";

    /** Onde uma pessoa mora, do jeito que a reserva guarda (bairro, município IBGE, cidade e uf). */
    protected record Onde(String bairro, String ibge, String cidade, String uf) {
    }

    protected static final Onde CENTRO = new Onde("Centro", OSASCO, "Osasco", "SP");
    protected static final Onde ALTINO = new Onde("Presidente Altino", OSASCO, "Osasco", "SP");
    protected static final Onde CARAPICUIBA_CENTRO = new Onde("Centro", CARAPICUIBA, "Carapicuíba", "SP");
    protected static final Onde SE = new Onde("Sé", SAO_PAULO, "São Paulo", "SP");
    protected static final Onde CAMPINAS_CENTRO = new Onde("Centro", CAMPINAS, "Campinas", "SP");
    protected static final Onde COPACABANA = new Onde("Copacabana", RIO, "Rio de Janeiro", "RJ");

    private static final java.util.concurrent.atomic.AtomicLong CONTATOS = new java.util.concurrent.atomic.AtomicLong(1_000_000);

    /** O contato de um visitante: o que decide se duas reservas são da mesma pessoa (E4). */
    protected record Contato(String whatsapp, String email) {

        protected static Contato novo() {
            long n = CONTATOS.incrementAndGet();
            return new Contato("119" + String.format("%08d", n), "visitante." + n + "@exemplo.test");
        }
    }

    /** Um atendimento de uma PESSOA NOVA (contato único) num horário novo (30 minutos depois do anterior), na região dada. */
    protected UUID atendimento(UUID barbeiro, UUID servico, String status, Onde onde) {
        return atendimentoEm(barbeiro, servico, DIA_DO_ATENDIMENTO, status, onde);
    }

    protected UUID atendimentoEm(UUID barbeiro, UUID servico, LocalDate dia, String status, Onde onde) {
        return atendimentoDe(Contato.novo(), barbeiro, servico, dia, status, onde);
    }

    /** O atendimento de um visitante com o contato dado (a mesma pessoa pode ter várias reservas). {@code onde} nulo = sem região. */
    protected UUID atendimentoDe(Contato contato, UUID barbeiro, UUID servico, LocalDate dia, String status, Onde onde) {
        OffsetDateTime inicio = DadosDeTeste.em(dia, 7, 0).plusMinutes(30L * proximoHorario++);
        UUID id = reserva(barbeiro, servico, inicio, status, null, null, "Visitante Fictício");
        jdbc.update("UPDATE reserva_guest SET whatsapp = ?, email = ? WHERE reserva_id = ?", contato.whatsapp(), contato.email(), id);
        if (onde != null) {
            jdbc.update("""
                    UPDATE reserva SET regiao_bairro = ?, regiao_municipio_ibge = ?, regiao_cidade = ?, regiao_uf = ?,
                                       regiao_consentimento_versao = '1.2' WHERE id = ?
                    """, onde.bairro(), onde.ibge(), onde.cidade(), onde.uf(), id);
        }
        return id;
    }

    /** {@code quantas} PESSOAS DIFERENTES atendidas (CONCLUIDO) na mesma região, no dia padrão. */
    protected void pessoas(int quantas, UUID barbeiro, UUID servico, Onde onde) {
        pessoasEm(quantas, barbeiro, servico, DIA_DO_ATENDIMENTO, onde);
    }

    protected void pessoasEm(int quantas, UUID barbeiro, UUID servico, LocalDate dia, Onde onde) {
        for (int i = 0; i < quantas; i++) {
            atendimentoEm(barbeiro, servico, dia, "CONCLUIDO", onde);
        }
    }

    /** Uma conta de cliente que AUTORIZOU a região (a linha de consentimento aceita) e mora em {@code onde}. */
    protected UUID clienteComRegiao(Onde onde) {
        UUID id = cliente("Cliente Fictício", "119" + String.format("%08d", CONTATOS.incrementAndGet()));
        jdbc.update("""
                UPDATE usuario SET bairro = ?, regiao_municipio_ibge = ?, cidade = ?, uf = ? WHERE id = ?
                """, onde.bairro(), onde.ibge(), onde.cidade(), onde.uf(), id);
        jdbc.update("INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em) VALUES (?, 'REGIAO_ALCANCE', '1.2', true, timestamptz '2026-11-01 00:00+00')", id);
        return id;
    }

    /** O atendimento de uma conta, com a região copiada da conta (como a reserva real faz). */
    protected UUID atendimentoDaConta(UUID clienteId, UUID barbeiro, UUID servico, LocalDate dia, String status) {
        OffsetDateTime inicio = DadosDeTeste.em(dia, 7, 0).plusMinutes(30L * proximoHorario++);
        UUID id = reserva(barbeiro, servico, inicio, status, null, clienteId, null);
        jdbc.update("""
                UPDATE reserva r SET regiao_bairro = u.bairro, regiao_municipio_ibge = u.regiao_municipio_ibge,
                       regiao_cidade = u.cidade, regiao_uf = u.uf, regiao_consentimento_versao = '1.2'
                FROM usuario u WHERE u.id = ? AND r.id = ? AND u.cidade IS NOT NULL
                """, clienteId, id);
        return id;
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------------

    protected static String corpoGuest(Catalogo c, String hora, String cep, boolean autoriza) {
        String campoCep = cep == null ? "" : ", \"cep\": \"%s\"".formatted(cep);
        String consentimento = autoriza ? ", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.2\"" : "";
        return """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s",
                  "nome": "Visitante Teste", "email": "visitante@exemplo.test", "whatsapp": "(11) 90000-0002"%s%s }
                """.formatted(c.servico(), c.barbeiro(), DadosDeTeste.em(TERCA_DA_RESERVA, Integer.parseInt(hora.substring(0, 2)),
                Integer.parseInt(hora.substring(3))), campoCep, consentimento);
    }

    /** Reserva do visitante pela API, de um IP novo, SEM marcar a caixinha de região (o CEP, se vier, é ignorado). */
    protected ResultActions reservarComoVisitante(Catalogo c, String hora, String cep) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                .content(corpoGuest(c, hora, cep, false)).with(ip(ipNovo())));
    }

    /** Reserva do visitante marcando a caixinha de região com a versão vigente (1.2). */
    protected ResultActions reservarAutorizando(Catalogo c, String hora, String cep) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                .content(corpoGuest(c, hora, cep, true)).with(ip(ipNovo())));
    }

    protected String tokenDeCliente(UUID clienteId) {
        return emissor.emitir(clienteId, List.of("CLIENTE"), 0, false).valor();
    }

    protected ResultActions reservarComoCliente(Catalogo c, String hora, UUID clienteId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/agendamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente(clienteId))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s" }
                        """.formatted(c.servico(), c.barbeiro(), DadosDeTeste.em(TERCA_DA_RESERVA,
                        Integer.parseInt(hora.substring(0, 2)), Integer.parseInt(hora.substring(3))))));
    }

    // ---- contas e painel pela API -------------------------------------------------------------------------------------

    protected static final String CEP_OSASCO = io.github.k3vinrich4rd.altashoras.cep.ProvedoresFalsos.CEP_OSASCO;

    /** Cadastro de cliente pela API; {@code campos} são os campos extras do JSON (já com a vírgula na frente) ou vazio. */
    protected ResultActions cadastrar(String email, String campos) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "nome": "Cliente Teste", "email": "%s", "whatsapp": "(11) 90000-0001", "senha": "Senha-de-teste-123"%s }
                        """.formatted(email, campos)).with(ip(ipNovo())));
    }

    protected static final String REGIAO_OSASCO = ", \"cep\": \"06010-000\", \"autorizaRegiao\": true, \"versaoTextoRegiao\": \"1.2\"";

    protected ResultActions comoCliente(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido,
            UUID clienteId, String corpo) throws Exception {
        if (corpo != null) {
            pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente(clienteId)).with(ip(ipNovo())));
    }

    /** O token de uma conta de equipe (ADMIN ou BARBEIRO), emitido com o relógio atual. */
    protected String tokenDeEquipe(UUID contaId, String... papeis) {
        return emissor.emitir(contaId, List.of(papeis), 0, false).valor();
    }

    protected ResultActions alcanceDoAdmin(String consulta) throws Exception {
        Conta admin = conta("ADMIN", "BARBEIRO");
        return mvc.perform(MockMvcRequestBuilders.get("/admin/alcance" + consulta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).with(ip(ipNovo())));
    }

    protected ResultActions alcanceDoBarbeiro(Conta barbeiro, String consulta) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/admin/alcance" + consulta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + barbeiro.token()).with(ip(ipNovo())));
    }

    /** Lê um valor do corpo JSON (um caminho de JsonPath). */
    protected static <T> T ler(String corpo, String caminho) {
        return com.jayway.jsonpath.JsonPath.read(corpo, caminho);
    }

    /** O corpo da resposta do painel do ADMIN (todos os barbeiros), com o código 200 conferido. */
    protected String painel(String consulta) throws Exception {
        return alcanceDoAdmin(consulta).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
