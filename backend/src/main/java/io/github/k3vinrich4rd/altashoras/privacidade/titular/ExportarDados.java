package io.github.k3vinrich4rd.altashoras.privacidade.titular;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;
import io.github.k3vinrich4rd.altashoras.privacidade.FonteDeDadosDoTitular;

/**
 * A exportação (LGPD art. 18, E14): junta o que cada {@link FonteDeDadosDoTitular} entrega, mais o histórico de
 * consentimentos, sem ler tabela alheia. Grava a solicitação ({@code EXPORTACAO}, só tipo, conta e datas: nada do
 * conteúdo, E22). O limite (H22: 5 por hora por conta) conta as próprias solicitações gravadas: não depende de memória
 * (sobrevive a reinício e a mais de uma instância) e não puxa o módulo plataforma, o que mantém a privacidade
 * dependendo só do comum (E10).
 */
@Service
class ExportarDados {

    private final List<FonteDeDadosDoTitular> fontes;
    private final Consentimentos consentimentos;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId fuso;
    private final int limite;
    private final Duration janela;

    ExportarDados(List<FonteDeDadosDoTitular> fontes, Consentimentos consentimentos, JdbcClient jdbc, Clock clock,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso,
            @Value("${app.limites.exportacao-max:5}") int limite,
            @Value("${app.limites.exportacao-janela:1h}") Duration janela) {
        this.fontes = fontes.stream().sorted(Comparator.comparing(FonteDeDadosDoTitular::chave)).toList();
        this.consentimentos = consentimentos;
        this.jdbc = jdbc;
        this.clock = clock;
        this.fuso = ZoneId.of(fuso);
        this.limite = limite;
        this.janela = janela;
    }

    @Transactional
    Map<String, Object> exportar(UUID usuarioId) {
        Instant agora = clock.instant();
        conferirLimite(usuarioId, agora);

        Map<String, Object> exportacao = new LinkedHashMap<>();
        exportacao.put("geradoEm", FormatoDeData.formatar(agora, fuso));
        for (FonteDeDadosDoTitular fonte : fontes) {
            exportacao.putAll(fonte.exportar(usuarioId));
        }
        exportacao.put("consentimentos", consentimentos.historico(usuarioId).stream()
                .map(registro -> ConsentimentoResposta.de(registro, fuso)).toList());

        jdbc.sql("""
                INSERT INTO solicitacao_titular (usuario_id, tipo, solicitada_em, concluida_em)
                VALUES (:usuario, 'EXPORTACAO', :agora, :agora)
                """)
                .param("usuario", usuarioId)
                .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
                .update();
        return exportacao;
    }

    /**
     * Já houve {@code limite} exportações dentro da janela? Então o pedido espera a mais antiga delas sair da janela
     * (a janela é aberta no começo: uma hora depois da primeira, ela já não conta).
     */
    private void conferirLimite(UUID usuarioId, Instant agora) {
        trancarDaConta(usuarioId);
        List<OffsetDateTime> recentes = jdbc.sql("""
                SELECT solicitada_em FROM solicitacao_titular
                WHERE usuario_id = :usuario AND tipo = 'EXPORTACAO' AND solicitada_em > :desde
                ORDER BY solicitada_em
                """)
                .param("usuario", usuarioId)
                .param("desde", OffsetDateTime.ofInstant(agora.minus(janela), ZoneOffset.UTC))
                .query(OffsetDateTime.class).list();
        if (recentes.size() >= limite) {
            Instant libera = recentes.get(recentes.size() - limite).toInstant().plus(janela);
            throw new ExcecaoDeLimite(Math.max(1, Duration.between(agora, libera).toSeconds()));
        }
    }

    private void trancarDaConta(UUID usuarioId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:chave, 0))")
                .param("chave", "exportacao|" + usuarioId)
                .query().listOfRows();
    }
}
