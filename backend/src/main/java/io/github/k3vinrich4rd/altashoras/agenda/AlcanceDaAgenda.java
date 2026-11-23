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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.alcance.ConsultaAlcance;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o alcance por região (Fatia 6C, ADR 0002): de onde vêm os atendimentos, por cidade e bairro.
 * O painel (módulo admin) pergunta aqui e nunca lê a tabela da reserva. Só entram reservas CONFIRMADO e CONCLUIDO (E31) e
 * nenhum grupo com menos de {@value #K_MINIMO} atendimentos aparece com nome (k-anonimato, E32).
 * O ADMIN vê todos os barbeiros ou filtra por um; o BARBEIRO vê sempre só os próprios atendimentos: o {@code barbeiroId}
 * que ele mandar é trocado pelo dele (E36).
 */
@Service
public class AlcanceDaAgenda {

    /** Menor grupo que aparece com nome: abaixo disso entra em "Outros bairros" ou "Outras cidades" (E32). */
    public static final int K_MINIMO = 5;
    /** Período máximo do painel, em dias (366 cobre um ano bissexto inteiro). */
    public static final int PERIODO_MAXIMO_DIAS = 366;
    /** Padrão do painel: hoje e os 29 dias anteriores. */
    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final String OUTRAS_CIDADES = "Outras cidades";
    public static final String OUTROS_BAIRROS = "Outros bairros";

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** Dias civis, inclusive nas duas pontas, no fuso da barbearia. */
    public record Periodo(LocalDate de, LocalDate ate) {
    }

    /** Abaixo do limite ("Outros bairros" com menos de 5): {@code atendimentos} e {@code percentual} nulos e {@code abaixoDoLimite} verdadeiro (D114). */
    public record Bairro(String bairro, @Nullable Long atendimentos, @Nullable Double percentual, boolean abaixoDoLimite) {
    }

    /** "Outras cidades" tem {@code uf} nula e nenhum bairro. O percentual é sobre o total do período (inclui o sem região). */
    public record Cidade(String cidade, @Nullable String uf, @Nullable Long atendimentos, @Nullable Double percentual,
            boolean abaixoDoLimite, List<Bairro> bairros) {
    }

    public record Painel(Periodo periodo, long total, long semRegiao, List<Cidade> cidades) {
    }

    /** A vitrine pública: só nomes. */
    public record Regiao(String cidade, String uf, List<String> bairros) {
    }

    public record Publico(String atualizadoEm, int totalCidades, int totalBairros, List<Regiao> regioes) {
    }

    private final ConsultaAlcance consulta;
    private final ConfiguracaoAgendaRepository configuracoes;
    private final AcessoAgenda acesso;
    private final Clock clock;

    AlcanceDaAgenda(ConsultaAlcance consulta, ConfiguracaoAgendaRepository configuracoes, AcessoAgenda acesso, Clock clock) {
        this.consulta = consulta;
        this.configuracoes = configuracoes;
        this.acesso = acesso;
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
        UUID alvo = acesso.alvoDoAlcance(escopo, barbeiroId);
        return consulta.painel(new Periodo(inicio, fim), utc(inicio.atStartOfDay(fuso).toInstant()),
                utc(fim.plusDays(1).atStartOfDay(fuso).toInstant()), alvo);
    }

    /** A vitrine pública: os últimos 12 meses de todos os barbeiros, só nomes de quem passa no k ≥ 5. Sem cache aqui (E35). */
    @Transactional(readOnly = true)
    public Publico publico() {
        ZoneId fuso = fuso();
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        Painel painel = consulta.painel(new Periodo(hoje.minusMonths(12), hoje),
                utc(hoje.minusMonths(12).atStartOfDay(fuso).toInstant()),
                utc(hoje.plusDays(1).atStartOfDay(fuso).toInstant()), null);
        List<Regiao> regioes = painel.cidades().stream()
                .filter(c -> c.uf() != null)
                .map(c -> new Regiao(c.cidade(), c.uf(), c.bairros().stream().map(Bairro::bairro)
                        .filter(nome -> !OUTROS_BAIRROS.equals(nome)).toList()))
                .toList();
        int bairros = regioes.stream().mapToInt(r -> r.bairros().size()).sum();
        return new Publico(FORMATO.format(agora.atZone(fuso)), regioes.size(), bairros, regioes);
    }

    private ZoneId fuso() {
        return ZoneId.of(configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow().getFuso());
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

}
