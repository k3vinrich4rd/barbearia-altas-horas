package io.github.k3vinrich4rd.altashoras.privacidade;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
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

    /** Versão vigente dos textos (política, caixinha de marketing e caixinha de região): a 1.2 chegou com a Fatia 11 (ADR 0014). */
    public static final String VERSAO_DO_TEXTO = "1.2";
    public static final String MARKETING = "MARKETING_CEP";
    /** A região derivada do CEP para o alcance (Fatia 11, ADR 0014): consentimento próprio, revogável. */
    public static final String REGIAO = "REGIAO_ALCANCE";

    /** Uma linha do histórico de consentimento. */
    public record Registro(String finalidade, boolean aceito, String versaoTexto, Instant registradoEm) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    Consentimentos(JdbcClient jdbc, Clock clock, ApplicationEventPublisher eventos) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.eventos = eventos;
    }

    public void registrarInicial(UUID usuarioId, boolean aceitaMarketing) {
        inserir(usuarioId, MARKETING, aceitaMarketing, VERSAO_DO_TEXTO);
    }

    /** O aceite de região no cadastro (só quando a caixinha foi marcada): uma linha REGIAO_ALCANCE, com a versão do texto. */
    public void registrarAceiteDeRegiao(UUID usuarioId, String versaoTexto) {
        inserir(usuarioId, REGIAO, true, versaoTexto);
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
        return registrar(usuarioId, MARKETING, aceito, versaoTexto);
    }

    /**
     * O mesmo para qualquer finalidade (a de região também). Quem revoga a região precisa apagar a localidade: isso é
     * avisado pelo evento {@link RegiaoRevogada}, que o módulo da conta e o da agenda escutam, na mesma transação.
     */
    @Transactional
    public Registro registrar(UUID usuarioId, String finalidade, boolean aceito, String versaoTexto) {
        trancarDaConta(usuarioId);
        Registro atual = atuais(usuarioId).stream().filter(r -> r.finalidade().equals(finalidade)).findFirst().orElse(null);
        if (atual != null && atual.aceito() == aceito && atual.versaoTexto().equals(versaoTexto)) {
            return atual;
        }
        return inserir(usuarioId, finalidade, aceito, versaoTexto);
    }

    /**
     * Aceita ou revoga a região (E17). Revogar, mesmo sem aceite anterior, publica {@link RegiaoRevogada} na mesma transação:
     * a conta e a agenda apagam a localidade (E3). Idempotente.
     */
    @Transactional
    public Registro registrarRegiao(UUID usuarioId, boolean aceito, String versaoTexto) {
        Registro registro = registrar(usuarioId, REGIAO, aceito, versaoTexto);
        if (!aceito) {
            eventos.publishEvent(new RegiaoRevogada(usuarioId));
        }
        return registro;
    }

    /** A conta autoriza hoje o uso da região dela no alcance? (a linha mais recente de REGIAO_ALCANCE é um aceite) */
    @Transactional(readOnly = true)
    public boolean regiaoAutorizada(UUID usuarioId) {
        return atuais(usuarioId).stream().anyMatch(r -> r.finalidade().equals(REGIAO) && r.aceito());
    }

    /** A versão do texto de região que a conta aceitou (e ainda vale), ou vazio. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> versaoDaRegiaoAceita(UUID usuarioId) {
        return atuais(usuarioId).stream().filter(r -> r.finalidade().equals(REGIAO) && r.aceito()).map(Registro::versaoTexto)
                .findFirst();
    }

    /** A última linha da exclusão da conta (E15): o histórico fica e termina em "não aceito". */
    @Transactional
    public void registrarRevogacaoFinal(UUID usuarioId) {
        inserir(usuarioId, MARKETING, false, VERSAO_DO_TEXTO);
        // A região segue o mesmo caminho: a exclusão encerra o histórico dela também (só se a conta chegou a aceitar).
        if (regiaoAutorizada(usuarioId)) {
            inserir(usuarioId, REGIAO, false, VERSAO_DO_TEXTO);
        }
    }

    private Registro inserir(UUID usuarioId, String finalidade, boolean aceito, String versaoTexto) {
        Instant agora = clock.instant();
        jdbc.sql("""
                INSERT INTO consentimento (usuario_id, finalidade, versao_texto, aceito, registrado_em)
                VALUES (:usuario, :finalidade, :versao, :aceito, :agora)
                """)
                .param("usuario", usuarioId)
                .param("finalidade", finalidade)
                .param("versao", versaoTexto)
                .param("aceito", aceito)
                .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .update();
        return new Registro(finalidade, aceito, versaoTexto, agora);
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
