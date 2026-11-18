package io.github.k3vinrich4rd.altashoras.privacidade;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo privacidade para o consentimento. Cada registro é uma linha nova (a tabela é só inserção):
 * histórico que prova o que foi aceito, quando e com qual versão do texto (R28). O estado atual de uma finalidade é a
 * linha mais recente. Só este módulo toca a tabela {@code consentimento}.
 */
@Service
public class Consentimentos {

    public static final String VERSAO_DO_TEXTO = "1.1";
    public static final String MARKETING = "MARKETING_CEP";

    /** Uma linha do histórico de consentimento. */
    public record Registro(String finalidade, boolean aceito, String versaoTexto, Instant registradoEm) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    Consentimentos(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void registrarInicial(UUID usuarioId, boolean aceitaMarketing) {
        inserir(usuarioId, aceitaMarketing, VERSAO_DO_TEXTO);
    }

    /** O estado atual de cada finalidade (a linha mais recente). Conta sem nenhum registro devolve lista vazia. */
    @Transactional(readOnly = true)
    public List<Registro> atuais(UUID usuarioId) {
        return jdbc.sql("""
                SELECT DISTINCT ON (finalidade) finalidade, aceito, versao_texto, registrado_em
                FROM consentimento WHERE usuario_id = :usuario
                ORDER BY finalidade, registrado_em DESC, id DESC
                """)
                .param("usuario", usuarioId)
                .query(Consentimentos::registro)
                .list();
    }

    /** O histórico inteiro, do mais antigo para o mais novo (a exportação do titular, E14). */
    @Transactional(readOnly = true)
    public List<Registro> historico(UUID usuarioId) {
        return jdbc.sql("""
                SELECT finalidade, aceito, versao_texto, registrado_em
                FROM consentimento WHERE usuario_id = :usuario
                ORDER BY registrado_em, id
                """)
                .param("usuario", usuarioId)
                .query(Consentimentos::registro)
                .list();
    }

    /**
     * Aceita ou revoga o marketing (E12). Se o estado pedido já é o atual, na mesma versão, devolve a linha existente e
     * não grava outra (o duplo clique não polui o histórico); mudou de estado, entra uma linha nova. O lock por conta
     * faz dois pedidos iguais ao mesmo tempo gravarem uma linha só.
     */
    @Transactional
    public Registro registrar(UUID usuarioId, boolean aceito, String versaoTexto) {
        trancarDaConta(usuarioId);
        List<Registro> atual = atuais(usuarioId);
        if (!atual.isEmpty() && atual.getFirst().aceito() == aceito && atual.getFirst().versaoTexto().equals(versaoTexto)) {
            return atual.getFirst();
        }
        return inserir(usuarioId, aceito, versaoTexto);
    }

    /** A última linha da exclusão da conta (E15): o histórico fica e termina em "não aceito". */
    @Transactional
    public void registrarRevogacaoFinal(UUID usuarioId) {
        inserir(usuarioId, false, VERSAO_DO_TEXTO);
    }

    private Registro inserir(UUID usuarioId, boolean aceito, String versaoTexto) {
        Instant agora = clock.instant();
        jdbc.sql("""
                INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em)
                VALUES (:usuario, 'MARKETING_CEP', :versao, :aceito, :agora)
                """)
                .param("usuario", usuarioId)
                .param("versao", versaoTexto)
                .param("aceito", aceito)
                .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .update();
        return new Registro(MARKETING, aceito, versaoTexto, agora);
    }

    private void trancarDaConta(UUID usuarioId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:chave, 0))")
                .param("chave", "consentimento|" + usuarioId)
                .query().listOfRows();
    }

    private static Registro registro(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        return new Registro(rs.getString("finalidade"), rs.getBoolean("aceito"), rs.getString("versao_texto"),
                rs.getObject("registrado_em", OffsetDateTime.class).toInstant());
    }
}
