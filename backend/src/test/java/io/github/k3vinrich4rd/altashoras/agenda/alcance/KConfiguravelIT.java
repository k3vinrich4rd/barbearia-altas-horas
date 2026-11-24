package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.k3vinrich4rd.altashoras.cep.TabelaIbge;

/**
 * A23 da Fatia 11B: o k é configuração ({@code app.alcance.k}), não código. Este contexto sobe com k = 3; o 8 roda na mesma regra
 * pura (o teste de propriedade cobre 3, 5 e 8); e k menor que 3 impede a aplicação de subir (ADR 0014, item 2).
 */
@SpringBootTest(properties = {"app.teste.contexto=alcance-k3", "app.alcance.k=3"})
class KConfiguravelIT extends AlcanceBase {

    @Test
    @DisplayName("A23: com app.alcance.k = 3 o painel mostra um município de 3 pessoas (com k = 5 ele subiria), e a resposta diz k = 3; com k = 8 o mesmo grupo de 7 sobe; k = 2 não sobe a aplicação")
    void kMudaOComportamentoSemMudarCodigo() throws Exception {
        Catalogo c = catalogo();
        pessoas(3, c.barbeiro(), c.servico(), CENTRO);

        String corpo = painel("?nivel=MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.k")).isEqualTo(3);
        assertThat(this.<String>ler(corpo, "$.grupos[0].tipo")).isEqualTo("MUNICIPIO");
        assertThat(this.<Integer>ler(corpo, "$.grupos[0].clientes")).isEqualTo(3);

        // k = 8 na mesma regra: 7 pessoas ainda não formam um município
        TabelaIbge tabela = new TabelaIbge();
        NiveisDeAlcance com8 = new NiveisDeAlcance(tabela, 8, NiveisDeAlcance.Regras.TODAS);
        List<NiveisDeAlcance.Pessoa> setePessoas = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> new NiveisDeAlcance.Pessoa(new NiveisDeAlcance.Lugar("SP", OSASCO, "Centro"),
                        List.of(new NiveisDeAlcance.Visita(java.util.UUID.randomUUID(), java.time.Instant.parse("2026-11-10T12:00:00Z")))))
                .toList();
        NiveisDeAlcance.Janela janela = new NiveisDeAlcance.Janela(java.time.Instant.parse("2026-11-01T00:00:00Z"),
                java.time.Instant.parse("2026-11-18T00:00:00Z"));
        var publicacao = com8.publicar(setePessoas, new NiveisDeAlcance.Recorte(null, janela), r -> List.of());
        assertThat(com8.painel(publicacao, io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Nivel.MUNICIPIO, null).grupos())
                .noneMatch(g -> "MUNICIPIO".equals(g.tipo()));

        // k < 3: a aplicação não sobe
        assertThatThrownBy(() -> new ConsultaAlcance(null, null, tabela, 2, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.alcance.k");
    }
}
