package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * O núcleo da regra de disponibilidade, como função pura: não conhece Spring, JPA nem banco (é o "domínio" da
 * arquitetura hexagonal; quem busca os dados é a ConsultaDisponibilidade, pela porta FonteDeBloqueios).
 *
 * Um início h é oferecido quando:
 *   1. está na grade do turno (do início do turno, de granularidade em granularidade);
 *   2. o serviço inteiro, mais o intervalo entre atendimentos, cabe no turno: [h, h + duração + intervalo);
 *   3. h > agora (desde a Fatia 14A, D197, não há antecedência mínima: só vale o início depois de agora);
 *   4. esse intervalo não sobrepõe nenhum bloqueio (folga ou reserva que ocupa horário).
 * É a mesma conta que a constraint do banco faz na reserva: a tela não oferece o que o banco recusaria.
 */
public final class CalculadoraDeHorarios {

    /** Um intervalo de trabalho no dia, em hora local. */
    public record Turno(LocalTime inicio, LocalTime fim) {
    }

    /**
     * Um período em que o barbeiro não pode atender: [inicio, fim), meio aberto. {@code folga} diz se veio de uma folga
     * ou férias (o expediente do dia usa isso, E18); para a conta dos horários, folga e reserva bloqueiam igual.
     */
    public record Bloqueio(Instant inicio, Instant fim, boolean folga) {

        public Bloqueio(Instant inicio, Instant fim) {
            this(inicio, fim, false);
        }
    }

    /** As regras configuráveis (configuracao_agenda), em minutos. */
    public record Regras(int intervaloEntreAtendimentosMin, int granularidadeMin) {
    }

    private CalculadoraDeHorarios() {
    }

    public static List<ZonedDateTime> calcular(LocalDate data, ZoneId fuso, List<Turno> turnos, List<Bloqueio> bloqueios,
            int duracaoMin, Regras regras, Instant agora) {
        int ocupacaoMin = duracaoMin + regras.intervaloEntreAtendimentosMin();
        List<ZonedDateTime> livres = new ArrayList<>();

        for (Turno turno : turnos.stream().sorted(Comparator.comparing(Turno::inicio)).toList()) {
            // Em minutos do dia, para o serviço das 23h30 não "dar a volta" na meia-noite do LocalTime.
            int fimDoTurno = turno.fim().toSecondOfDay() / 60;
            for (int minuto = turno.inicio().toSecondOfDay() / 60; minuto + ocupacaoMin <= fimDoTurno;
                    minuto += regras.granularidadeMin()) {
                ZonedDateTime inicio = ZonedDateTime.of(data, LocalTime.ofSecondOfDay(minuto * 60L), fuso);
                Instant comeca = inicio.toInstant();
                Instant termina = comeca.plus(Duration.ofMinutes(ocupacaoMin));
                if (comeca.isAfter(agora) && livre(comeca, termina, bloqueios)) {
                    livres.add(inicio);
                }
            }
        }
        return livres;
    }

    /** Dois intervalos meio abertos se sobrepõem quando um começa antes de o outro terminar, nos dois sentidos. */
    private static boolean livre(Instant comeca, Instant termina, List<Bloqueio> bloqueios) {
        for (Bloqueio bloqueio : bloqueios) {
            if (bloqueio.inicio().isBefore(termina) && bloqueio.fim().isAfter(comeca)) {
                return false;
            }
        }
        return true;
    }
}
