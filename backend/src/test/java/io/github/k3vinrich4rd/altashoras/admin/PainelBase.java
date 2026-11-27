package io.github.k3vinrich4rd.altashoras.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste.RelogioAjustavel;
import io.github.k3vinrich4rd.altashoras.plataforma.EmissorDeToken;

/**
 * Base dos testes do painel (Fatia 5A). Todos dividem um contexto só, com um PostgreSQL só (a propriedade
 * {@code app.teste.contexto=painel} separa este contexto dos demais). Como a agenda do painel lista reservas de todos
 * os barbeiros, cada teste começa com as tabelas de reserva, pagamento e auditoria vazias. Os testes de agenda e
 * fechamento rodam na terça 01/12/2026 às 09:30; quem precisa de outra hora chama {@link #irPara}. Os tokens são
 * emitidos pelo próprio {@link EmissorDeToken}, com o relógio do teste: quem muda o relógio depois continua válido
 * por 15 minutos. Nomes, contas e WhatsApp são fictícios.
 */
@SpringBootTest(properties = "app.teste.contexto=painel")
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class, ContadorDeSql.class})
public abstract class PainelBase {

    protected static final LocalDate TERCA = LocalDate.of(2026, 12, 1);

    /** Uma conta de teste já com o token de acesso (papéis no claim, como o login real). */
    protected record Conta(UUID id, String token) {
    }

    /** A massa dos critérios de agenda (seção 4 da spec). Barbeiro A = o Eduardo; barbeiro B tem conta BARBEIRO própria. */
    protected record Massa(UUID servico, UUID barbeiroA, UUID barbeiroB, Conta contaA, Conta contaB, Conta admin,
            UUID r1, UUID r2, UUID r3, UUID r4, UUID r5, UUID r6, UUID clienteDaR1) {
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelogioAjustavel relogio;

    @Autowired
    protected EmissorDeToken emissor;

    @BeforeEach
    protected void prepararPainel() {
        jdbc.update("DELETE FROM auditoria_admin");
        jdbc.update("DELETE FROM cobranca");
        jdbc.update("DELETE FROM evento_webhook");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM event_publication");
        irPara(TERCA, 9, 30);
    }

    @AfterEach
    protected void relogioNoLugar() {
        // Falha injetada que sobrou (a criação ou o teste quebrou no meio) não pode vazar para o próximo teste.
        jdbc.execute("DROP FUNCTION IF EXISTS falha_do_teste() CASCADE");
        relogio.reiniciar();
        // O ThreadLocal do contador de SQL não pode ficar para o próximo teste desta mesma thread.
        ContadorDeSql.limpar();
    }

    // ---- relógio ----------------------------------------------------------------------------------------------------

    protected void irPara(LocalDate dia, int hora, int minuto) {
        relogio.reiniciar();
        relogio.avancar(Duration.between(RelogioFixoDeTeste.AGORA, DadosDeTeste.em(dia, hora, minuto).toInstant()));
    }

    protected static OffsetDateTime as(int hora, int minuto) {
        return DadosDeTeste.em(TERCA, hora, minuto);
    }

    // ---- contas -----------------------------------------------------------------------------------------------------

    protected Conta conta(String... papeis) {
        return conta(false, papeis);
    }

    protected Conta conta(boolean deveTrocarSenha, String... papeis) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nome, email, whatsapp, deve_trocar_senha) VALUES (?, ?, ?, '11900000009', ?)",
                id, "Equipe Teste", "equipe." + id + "@exemplo.test", deveTrocarSenha);
        for (String papel : papeis) {
            jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, ?)", id, papel);
        }
        return new Conta(id, emissor.emitir(id, List.of(papeis), 0, deveTrocarSenha).valor());
    }

    /** A mesma conta com um token novo, emitido no instante atual do relógio (depois de {@link #irPara}). */
    protected Conta comTokenNovo(Conta conta, String... papeis) {
        return new Conta(conta.id(), emissor.emitir(conta.id(), List.of(papeis), 0, false).valor());
    }

    protected UUID cliente(String nome, String whatsapp) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, nome, email, whatsapp) VALUES (?, ?, ?, ?)", id, nome,
                "cliente." + id + "@exemplo.test", whatsapp);
        jdbc.update("INSERT INTO usuario_papel (usuario_id, papel) VALUES (?, 'CLIENTE')", id);
        return id;
    }

    protected UUID barbeiroDaConta(UUID usuarioId) {
        UUID id = DadosDeTeste.barbeiro(jdbc, true);
        jdbc.update("UPDATE barbeiro SET usuario_id = ? WHERE id = ?", usuarioId, id);
        return id;
    }

    // ---- reservas e cobranças ---------------------------------------------------------------------------------------

    /**
     * Reserva de 30 min de R$ 45,00. Com {@code clienteId} é do cliente logado; sem, é do visitante (com nome e
     * WhatsApp na tabela do contato). Os status pagos levam o aceite da política, como exige a V1.
     */
    protected UUID reserva(UUID barbeiro, UUID servico, OffsetDateTime inicio, String status, Instant expiraEm,
            UUID clienteId, String nomeDoVisitante) {
        UUID id = UUID.randomUUID();
        boolean aguardando = "AGUARDANDO_PAGAMENTO".equals(status);
        boolean pago = List.of("CONFIRMADO", "CONCLUIDO", "NAO_COMPARECEU").contains(status);
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, cliente_id, origem, inicio, fim, fim_ocupacao, status,
                                     preco_centavos, expira_em, aceite_politica_versao, aceite_politica_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 4500, ?, ?, ?)
                """, id, barbeiro, servico, clienteId, clienteId == null ? "GUEST" : "CLIENTE", inicio,
                inicio.plusMinutes(30), inicio.plusMinutes(30), status,
                aguardando ? OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC) : null, pago ? 1 : null,
                pago ? inicio.minusDays(1) : null);
        if (clienteId == null) {
            jdbc.update("""
                    INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
                    VALUES (?, ?, ?, '11988880002', ?)
                    """, id, nomeDoVisitante, "visitante." + id + "@exemplo.test", hashFalso());
        }
        return id;
    }

    protected UUID cobranca(UUID reservaId, String status, OffsetDateTime criadaEm) {
        return jdbc.queryForObject("""
                INSERT INTO cobranca (reserva_id, provedor, metodo, id_cobranca_provedor, valor_centavos, status,
                                      chave_idempotencia_hash, hash_corpo, criado_em, atualizado_em)
                VALUES (?, 'SIMULADO', 'PIX', ?, 4500, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, reservaId, "sim_" + UUID.randomUUID(), status, hashFalso(), hashFalso(), criadaEm,
                criadaEm);
    }

    /** Hash de 64 caracteres, único: serve para as colunas char(64) que a V1 exige. */
    protected static String hashFalso() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    /** A massa da seção 4: R1 a R5 do barbeiro A, R6 do barbeiro B; só R1 tem cobrança PAGA. */
    protected Massa massa() {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        Conta contaA = conta("BARBEIRO");
        Conta contaB = conta("BARBEIRO");
        Conta admin = conta("ADMIN", "BARBEIRO");
        UUID barbeiroA = barbeiroDaConta(contaA.id());
        UUID barbeiroB = barbeiroDaConta(contaB.id());
        UUID clienteDaR1 = cliente("Maria Souza Teste", "11988880001");
        UUID r1 = reserva(barbeiroA, servico, as(9, 0), "CONFIRMADO", null, clienteDaR1, null);
        cobranca(r1, "PAGA", as(8, 0));
        UUID r2 = reserva(barbeiroA, servico, as(10, 0), "AGUARDANDO_PAGAMENTO", as(9, 40).toInstant(), null,
                "Visitante Dois");
        UUID r3 = reserva(barbeiroA, servico, as(11, 0), "EXPIRADO", null, null, "Visitante Tres");
        UUID r4 = reserva(barbeiroA, servico, as(11, 30), "AGUARDANDO_PAGAMENTO", as(9, 25).toInstant(), null,
                "Visitante Quatro");
        UUID r5 = reserva(barbeiroA, servico, as(14, 0), "CANCELADO", null, cliente("Joao Lima Teste", "11988880005"), null);
        UUID r6 = reserva(barbeiroB, servico, as(9, 30), "CONFIRMADO", null, cliente("Ana Costa Teste", "11988880006"), null);
        return new Massa(servico, barbeiroA, barbeiroB, contaA, contaB, admin, r1, r2, r3, r4, r5, r6, clienteDaR1);
    }

    protected long versaoDa(UUID reservaId) {
        return jdbc.queryForObject("SELECT version FROM reserva WHERE id = ?", Long.class, reservaId);
    }

    protected String statusDaReserva(UUID reservaId) {
        return jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?", String.class, reservaId);
    }

    protected String statusDaCobrancaDaReserva(UUID reservaId) {
        return jdbc.queryForObject("SELECT status FROM cobranca WHERE reserva_id = ?", String.class, reservaId);
    }

    protected long linhasDeAuditoria() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Long.class);
    }

    // ---- falha injetada no pagamento ----------------------------------------------------------------------------------

    /**
     * Faz o listener do pagamento falhar na primeira entrega: o banco recusa a troca de status para DEVOLUCAO_PENDENTE ou
     * CANCELADA (o pagamento tardio, que vira PAGA_APOS_EXPIRACAO, continua passando). Quem chama conserta no finally.
     */
    protected void falharNasCobrancas() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION falha_do_teste() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'falha injetada pelo teste'; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER falha_do_teste BEFORE UPDATE ON cobranca FOR EACH ROW
                WHEN (NEW.status IN ('DEVOLUCAO_PENDENTE', 'CANCELADA')) EXECUTE FUNCTION falha_do_teste()
                """);
    }

    protected void consertarAsCobrancas() {
        jdbc.execute("DROP TRIGGER IF EXISTS falha_do_teste ON cobranca");
    }

    /** Espera o outbox marcar a entrega como falha (o listener já tentou e o banco recusou). */
    protected void esperarEntregaFalhar() throws InterruptedException {
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE status = 'FAILED'", Long.class) == 1);
    }

    protected void esperarOutboxVazio() throws InterruptedException {
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL",
                Long.class) == 0);
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------------

    protected ResultActions get(String rota, Conta quem) throws Exception {
        return get(rota, quem.token());
    }

    protected ResultActions get(String rota, String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions patchStatus(UUID reservaId, Conta quem, String corpo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/admin/agendamentos/" + reservaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    protected static String corpoDeStatus(String status, long version) {
        return """
                { "status": "%s", "version": %d }
                """.formatted(status, version);
    }

    protected static String corpoDeCancelamento(String motivo, long version) {
        return """
                { "status": "CANCELADO", "motivo": "%s", "version": %d }
                """.formatted(motivo, version);
    }

    protected ResultActions post(String rota, Conta quem) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()));
    }

    protected static List<String> valores(String corpo, String caminho) {
        return JsonPath.read(corpo, caminho);
    }

    /** Espera curta por quem roda depois do commit (os listeners do outbox). */
    protected static void esperar(BooleanSupplier condicao) throws InterruptedException {
        for (int i = 0; i < 100 && !condicao.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        org.assertj.core.api.Assertions.assertThat(condicao.getAsBoolean()).as("condição em até 10 s").isTrue();
    }
}
