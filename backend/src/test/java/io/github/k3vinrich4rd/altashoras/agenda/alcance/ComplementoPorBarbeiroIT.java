package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/**
 * A40 da Fatia 11D: dois recortes diferentes não se subtraem (E11). "Todos os barbeiros" menos "só o barbeiro A" revelaria
 * quantas pessoas são só dos outros; se a diferença está entre 1 e k - 1, a célula do filtro sobe de nível.
 */
class ComplementoPorBarbeiroIT extends AlcanceBase {

    private String deA(UUID barbeiroA) throws Exception {
        return painel("?nivel=MUNICIPIO&barbeiroId=" + barbeiroA);
    }

    private List<Object> municipios(String corpo) {
        return ler(corpo, "$.grupos[?(@.tipo == 'MUNICIPIO')].nome");
    }

    @Test
    @DisplayName("A40: ADMIN, \"todos\" com Osasco 10 e ?barbeiroId=A com Osasco 8 (diferença 2): no pedido de A o Osasco sobe de nível (a diferença 2 seria um número menor que k); com diferença 0 ou pelo menos 5 a célula aparece")
    void diferencaPequenaEntreRecortesSobeDeNivel() throws Exception {
        UUID barbeiroA = DadosDeTeste.barbeiro(jdbc, true);
        UUID barbeiroB = DadosDeTeste.barbeiro(jdbc, true);
        UUID servico = DadosDeTeste.servico(jdbc, 30);

        pessoas(8, barbeiroA, servico, CENTRO);
        pessoas(2, barbeiroB, servico, CENTRO);
        assertThat(this.<List<Object>>ler(painel("?nivel=MUNICIPIO"), "$.grupos[?(@.nome == 'Osasco')].clientes")).containsExactly(10);
        String sobe = deA(barbeiroA);
        assertThat(municipios(sobe)).doesNotContain("Osasco");
        // Osasco, a região e a UF têm a mesma diferença (2) para "todos": a célula sobe até onde a diferença deixa de existir
        assertThat(this.<String>ler(sobe, "$.grupos[0].nome")).isEqualTo("Outros estados");
        assertThat(this.<String>ler(sobe, "$.grupos[0].agrupadoEm")).isEqualTo("PAIS");
        assertThat(this.<Integer>ler(sobe, "$.grupos[0].clientes")).isEqualTo(8);
        assertThat(sobe).doesNotContain("Osasco");

        // diferença 5 (B tem 5 pessoas só dele): a célula de A aparece
        jdbc.update("DELETE FROM reserva");
        pessoas(8, barbeiroA, servico, CENTRO);
        pessoas(5, barbeiroB, servico, CENTRO);
        assertThat(municipios(deA(barbeiroA))).containsExactly("Osasco");

        // diferença 0 (o barbeiro B não atende ninguém no período)
        jdbc.update("DELETE FROM reserva");
        pessoas(8, barbeiroA, servico, CENTRO);
        assertThat(municipios(deA(barbeiroA))).containsExactly("Osasco");
    }
}
