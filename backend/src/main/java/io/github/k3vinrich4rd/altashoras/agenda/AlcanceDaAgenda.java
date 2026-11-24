package io.github.k3vinrich4rd.altashoras.agenda;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.alcance.ConsultaAlcance;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Janela;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Pessoa;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Publicacao;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Recorte;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * API pública da agenda para o alcance (Fatia 6C, reescrita na Fatia 11, ADR 0002 e ADR 0014): de onde vêm os CLIENTES (pessoas
 * distintas, não atendimentos), por estado, região, município e bairro. O painel (módulo admin) pergunta aqui e nunca lê a
 * tabela da reserva. Só entram reservas CONFIRMADO e CONCLUIDO (E14) e nenhum lugar com menos de k pessoas aparece com número
 * (k-anonimato, E6): ele sobe de nível (E7). O ADMIN vê todos os barbeiros ou filtra por um; o BARBEIRO vê sempre só os próprios
 * clientes: o {@code barbeiroId} que ele mandar é trocado pelo dele, sem sinal disso na resposta (E15).
 */
@Service
public class AlcanceDaAgenda {

    /** Período máximo do painel, em dias (366 cobre um ano bissexto inteiro). */
    public static final int PERIODO_MAXIMO_DIAS = 366;
    /** Padrão do painel: hoje e os 29 dias anteriores. */
    public static final int PERIODO_PADRAO_DIAS = 30;

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** O nível das linhas do painel (o seletor da tela). */
    public enum Nivel {
        ESTADO, REGIAO, MUNICIPIO
    }

    /** As três janelas padrão, as únicas que a regra E11 compara entre si. */
    public enum PeriodoPadrao {
        ULTIMOS_30_DIAS, ULTIMOS_90_DIAS, ULTIMOS_12_MESES
    }

    /** Dias civis, inclusive nas duas pontas, no fuso da barbearia. */
    public record Periodo(LocalDate de, LocalDate ate) {
    }

    /** Quem ficou sem região: {@code clientes} nulo e {@code abaixoDoLimite} quando o número não é publicado. */
    public record SemRegiao(@Nullable Long clientes, boolean abaixoDoLimite) {
    }

    /**
     * Um filho (nível de baixo) de um grupo. {@code clientes} e {@code percentual} são nulos quando {@code abaixoDoLimite}
     * (a tela escreve "menos de k"). Em "OUTROS", {@code codigo} é o do pai.
     */
    public record Filho(String tipo, @Nullable String codigo, String nome, @Nullable Long clientes,
            @Nullable Double percentual, boolean abaixoDoLimite) {
    }

    /**
     * Uma linha do nível pedido (UF, REGIAO, MUNICIPIO ou OUTROS). {@code agrupadoEm} diz até que nível um "Outros" subiu
     * (REGIAO, UF ou PAIS) e é nulo quando a linha ficou no nível pedido.
     */
    public record Grupo(String tipo, @Nullable String codigo, String nome, @Nullable String uf, @Nullable Long clientes,
            @Nullable Double percentual, boolean abaixoDoLimite, @Nullable String agrupadoEm, List<Filho> filhos) {
    }

    /** Deprecated (próxima major): o bairro como o painel antigo mostrava, agora contando clientes. */
    public record Bairro(String bairro, @Nullable Long atendimentos, @Nullable Double percentual, boolean abaixoDoLimite) {
    }

    /** Deprecated (próxima major): a cidade como o painel antigo mostrava; só vem preenchida com {@code nivel = MUNICIPIO}. */
    public record Cidade(String cidade, @Nullable String uf, @Nullable Long atendimentos, @Nullable Double percentual,
            boolean abaixoDoLimite, List<Bairro> bairros) {
    }

    public record Painel(Periodo periodo, Nivel nivel, int k, boolean totalVisivel, @Nullable Long clientes,
            SemRegiao semRegiao, List<Grupo> grupos, @Nullable Long total, List<Cidade> cidades) {
    }

    /** Um bairro da vitrine: só o nome e a faixa de porcentagem ({@code "15"} ou {@code "<5"}); nunca número de pessoas. */
    public record BairroDaVitrine(String bairro, @Nullable String faixaPercentual, boolean outros) {
    }

    /**
     * Um município da vitrine. {@code bairros} (só nomes) é o formato antigo, deprecated; {@code bairrosComFaixa} traz a faixa.
     * {@code comFaixas} é falso em município com menos de 20 pessoas (só nomes).
     */
    public record RegiaoDaVitrine(String cidade, String uf, String codigoIbge, boolean comFaixas, List<String> bairros,
            List<BairroDaVitrine> bairrosComFaixa) {

        public RegiaoDaVitrine(String cidade, String uf, String codigoIbge, boolean comFaixas,
                List<BairroDaVitrine> bairrosComFaixa) {
            this(cidade, uf, codigoIbge, comFaixas, bairrosComFaixa.stream().filter(b -> !b.outros())
                    .map(BairroDaVitrine::bairro).toList(), bairrosComFaixa);
        }
    }

    public record Publico(String atualizadoEm, int totalCidades, int totalBairros, List<RegiaoDaVitrine> regioes) {
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
     * O alcance do período. Com {@code de} ou {@code ate}, é uma janela livre (D108: sem a proteção entre janelas, D-4); sem
     * os dois, vale {@code periodo} (padrão: 30 dias). Período invertido ou com mais de 366 dias: 400. Barbeiro inexistente,
     * ou BARBEIRO sem vínculo com uma conta: 404 (R23).
     */
    @Transactional(readOnly = true)
    public Painel painel(PainelDaAgenda.Escopo escopo, @Nullable LocalDate de, @Nullable LocalDate ate,
            @Nullable UUID barbeiroId, @Nullable Nivel nivel, @Nullable String uf, @Nullable PeriodoPadrao periodo) {
        ZoneId fuso = fuso();
        LocalDate hoje = LocalDate.ofInstant(clock.instant(), fuso);
        LocalDate fim;
        LocalDate inicio;
        if (de != null || ate != null) {
            fim = ate != null ? ate : hoje;
            inicio = de != null ? de : fim.minusDays(PERIODO_PADRAO_DIAS - 1L);
            if (inicio.isAfter(fim)) {
                throw ExcecaoDeNegocio.campoInvalido("de", "A data inicial não pode ser depois da final.");
            }
            if (inicio.plusDays(PERIODO_MAXIMO_DIAS).isBefore(fim.plusDays(1))) {
                throw ExcecaoDeNegocio.campoInvalido("ate", "O período pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");
            }
        } else {
            fim = hoje;
            inicio = inicioDe(periodo == null ? PeriodoPadrao.ULTIMOS_30_DIAS : periodo, hoje);
        }
        Nivel nivelEscolhido = nivel == null ? Nivel.MUNICIPIO : nivel;
        UUID alvo = acesso.alvoDoAlcance(escopo, barbeiroId);

        List<Pessoa> pessoas = consulta.pessoas(janela(inicio.isBefore(hoje.minusMonths(12)) ? inicio : hoje.minusMonths(12), fim, fuso).de()
                .minus(java.time.Duration.ofDays(31)));
        Janela janela = janela(inicio, fim, fuso);
        List<Janela> padroes = padroes(hoje, fuso);
        boolean admin = escopo.admin();
        Function<Recorte, List<Recorte>> referencias = NiveisDeAlcance.referenciasPadrao(padroes, admin);
        NiveisDeAlcance niveis = consulta.niveis();
        Publicacao publicacao = niveis.publicar(pessoas, new Recorte(alvo, janela), referencias);
        NiveisDeAlcance.Resultado resultado = niveis.painel(publicacao, nivelEscolhido, uf == null || uf.isBlank() ? null
                : uf.strip().toUpperCase(java.util.Locale.ROOT));
        return new Painel(new Periodo(inicio, fim), nivelEscolhido, niveis.k(), resultado.totalVisivel(), resultado.clientes(),
                resultado.semRegiao(), resultado.grupos(), resultado.clientes(),
                nivelEscolhido == Nivel.MUNICIPIO ? cidadesAntigas(resultado.grupos()) : List.of());
    }

    /** A vitrine pública: os últimos 12 meses de todos os barbeiros, só nomes e faixas. Sem cache aqui (E35). */
    @Transactional(readOnly = true)
    public Publico publico() {
        ZoneId fuso = fuso();
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        Janela janela = janela(inicioDe(PeriodoPadrao.ULTIMOS_12_MESES, hoje), hoje, fuso);
        List<RegiaoDaVitrine> regioes = consulta.niveis().vitrine(consulta.pessoas(janela.de().minus(java.time.Duration.ofDays(31))), janela);
        int bairros = regioes.stream().mapToInt(r -> r.bairros().size()).sum();
        return new Publico(FORMATO.format(agora.atZone(fuso)), regioes.size(), bairros, regioes);
    }

    private static LocalDate inicioDe(PeriodoPadrao periodo, LocalDate hoje) {
        return switch (periodo) {
            case ULTIMOS_30_DIAS -> hoje.minusDays(29);
            case ULTIMOS_90_DIAS -> hoje.minusDays(89);
            case ULTIMOS_12_MESES -> hoje.minusMonths(12);
        };
    }

    private static Janela janela(LocalDate inicio, LocalDate fim, ZoneId fuso) {
        return new Janela(inicio.atStartOfDay(fuso).toInstant(), fim.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    private static List<Janela> padroes(LocalDate hoje, ZoneId fuso) {
        List<Janela> janelas = new ArrayList<>();
        for (PeriodoPadrao p : PeriodoPadrao.values()) {
            janelas.add(janela(inicioDe(p, hoje), hoje, fuso));
        }
        return janelas;
    }

    /** O formato antigo (deprecated), preenchido com {@code nivel = MUNICIPIO}: agora cada número conta clientes. */
    private static List<Cidade> cidadesAntigas(List<Grupo> grupos) {
        List<Cidade> cidades = new ArrayList<>();
        for (Grupo g : grupos) {
            List<Bairro> bairros = g.filhos().stream()
                    .map(f -> new Bairro(f.nome(), f.clientes(), f.percentual(), f.abaixoDoLimite())).toList();
            cidades.add(new Cidade(g.nome(), g.uf(), g.clientes(), g.percentual(), g.abaixoDoLimite(), bairros));
        }
        return cidades;
    }

    private ZoneId fuso() {
        return ZoneId.of(configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow().getFuso());
    }
}
