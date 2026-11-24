package io.github.k3vinrich4rd.altashoras.conta.admin;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.conta.AdminInicialCriado;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;

/**
 * Cria o primeiro admin na subida, a partir de ADMIN_EMAIL e ADMIN_SENHA (R25). Só quando ainda não existe
 * nenhum admin: depois disso as variáveis são ignoradas e nunca sobrescrevem a senha. Nada do que este
 * componente registra no log contém e-mail ou senha.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final ApplicationEventPublisher eventos;
    private final String email;
    private final String senha;
    private final String nome;

    AdminInicial(UsuarioRepository usuarios, PasswordEncoder codificador, ApplicationEventPublisher eventos,
            @Value("${app.admin.email:}") String email, @Value("${app.admin.senha:}") String senha,
            @Value("${app.admin.nome:Administrador}") String nome) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.eventos = eventos;
        this.email = email;
        this.senha = senha;
        this.nome = nome;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        garantir(email, senha);
    }

    /** @return true se criou o admin agora. */
    @Transactional
    public boolean garantir(String emailDoAdmin, String senhaDoAdmin) {
        if (usuarios.existeComPapel(Usuario.ADMIN)) {
            return false;
        }
        if (emailDoAdmin == null || emailDoAdmin.isBlank() || senhaDoAdmin == null || senhaDoAdmin.isBlank()) {
            LOG.warn("Nenhum admin cadastrado. Defina ADMIN_EMAIL e ADMIN_SENHA e reinicie a API para criar o primeiro.");
            return false;
        }
        int bytes = senhaDoAdmin.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 10 || bytes > 72) {
            throw new IllegalStateException("ADMIN_SENHA precisa ter de 10 a 72 bytes. O admin não foi criado.");
        }
        // Admin e barbeiro: na Altas Horas é a mesma pessoa (D36). A senha veio de uma variável que quem
        // configurou o servidor conhece, por isso a troca é obrigatória no primeiro acesso (E24).
        Usuario admin = usuarios.saveAndFlush(new Usuario(nome, Email.de(emailDoAdmin).valor(), null,
                codificador.encode(senhaDoAdmin), Set.of(Usuario.ADMIN, Usuario.BARBEIRO), true));
        eventos.publishEvent(new AdminInicialCriado(admin.getId()));
        LOG.info("Primeiro admin criado (id {}). A troca de senha é obrigatória no primeiro acesso.", admin.getId());
        return true;
    }
}
