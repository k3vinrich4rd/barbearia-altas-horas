package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.as;
import static io.github.k3vinrich4rd.altashoras.agenda.reserva.Reservas.corpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.PostgresTestcontainers;
import io.github.k3vinrich4rd.altashoras.RelogioFixoDeTeste;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, RelogioFixoDeTeste.class})
class MeusAgendamentosIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory emf;

    private UUID barbeiro;
    private UUID servico;

    @BeforeEach
    void dados() {
        barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, 30);
    }

    private String reservar(String acesso, OffsetDateTime inicio) throws Exception {
        String resposta = Reservas.reservar(mvc, acesso, corpo(servico, barbeiro, inicio))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private static String bearer(String acesso) {
        return "Bearer " + acesso;
    }

    @Test
    @DisplayName("A14: cliente com duas reservas → 200, as duas, mais recentes primeiro, paginado")
    void minhasReservas() throws Exception {
        String acesso = Reservas.clienteLogado(mvc);
        String dez = reservar(acesso, as(10, 0));
        String onze = reservar(acesso, as(11, 0));

        mvc.perform(get("/me/agendamentos").header(HttpHeaders.AUTHORIZATION, bearer(acesso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.itens[0].id").value(onze))
                .andExpect(jsonPath("$.itens[1].id").value(dez))
                .andExpect(jsonPath("$.itens[0].servico.nome").exists());
        mvc.perform(get("/me/agendamentos").param("pagina", "1").param("tamanho", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(acesso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(dez));
        mvc.perform(get("/me/agendamentos").param("tamanho", "51").header(HttpHeaders.AUTHORIZATION, bearer(acesso)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/me/agendamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A15: a reserva do cliente A pedida pelo cliente B → 404 (R23)")
    void reservaDeOutroDono() throws Exception {
        String acessoA = Reservas.clienteLogado(mvc);
        String id = reservar(acessoA, as(10, 0));

        mvc.perform(get("/agendamentos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(acessoA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/agendamentos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(Reservas.clienteLogado(mvc))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("A16: cancelar ou remarcar como cliente → 404: as rotas não existem (R18)")
    void semCancelarNemRemarcar() throws Exception {
        String acesso = Reservas.clienteLogado(mvc);
        String id = reservar(acesso, as(10, 0));
        for (String acao : new String[] {"cancelamento", "remarcacao"}) {
            mvc.perform(post("/agendamentos/" + id + "/" + acao).header(HttpHeaders.AUTHORIZATION, bearer(acesso))
                            .with(ContaDeTeste.ip(ContaDeTeste.ipNovo())))
                    .andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM reserva WHERE id = ?::uuid", String.class, id))
                .isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    @Test
    @DisplayName("A17: com 2 e com 20 reservas, o número de consultas é o mesmo (sem N+1)")
    void semNmaisUm() throws Exception {
        ContaDeTeste.Conta poucas = ContaDeTeste.cadastrar(mvc);
        ContaDeTeste.Conta muitas = ContaDeTeste.cadastrar(mvc);
        UUID outroBarbeiro = DadosDeTeste.barbeiro(jdbc, true);
        for (int i = 0; i < 2; i++) {
            inserir(poucas.id(), barbeiro, i);
        }
        for (int i = 0; i < 20; i++) {
            inserir(muitas.id(), outroBarbeiro, i);
        }
        String acessoPoucas = ContaDeTeste.entrar(mvc, poucas.email(), poucas.senha()).acesso();
        String acessoMuitas = ContaDeTeste.entrar(mvc, muitas.email(), muitas.senha()).acesso();

        Statistics estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
        long comDuas = 0;
        long comVinte = 0;
        for (int vez = 0; vez < 3; vez++) {
            long antes = estatisticas.getPrepareStatementCount();
            mvc.perform(get("/me/agendamentos").param("tamanho", "50").header(HttpHeaders.AUTHORIZATION, bearer(acessoPoucas)))
                    .andExpect(jsonPath("$.itens.length()").value(2));
            comDuas = estatisticas.getPrepareStatementCount() - antes;

            antes = estatisticas.getPrepareStatementCount();
            mvc.perform(get("/me/agendamentos").param("tamanho", "50").header(HttpHeaders.AUTHORIZATION, bearer(acessoMuitas)))
                    .andExpect(jsonPath("$.itens.length()").value(20));
            comVinte = estatisticas.getPrepareStatementCount() - antes;
        }
        // Positivo: prova que as estatísticas contaram; igual: 20 reservas não geram consulta a mais.
        assertThat(comDuas).isPositive().isLessThanOrEqualTo(2);
        assertThat(comVinte).isEqualTo(comDuas);
    }

    /** 20 horários seguidos de 30 min: 07h00 a 11h30 e 13h00 a 17h30. */
    private void inserir(String clienteId, UUID barbeiroId, int indice) {
        OffsetDateTime inicio = indice < 10 ? as(7, 0).plusMinutes(30L * indice) : as(13, 0).plusMinutes(30L * (indice - 10));
        jdbc.update("""
                INSERT INTO reserva (barbeiro_id, servico_id, cliente_id, origem, inicio, fim, fim_ocupacao, status,
                                     preco_centavos, expira_em)
                VALUES (?, ?, ?::uuid, 'CLIENTE', ?, ?, ?, 'AGUARDANDO_PAGAMENTO', 5500, ?)
                """, barbeiroId, servico, clienteId, inicio, inicio.plusMinutes(30), inicio.plusMinutes(30),
                OffsetDateTime.ofInstant(RelogioFixoDeTeste.AGORA.plusSeconds(900), ZoneOffset.UTC));
    }
}
