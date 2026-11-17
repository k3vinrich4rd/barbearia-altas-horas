package io.github.k3vinrich4rd.altashoras.plataforma.seguranca;

import org.springframework.security.access.AccessDeniedException;

/** Logado e com o papel certo, mas ainda usando a senha inicial (E24): vira 403 TROCA_DE_SENHA_OBRIGATORIA. */
public class TrocaDeSenhaObrigatoriaException extends AccessDeniedException {

    public TrocaDeSenhaObrigatoriaException() {
        super("troca de senha obrigatória");
    }
}
