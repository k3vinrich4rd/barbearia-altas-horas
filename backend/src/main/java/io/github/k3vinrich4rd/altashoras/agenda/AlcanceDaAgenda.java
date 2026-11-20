package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.alcance.ConsultaAlcance;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o alcance por região (Fatia 6C, ADR 0002): de onde vêm os atendimentos, por cidade e bairro.
 * O painel (módulo admin) pergunta aqui e nunca lê a tabela da reserva. Só entram reservas CONFIRMADO e CONCLUIDO (E31) e
 * toda cidade e todo bairro aparece, com a quantidade exata (D149: sem limite mínimo, risco aceito pelo Kevin).
 * O ADMIN vê todos os barbeiros ou filtra por um; o BARBEIRO vê sempre só os próprios atendimentos: o {@code barbeiroId}
 * que ele mandar é trocado pelo dele (E36).
 */
@Service
public class AlcanceDaAgenda {

    /** Período máximo do painel, em dias (366 cobre um ano bissexto inteiro). */
    public static final int PERIODO_MAXIMO_DIAS = 366;
    /** Padrão do painel: hoje e os 29 dias anteriores. */
    public static final int PERIODO_PADRAO_DIAS = 30;

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** Dias civis, inclusive nas duas pontas, no fuso da barbearia. */
    public record Periodo(LocalDate de, LocalDate ate) {
    }

    /** Um bairro do painel: o número exato e o percentual sobre o total do período. */
    public record Bairro(String bairro, long atendimentos, double percentual) {
    }

    /** O percentual é sobre o total do período (inclui o sem região). */
    public record Cidade(String cidade, String uf, long atendimentos, double percentual, List<Bairro> bairros) {
    }

    public record Painel(Periodo periodo, long total, long semRegiao, List<Cidade> cidades) {
    }

    /** Um bairro da vitrine pública: o nome e a quantidade de atendimentos. */
    public record BairroPublico(String bairro, long atendimentos) {
    }

    /** A vitrine pública: nome e quantidade de atendimentos por cidade e por bairro, do maior para o menor. */
    public record Regiao(String cidade, String uf, long atendimentos, List<BairroPublico> bairros) {
    }

    public record Publico(String atualizadoEm, int totalCidades, int totalBairros, List<Regiao> regioes) {
    }

    private final ConsultaAlcance consulta;
    private final ConfiguracaoAgendaRepository configuracoes;
    private final JdbcClient jdbc;
    private final Clock clock;

    AlcanceDaAgenda(ConsultaAlcance consulta, ConfiguracaoAgendaRepository configuracoes, JdbcClient jdbc, Clock clock) {
        this.consulta = consulta;
        this.configuracoes = configuracoes;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * O alcance do período. Sem {@code ate}, é hoje; sem {@code de}, são 29 dias antes de {@code ate}. Período invertido ou
     * com mais de 366 dias: 400. Barbeiro inexistente, ou BARBEIRO sem vínculo com uma conta: 404 (R23).
     */
    @Transactional(readOnly = true)
    public Painel painel(PainelDaAgenda.Escopo escopo, @Nullable LocalDate de, @Nullable LocalDate ate,
            @Nullable UUID barbeiroId) {
        ZoneId fuso = fuso();
        LocalDate fim = ate != null ? ate : LocalDate.ofInstant(clock.instant(), fuso);
        LocalDate inicio = de != null ? de : fim.minusDays(PERIODO_PADRAO_DIAS - 1L);
        if (inicio.isAfter(fim)) {
            throw ExcecaoDeNegocio.campoInvalido("de", "A data inicial não pode ser depois da final.");
        }
        if (inicio.plusDays(PERIODO_MAXIMO_DIAS).isBefore(fim.plusDays(1))) {
            throw ExcecaoDeNegocio.campoInvalido("ate", "O período pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        UUID alvo = barbeiroDoPedido(escopo, barbeiroId);
        return consulta.painel(new Periodo(inicio, fim), utc(inicio.atStartOfDay(fuso).toInstant()),
                utc(fim.plusDays(1).atStartOfDay(fuso).toInstant()), alvo);
    }

    /** A vitrine pública: os últimos 12 meses de todos os barbeiros, nome e quantidade de cada cidade e bairro, sem limite mínimo (D149). Sem cache aqui (E35). */
    @Transactional(readOnly = true)
    public Publico publico() {
        ZoneId fuso = fuso();
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        Painel painel = consulta.painel(new Periodo(hoje.minusMonths(12), hoje),
                utc(hoje.minusMonths(12).atStartOfDay(fuso).toInstant()),
                utc(hoje.plusDays(1).atStartOfDay(fuso).toInstant()), null);
        List<Regiao> regioes = painel.cidades().stream()
                .map(c -> new Regiao(c.cidade(), c.uf(), c.atendimentos(),
                        c.bairros().stream().map(b -> new BairroPublico(b.bairro(), b.atendimentos())).toList()))
                .toList();
        int bairros = regioes.stream().mapToInt(r -> r.bairros().size()).sum();
        return new Publico(FORMATO.format(agora.atZone(fuso)), regioes.size(), bairros, regioes);
    }

    /**
     * ADMIN vê todos ou o barbeiro filtrado (inexistente é 404). BARBEIRO vê sempre o próprio, seja qual for o que mandou
     * (E36); sem vínculo com um barbeiro, 404.
     */
    private @Nullable UUID barbeiroDoPedido(PainelDaAgenda.Escopo escopo, @Nullable UUID barbeiroId) {
        if (escopo.admin()) {
            if (barbeiroId == null) {
                return null;
            }
            boolean existe = jdbc.sql("SELECT count(*) FROM barbeiro WHERE id = :id").param("id", barbeiroId)
                    .query(Long.class).single() > 0;
            if (!existe) {
                throw barbeiroNaoEncontrado();
            }
            return barbeiroId;
        }
        return jdbc.sql("SELECT id FROM barbeiro WHERE usuario_id = :usuario").param("usuario", escopo.usuarioId())
                .query(UUID.class).optional().orElseThrow(AlcanceDaAgenda::barbeiroNaoEncontrado);
    }

    private ZoneId fuso() {
        return ZoneId.of(configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow().getFuso());
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static ExcecaoDeNegocio barbeiroNaoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado.");
    }
}
