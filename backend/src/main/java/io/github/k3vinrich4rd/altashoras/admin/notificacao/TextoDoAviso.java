package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.AvisoParaEnvio;

/**
 * O texto do aviso (E10): "Novo horário: Corte Degradê, qui 27/11 às 10:00", no fuso da barbearia. Só serviço, dia e hora:
 * sem nome, WhatsApp, e-mail, CPF, valor nem o id da reserva no texto (D173).
 */
final class TextoDoAviso {

    static final String TITULO = "Altas Horas";
    static final String LINK = "/painel";

    private static final String[] DIAS = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    private TextoDoAviso() {
    }

    static String novoHorario(String servico, Instant inicio, ZoneId fuso) {
        ZonedDateTime local = inicio.atZone(fuso);
        return "Novo horário: %s, %s %02d/%02d às %02d:%02d".formatted(servico, DIAS[local.getDayOfWeek().getValue() - 1],
                local.getDayOfMonth(), local.getMonthValue(), local.getHour(), local.getMinute());
    }

    /** O {@code tag} é o id da reserva: o sistema operacional junta repetidos. Ele não aparece no texto. */
    static AvisoParaEnvio doNovoHorario(String servico, Instant inicio, UUID reservaId, ZoneId fuso) {
        return new AvisoParaEnvio(TITULO, novoHorario(servico, inicio, fuso), LINK, reservaId.toString());
    }

    static AvisoParaEnvio deTeste() {
        return new AvisoParaEnvio(TITULO, "Notificações ativadas neste aparelho", LINK, "teste");
    }
}
