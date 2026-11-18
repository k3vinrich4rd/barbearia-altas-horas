/**
 * Módulo Agenda. Serviços, barbeiro, jornada, disponibilidade e reservas com trava de 15 minutos (Fatias 2 e 4) e o
 * alcance por região (Fatia 6C): a reserva copia a região do cliente ou do CEP do visitante, e {@code AlcanceDaAgenda} agrega.
 */
@ApplicationModule(displayName = "Agenda")
package io.github.k3vinrich4rd.altashoras.agenda;

import org.springframework.modulith.ApplicationModule;
