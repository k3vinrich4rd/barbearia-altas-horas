package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.time.ZoneId;

import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/** O "Consentimento" do contrato. */
record ConsentimentoResposta(String finalidade, boolean aceito, String versaoTexto, String registradoEm) {

    static ConsentimentoResposta de(Consentimentos.Registro registro, ZoneId fuso) {
        return new ConsentimentoResposta(registro.finalidade(), registro.aceito(), registro.versaoTexto(),
                FormatoDeData.formatar(registro.registradoEm(), fuso));
    }
}
