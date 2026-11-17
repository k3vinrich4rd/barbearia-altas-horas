package io.github.k3vinrich4rd.altashoras.agenda.catalogo;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.conta.AdminInicialCriado;

/**
 * Quando o primeiro admin nasce, ele é ligado ao cadastro do barbeiro, mas só se houver exatamente um barbeiro
 * sem usuário (o caso da Altas Horas). Com mais de um, ninguém é vinculado sozinho: o admin escolhe no painel.
 * A agenda escuta o evento da conta; a conta não sabe que a agenda existe.
 */
@Component
class VinculoDoBarbeiro {

    private final JdbcClient jdbc;

    VinculoDoBarbeiro(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void aoCriarAdminInicial(AdminInicialCriado evento) {
        jdbc.sql("""
                UPDATE barbeiro SET usuario_id = :usuario
                WHERE usuario_id IS NULL AND (SELECT count(*) FROM barbeiro WHERE usuario_id IS NULL) = 1
                """).param("usuario", evento.usuarioId()).update();
    }
}
