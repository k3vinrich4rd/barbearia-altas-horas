package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.k3vinrich4rd.altashoras.ContaDeTeste;
import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.CadastrosBase;

/**
 * A massa da Fatia 15 (seção 5 da spec): o Eduardo com a jornada real (terça a sábado, 07:00–12:00 e 13:00–20:00),
 * um serviço de 30 min e o relógio em ter 01/12/2026 10:00 (São Paulo). Os helpers montam os pedidos de folga como o
 * painel manda, sem nenhum dado pessoal real.
 */
public abstract class FeriasBase extends CadastrosBase {

    protected UUID eduardo;
    protected UUID servico;
    protected Conta admin;

    @BeforeEach
    protected void massaDaFatia15() {
        irPara(TERCA, 10, 0);
        eduardo = DadosDeTeste.barbeiro(jdbc, true);
        servico = DadosDeTeste.servico(jdbc, "Corte", 30, 4500, true);
        admin = admin();
    }

    protected static OffsetDateTime em(int ano, int mes, int dia, int hora, int minuto) {
        return DadosDeTeste.em(LocalDate.of(ano, mes, dia), hora, minuto);
    }

    protected static OffsetDateTime dez(int dia, int hora, int minuto) {
        return em(2026, 12, dia, hora, minuto);
    }

    /** Férias do A1: 21/12/2026 00:00 a 03/01/2027 00:00. */
    protected static final OffsetDateTime FERIAS_INICIO = dez(21, 0, 0);
    protected static final OffsetDateTime FERIAS_FIM = em(2027, 1, 3, 0, 0);

    protected String rotaDasFolgas(UUID barbeiro) {
        return "/admin/barbeiros/" + barbeiro + "/folgas";
    }

    /** O corpo do POST/PUT; os campos nulos ficam fora (o front da 1.11.0 não manda tipo nem manterReservas). */
    protected static String corpo(OffsetDateTime inicio, OffsetDateTime fim, String tipo, String motivo, Boolean manter) {
        StringBuilder b = new StringBuilder("{ \"inicio\": \"" + inicio + "\", \"fim\": \"" + fim + "\"");
        if (tipo != null) {
            b.append(", \"tipo\": \"").append(tipo).append('"');
        }
        if (motivo != null) {
            b.append(", \"motivo\": \"").append(motivo).append('"');
        }
        if (manter != null) {
            b.append(", \"manterReservas\": ").append(manter);
        }
        return b.append(" }").toString();
    }

    protected ResultActions criarFolga(String corpo) throws Exception {
        return postJson(rotaDasFolgas(eduardo), admin, corpo);
    }

    protected ResultActions alterarFolga(String folgaId, String corpo) throws Exception {
        return put(rotaDasFolgas(eduardo) + "/" + folgaId, admin, corpo);
    }

    protected ResultActions conflitos(UUID barbeiro, Conta quem, OffsetDateTime inicio, OffsetDateTime fim)
            throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(rotaDasFolgas(barbeiro) + "/conflitos").param("inicio", inicio.toString())
                .param("fim", fim.toString()).header(HttpHeaders.AUTHORIZATION, "Bearer " + quem.token()));
    }

    protected long folgasGravadas() {
        return jdbc.queryForObject("SELECT count(*) FROM folga WHERE barbeiro_id = ?", Long.class, eduardo);
    }

    protected List<String> auditoria(String acao) {
        return jdbc.queryForList("SELECT detalhe::text FROM auditoria_admin WHERE acao = ? ORDER BY ocorrido_em", String.class,
                acao);
    }

    /** POST /agendamentos/guest como visitante (IP novo, para não esbarrar no limite de tentativas). */
    protected ResultActions reservarComoVisitante(OffsetDateTime inicio) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/agendamentos/guest").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s",
                          "nome": "Visitante Teste", "email": "visitante@exemplo.test", "whatsapp": "(11) 90000-0002" }
                        """.formatted(servico, eduardo, inicio))
                .with(ContaDeTeste.ip(ContaDeTeste.ipNovo())));
    }

    /** POST /admin/agendamentos (reserva manual, Fatia 6D) como ADMIN. */
    protected ResultActions reservaManual(OffsetDateTime inicio) throws Exception {
        return postJson("/admin/agendamentos", admin, """
                { "servicoId": "%s", "barbeiroId": "%s", "inicio": "%s", "nomeCliente": "Cliente do WhatsApp" }
                """.formatted(servico, eduardo, inicio));
    }

    /** Reserva CONFIRMADO de cliente logado e AGUARDANDO_PAGAMENTO (no prazo) de visitante, as duas do A8. */
    protected UUID[] reservasDoA8() {
        UUID cliente = cliente("Cliente Exemplo", "11988880001");
        UUID confirmada = reserva(eduardo, servico, dez(22, 15, 0), "CONFIRMADO", null, cliente, null);
        UUID pendente = reserva(eduardo, servico, dez(22, 15, 30), "AGUARDANDO_PAGAMENTO",
                relogio.instant().plusSeconds(120), null, "Visitante Dois");
        return new UUID[] {confirmada, pendente};
    }

    protected List<String> horasDe(LocalDate dia) throws Exception {
        return horas(servico, eduardo, dia);
    }
}
