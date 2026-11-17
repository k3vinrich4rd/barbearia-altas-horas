package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas.Dono;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Quem está pedindo, nas rotas do pagamento que aceitam as duas formas de acesso: o cliente logado (o id vem do
 * {@code sub} do token, nunca do corpo) ou o visitante com o {@code X-Guest-Token} da reserva. Sem nenhum dos dois: 401.
 */
final class Donos {

    private Donos() {
    }

    static Dono de(@Nullable Jwt token, @Nullable String tokenGuest) {
        if (token != null) {
            return Dono.cliente(UUID.fromString(token.getSubject()));
        }
        if (tokenGuest != null && !tokenGuest.isBlank()) {
            return Dono.visitante(tokenGuest);
        }
        throw new ExcecaoDeNegocio(CodigoErro.NAO_AUTENTICADO);
    }
}
