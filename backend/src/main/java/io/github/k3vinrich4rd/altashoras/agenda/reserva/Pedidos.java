package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.comum.validacao.TelefoneBr;

/**
 * Os corpos aceitos. Só os campos do contrato: mandar {@code status}, {@code precoCentavos} ou {@code clienteId}
 * é 400 CAMPO_DESCONHECIDO (A7). O preço e o dono nunca vêm do cliente. O {@code cep} do visitante é opcional, só
 * vale com {@code autorizaRegiao = true} (Fatia 11) e o formato é conferido no caso de uso (400 CEP_INVALIDO, como em
 * {@code GET /cep}), não aqui.
 */
final class Pedidos {

    private Pedidos() {
    }

    record Reserva(
            @NotNull(message = "obrigatório") UUID servicoId,
            @NotNull(message = "obrigatório") UUID barbeiroId,
            @NotNull(message = "obrigatório") OffsetDateTime inicio) {
    }

    record ReservaGuest(
            @NotNull(message = "obrigatório") UUID servicoId,
            @NotNull(message = "obrigatório") UUID barbeiroId,
            @NotNull(message = "obrigatório") OffsetDateTime inicio,
            @NotBlank(message = "obrigatório") @Size(min = 2, max = 120, message = "deve ter de 2 a 120 caracteres") String nome,
            @NotBlank(message = "obrigatório") @Email(message = "deve ser um e-mail válido") @Size(max = 254) String email,
            @NotBlank(message = "obrigatório") @TelefoneBr String whatsapp,
            @Nullable String cep,
            @Nullable Boolean autorizaRegiao,
            @Nullable @Size(max = 20, message = "deve ter no máximo 20 caracteres") String versaoTextoRegiao) {

        Reserva horario() {
            return new Reserva(servicoId, barbeiroId, inicio);
        }

        /** Nunca imprime os dados de contato (R30). */
        @Override
        public String toString() {
            return "ReservaGuest[dados pessoais omitidos]";
        }
    }
}
