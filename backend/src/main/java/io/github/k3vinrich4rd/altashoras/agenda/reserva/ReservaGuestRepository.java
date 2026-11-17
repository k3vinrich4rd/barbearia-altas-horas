package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReservaGuestRepository extends JpaRepository<ReservaGuest, UUID> {
}
