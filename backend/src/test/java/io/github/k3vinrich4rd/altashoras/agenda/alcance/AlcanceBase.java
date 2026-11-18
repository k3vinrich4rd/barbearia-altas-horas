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

    /**
     * Um atendimento direto no banco, num horário novo (30 minutos depois do anterior) do barbeiro, com a região
     * copiada (nula = sem região). É do visitante: o alcance não depende de quem foi atendido.
     */
    protected UUID atendimento(UUID barbeiro, UUID servico, String status, String bairro, String cidade, String uf) {
        return atendimentoEm(barbeiro, servico, DIA_DO_ATENDIMENTO, status, bairro, cidade, uf);
    }

    protected UUID atendimentoEm(UUID barbeiro, UUID servico, LocalDate dia, String status, String bairro, String cidade,
            String uf) {
        OffsetDateTime inicio = DadosDeTeste.em(dia, 7, 0).plusMinutes(30L * proximoHorario++);
        UUID id = reserva(barbeiro, servico, inicio, status, null, null, "Visitante Fictício");
        jdbc.update("UPDATE reserva SET regiao_bairro = ?, regiao_cidade = ?, regiao_uf = ? WHERE id = ?", bairro, cidade, uf,
                id);
        return id;
    }

    /** {@code quantos} atendimentos CONCLUIDO na mesma região (o horário anda 30 minutos a cada um). */
    protected void atendimentos(int quantos, UUID barbeiro, UUID servico, String bairro, String cidade, String uf) {
        atendimentosEm(quantos, barbeiro, servico, DIA_DO_ATENDIMENTO, bairro, cidade, uf);
    }

    protected void atendimentosEm(int quantos, UUID barbeiro, UUID servico, LocalDate dia, String bairro, String cidade,
            String uf) {
        for (int i = 0; i < quantos; i++) {
            atendimentoEm(barbeiro, servico, dia, "CONCLUIDO", bairro, cidade, uf);
        }
    }

    /**
     * O cenário do A67: 6 atendimentos CONCLUIDO em Centro/Osasco, 2 em Sé/São Paulo, 1 EXPIRADO em Centro (fora) e 1
     * CONFIRMADO sem região.
     */
    protected void cenarioDoA67() {
        Catalogo c = catalogo();
        for (int i = 0; i < 6; i++) {
            atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", "Centro", "Osasco", "SP");
        }
        for (int i = 0; i < 2; i++) {
            atendimento(c.barbeiro(), c.servico(), "CONCLUIDO", "Sé", "São Paulo", "SP");
        }
        atendimento(c.barbeiro(), c.servico(), "EXPIRADO", "Centro", "Osasco", "SP");
        atendimento(c.barbeiro(), c.servico(), "CONFIRMADO", null, null, null);
    }

    // ---- HTTP -------------------------------------------------------------------------------------------------------

    protected static String corpoGuest(Catalogo c, String hora, String cep) {
        String campoCep = cep == null ? "" : ", \"cep\": \"%s\"".formatted(cep);
        return """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s",
                  "nome": "Visitante Teste", "email": "visitante@exemplo.test", "whatsapp": "(11) 90000-0002"%s }
                """.formatted(c.servico(), c.barbeiro(), DadosDeTeste.em(TERCA_DA_RESERVA, Integer.parseInt(hora.substring(0, 2)),
                Integer.parseInt(hora.substring(3))), campoCep);
    }

    /** Reserva do visitante pela API, de um IP novo (o limite de um teste não atrapalha o outro). */
    protected ResultActions reservarComoVisitante(Catalogo c, String hora, String cep) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                .content(corpoGuest(c, hora, cep)).with(ip(ipNovo())));
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
}
