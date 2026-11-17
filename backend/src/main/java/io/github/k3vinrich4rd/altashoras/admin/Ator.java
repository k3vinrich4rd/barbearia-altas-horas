package io.github.k3vinrich4rd.altashoras.admin;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quem está usando o painel: o {@code sub} do token (nunca um parâmetro da URL), se tem o papel ADMIN (quem tem
 * ADMIN e BARBEIRO vale como ADMIN, E3) e o IP da requisição, que só a auditoria guarda.
 */
public record Ator(UUID id, boolean admin, String ip) {

    public static Ator de(Jwt token, HttpServletRequest request) {
        List<String> papeis = token.getClaimAsStringList("papeis");
        return new Ator(UUID.fromString(token.getSubject()), papeis != null && papeis.contains("ADMIN"),
                request.getRemoteAddr());
    }
}
