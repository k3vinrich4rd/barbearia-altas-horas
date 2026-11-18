package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** O mesmo formato do resto da API: segundos e offset do fuso da barbearia (2026-12-01T09:30:00-03:00). */
final class FormatoDeData {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FormatoDeData() {
    }

    static String formatar(Instant instante, ZoneId fuso) {
        return FORMATO.format(instante.atZone(fuso));
    }
}
