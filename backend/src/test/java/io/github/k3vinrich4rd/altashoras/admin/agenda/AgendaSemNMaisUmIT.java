package io.github.k3vinrich4rd.altashoras.admin.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.ContadorDeSql;

/** A46 da Fatia 14C (E16): o número de consultas SQL das duas rotas não cresce com o número de barbeiros. */
class AgendaSemNMaisUmIT extends AgendaNovaBase {

    private long consultas(String rota, Conta quem) throws Exception {
        get(rota, quem).andExpect(status().isOk()); // aquece (cache do Hibernate, conexões)
        long antes = ContadorDeSql.total();
        get(rota, quem).andExpect(status().isOk());
        return ContadorDeSql.total() - antes;
    }

    @Test
    @DisplayName("A46: ADMIN vendo todos os barbeiros: com um barbeiro a mais (e a reserva dele) o número de consultas SQL de cada rota é o mesmo (sem N+1); com o filtro de 1 barbeiro também não muda")
    void mesmoNumeroDeConsultas() throws Exception {
        MassaDaAgenda m = massaDaAgenda();
        String filtro = "&barbeiroId=" + m.barbeiroEduardo();
        long mesUm = consultas("/admin/agenda/mes?mes=2026-12" + filtro, m.eduardo());
        long diaUm = consultas("/admin/agenda/dia?data=2026-12-01" + filtro, m.eduardo());
        long mesTodos = consultas("/admin/agenda/mes?mes=2026-12", m.eduardo());
        long diaTodos = consultas("/admin/agenda/dia?data=2026-12-01", m.eduardo());

        for (int i = 0; i < 2; i++) {
            UUID outro = barbeiroDaConta(conta("BARBEIRO").id());
            reserva60(outro, m.servico(), as(14, 0), "CONFIRMADO", cliente("Ana Costa Teste", "1198888000" + i));
            assertThat(consultas("/admin/agenda/mes?mes=2026-12", m.eduardo())).isEqualTo(mesTodos);
            assertThat(consultas("/admin/agenda/dia?data=2026-12-01", m.eduardo())).isEqualTo(diaTodos);
            assertThat(consultas("/admin/agenda/mes?mes=2026-12" + filtro, m.eduardo())).isEqualTo(mesUm);
            assertThat(consultas("/admin/agenda/dia?data=2026-12-01" + filtro, m.eduardo())).isEqualTo(diaUm);
        }
        // com filtro há só a checagem de que o barbeiro existe a mais (a política do AcessoAgenda)
        assertThat(diaUm - diaTodos).isBetween(0L, 1L);
    }
}
