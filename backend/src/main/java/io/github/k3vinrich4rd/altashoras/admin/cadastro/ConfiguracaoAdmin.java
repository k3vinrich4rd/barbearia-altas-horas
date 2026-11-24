package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConfiguracaoCadastrada;

/** A configuração da agenda como o contrato a escreve (com o fuso, que é só leitura, e a {@code version}). */
record ConfiguracaoAdmin(String fuso, int intervaloEntreAtendimentosMin, int granularidadeMin,
        int expiracaoPagamentoMin, long version) {

    static ConfiguracaoAdmin de(ConfiguracaoCadastrada c) {
        return new ConfiguracaoAdmin(c.fuso(), c.intervaloEntreAtendimentosMin(), c.granularidadeMin(),
                c.expiracaoPagamentoMin(), c.version());
    }
}
