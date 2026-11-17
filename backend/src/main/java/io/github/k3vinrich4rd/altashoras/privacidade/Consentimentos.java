package io.github.k3vinrich4rd.altashoras.privacidade;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * API pública do módulo privacidade. Nesta fatia, só o registro do consentimento informado no cadastro (E20);
 * consultar, revogar, exportar e excluir chegam na Fatia 6. Cada registro é uma linha nova: histórico que prova
 * o que foi aceito, quando e com qual versão do texto (R28).
 */
@Service
public class Consentimentos {

    public static final String VERSAO_DO_TEXTO = "1.0";

    private final JdbcClient jdbc;
    private final Clock clock;

    Consentimentos(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void registrarInicial(UUID usuarioId, boolean aceitaMarketing) {
        jdbc.sql("""
                INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em)
                VALUES (:usuario, 'MARKETING_CEP', :versao, :aceito, :agora)
                """)
                .param("usuario", usuarioId)
                .param("versao", VERSAO_DO_TEXTO)
                .param("aceito", aceitaMarketing)
                .param("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }
}
