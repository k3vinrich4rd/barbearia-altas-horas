package io.github.k3vinrich4rd.altashoras.admin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.jspecify.annotations.Nullable;

/** O mesmo formato da agenda do cliente: sempre com segundos e offset (2026-12-01T10:00:00-03:00). */
public final class FormatoDeData {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FormatoDeData() {
    }

    public static @Nullable String formatar(@Nullable Instant instante, ZoneId fuso) {
        return instante == null ? null : FORMATO.format(instante.atZone(fuso));
    }
}
