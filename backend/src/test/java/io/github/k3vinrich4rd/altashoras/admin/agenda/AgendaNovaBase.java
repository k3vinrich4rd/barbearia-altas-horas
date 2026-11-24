package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;
import io.github.k3vinrich4rd.altashoras.admin.PainelBase;

/**
 * A massa da agenda nova do painel (Fatia 14C, seção 9 da spec). Eduardo (ADMIN e BARBEIRO) e Bruno (BARBEIRO convidado),
 * os dois com a jornada padrão (ter a sáb, 07:00–12:00 e 13:00–20:00). Reservas do Eduardo em dezembro de 2026: ter 01/12
 * às 09:00 Corte 60 min CONFIRMADO, às 10:00 CONCLUIDO, às 15:00 AGUARDANDO_PAGAMENTO no prazo, às 16:00 EXPIRADO e às
 * 17:00 CANCELADO; qui 31/12 às 22:00 (01:00 UTC de 01/01/2027) CONFIRMADO, direto no banco. Folga do Eduardo qua 02/12
 * das 14:00 às 16:00. Bruno: ter 01/12 às 11:00 CONFIRMADO. O relógio fica na terça 01/12/2026, 09:30 (PainelBase).
 * Nomes, contas e WhatsApp são fictícios.
 */
abstract class AgendaNovaBase extends PainelBase {

    protected record MassaDaAgenda(Conta eduardo, Conta bruno, UUID barbeiroEduardo, UUID barbeiroBruno, UUID servico,
            UUID r0900, UUID r1000, UUID r1500, UUID r1600, UUID r1700, UUID r3112, UUID rBruno) {
    }

    protected MassaDaAgenda massaDaAgenda() {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte", 60, 5500, true);
        Conta eduardo = conta("ADMIN", "BARBEIRO");
        Conta bruno = conta("BARBEIRO");
        UUID barbeiroEduardo = barbeiroDaConta(eduardo.id());
        UUID barbeiroBruno = barbeiroDaConta(bruno.id());
        jdbc.update("UPDATE barbeiro SET nome_exibicao = ? WHERE id = ?", "Eduardo Teste " + barbeiroEduardo, barbeiroEduardo);
        jdbc.update("UPDATE barbeiro SET nome_exibicao = ? WHERE id = ?", "Bruno Teste " + barbeiroBruno, barbeiroBruno);
        UUID cliente = cliente("Maria Souza Teste", "11988880001");

        UUID r0900 = reserva60(barbeiroEduardo, servico, as(9, 0), "CONFIRMADO", cliente);
        UUID r1000 = reserva60(barbeiroEduardo, servico, as(10, 0), "CONCLUIDO", cliente);
        UUID r1500 = reserva60(barbeiroEduardo, servico, as(15, 0), "AGUARDANDO_PAGAMENTO", null);
        UUID r1600 = reserva60(barbeiroEduardo, servico, as(16, 0), "EXPIRADO", null);
        UUID r1700 = reserva60(barbeiroEduardo, servico, as(17, 0), "CANCELADO", cliente);
        UUID r3112 = reserva60(barbeiroEduardo, servico, DadosDeTeste.em(LocalDate.of(2026, 12, 31), 22, 0), "CONFIRMADO",
                cliente);
        UUID rBruno = reserva60(barbeiroBruno, servico, as(11, 0), "CONFIRMADO", cliente);
        DadosDeTeste.folga(jdbc, barbeiroEduardo, DadosDeTeste.em(LocalDate.of(2026, 12, 2), 14, 0),
                DadosDeTeste.em(LocalDate.of(2026, 12, 2), 16, 0));
        return new MassaDaAgenda(eduardo, bruno, barbeiroEduardo, barbeiroBruno, servico, r0900, r1000, r1500, r1600,
                r1700, r3112, rBruno);
    }

    /** Reserva de 60 min (ocupa até o fim; o intervalo entre atendimentos do padrão é 0). A aguardando vence às 09:33. */
    protected UUID reserva60(UUID barbeiro, UUID servico, OffsetDateTime inicio, String status, UUID clienteId) {
        UUID id = UUID.randomUUID();
        boolean aguardando = "AGUARDANDO_PAGAMENTO".equals(status);
        boolean pago = List.of("CONFIRMADO", "CONCLUIDO", "NAO_COMPARECEU").contains(status);
        jdbc.update("""
                INSERT INTO reserva (id, barbeiro_id, servico_id, cliente_id, origem, inicio, fim, fim_ocupacao, status,
                                     preco_centavos, expira_em, aceite_politica_versao, aceite_politica_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 5500, ?, ?, ?)
                """, id, barbeiro, servico, clienteId, clienteId == null ? "GUEST" : "CLIENTE", inicio,
                inicio.plusMinutes(60), inicio.plusMinutes(60), status,
                aguardando ? OffsetDateTime.of(2026, 12, 1, 12, 33, 0, 0, ZoneOffset.UTC) : null, pago ? 1 : null,
                pago ? inicio.minusDays(1) : null);
        if (clienteId == null) {
            jdbc.update("""
                    INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
                    VALUES (?, 'Visitante Teste', ?, '11988880002', ?)
                    """, id, "visitante." + id + "@exemplo.test", hashFalso());
        }
        return id;
    }

    protected String corpo(String rota, Conta quem) throws Exception {
        return get(rota, quem).andReturn().getResponse().getContentAsString();
    }

    /** O item de um dia no ResumoDoMes. */
    protected static Object dia(String corpo, String data, String campo) {
        List<Object> valores = JsonPath.read(corpo, "$.dias[?(@.data == '" + data + "')]." + campo);
        return valores.getFirst();
    }

    /** A situação de um horário da coluna de um barbeiro na GradeDoDia. */
    protected static List<String> situacoes(String corpo, UUID barbeiro) {
        return JsonPath.read(corpo, "$.colunas[?(@.barbeiroId == '" + barbeiro + "')].horarios[*].situacao");
    }

    protected static List<String> inicios(String corpo, UUID barbeiro, String situacao) {
        return JsonPath.read(corpo, "$.colunas[?(@.barbeiroId == '" + barbeiro + "')].horarios[?(@.situacao == '"
                + situacao + "')].inicio");
    }
}
