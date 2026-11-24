package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.BairroDaVitrine;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Filho;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Grupo;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Nivel;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.RegiaoDaVitrine;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.SemRegiao;
import io.github.k3vinrich4rd.altashoras.cep.TabelaIbge;

/**
 * A regra do alcance por nível (Fatia 11, ADR 0014), em código PURO: não conhece Spring, banco nem relógio, para o teste de
 * propriedade rodar 200 sementes em milissegundos. Recebe pessoas (já distintas, com o lugar delas e as visitas) e devolve o que
 * pode ser publicado:
 * <ul>
 *   <li><b>k</b>: nada menor que k vira número. O lugar pequeno sobe: bairro → município → região imediata → UF → "Outros estados".</li>
 *   <li><b>E9, supressão complementar</b>: em cada grupo, o que sobra depois dos filhos nomeados é 0 ou pelo menos k; senão o menor filho
 *       nomeado é rebaixado para dentro do "Outros".</li>
 *   <li><b>E10, total</b>: o total só aparece se o que ficou escondido no topo (sem região, "Outros estados") for 0 ou pelo menos k.</li>
 *   <li><b>E11, recortes que não se subtraem</b>: uma célula só é publicada se a diferença para a mesma célula num recorte maior
 *       (todos os barbeiros; janela maior) é 0 ou pelo menos k; senão ela é tratada como menor que k e sobe.</li>
 * </ul>
 * A árvore publicada é UMA por recorte e não depende do nível pedido (nem do filtro de UF): os níveis são só projeções dela, então
 * respostas de níveis diferentes nunca se contradizem nem se subtraem. As regras E9 e E11 podem ser desligadas só em teste
 * ({@link Regras}) para provar que o teste de propriedade pega o vazamento (A44).
 */
public final class NiveisDeAlcance {

    /** Liga e desliga as duas defesas de subtração. Em produção é sempre {@link #TODAS}. */
    public record Regras(boolean complementar, boolean entreRecortes) {

        public static final Regras TODAS = new Regras(true, true);
    }

    public record Lugar(String uf, @Nullable String municipio, @Nullable String bairro) {
    }

    public record Visita(UUID barbeiro, Instant inicio) {
    }

    /** Uma pessoa (não um atendimento). {@code lugar} nulo = sem região. Nada daqui vai para log. */
    public record Pessoa(@Nullable Lugar lugar, List<Visita> visitas) {

        @Override
        public String toString() {
            return "Pessoa[omitida]";
        }
    }

    /** {@code de} inclusive e {@code ate} exclusivo. */
    public record Janela(Instant de, Instant ate) {
    }

    public record Recorte(@Nullable UUID barbeiro, Janela janela) {
    }

    /** O que se publica de um recorte: a árvore, as células numéricas e os totais do topo. */
    public static final class Publicacao {

        private final long total;
        private final long semRegiao;
        private final boolean semRegiaoNumerico;
        private final long outrosEstados;
        private final boolean outrosEstadosNumerico;
        private final boolean totalVisivel;
        private final List<NoPublicado> ufs;
        private final Map<String, Long> celulas;

        private Publicacao(long total, long semRegiao, boolean semRegiaoNumerico, long outrosEstados,
                boolean outrosEstadosNumerico, boolean totalVisivel, List<NoPublicado> ufs, Map<String, Long> celulas) {
            this.total = total;
            this.semRegiao = semRegiao;
            this.semRegiaoNumerico = semRegiaoNumerico;
            this.outrosEstados = outrosEstados;
            this.outrosEstadosNumerico = outrosEstadosNumerico;
            this.totalVisivel = totalVisivel;
            this.ufs = ufs;
            this.celulas = celulas;
        }

        public boolean totalVisivel() {
            return totalVisivel;
        }

        public long total() {
            return total;
        }
    }

    private enum Tipo {
        UF, REGIAO, MUNICIPIO, BAIRRO
    }

    private static final class No {

        final Tipo tipo;
        final String chave;
        final String codigo;
        final String nome;
        final String uf;
        long n;
        final Map<String, No> filhos = new LinkedHashMap<>();

        No(Tipo tipo, String chave, String codigo, String nome, String uf) {
            this.tipo = tipo;
            this.chave = chave;
            this.codigo = codigo;
            this.nome = nome;
            this.uf = uf;
        }
    }

    private static final class Contagem {

        final Map<String, No> ufs = new TreeMap<>();
        long total;
        long semRegiao;
    }

    private static final class NoPublicado {

        final No no;
        final List<NoPublicado> filhos;
        final long resto;

        NoPublicado(No no, List<NoPublicado> filhos, long resto) {
            this.no = no;
            this.filhos = filhos;
            this.resto = resto;
        }
    }

    private final TabelaIbge tabela;
    private final int k;
    private final Regras regras;

    public NiveisDeAlcance(TabelaIbge tabela, int k, Regras regras) {
        this.tabela = tabela;
        this.k = k;
        this.regras = regras;
    }

    public int k() {
        return k;
    }

    // ---- contagem ---------------------------------------------------------------------------------------------------

    private static boolean noRecorte(Pessoa pessoa, Recorte recorte) {
        for (Visita v : pessoa.visitas()) {
            if ((recorte.barbeiro() == null || recorte.barbeiro().equals(v.barbeiro()))
                    && !v.inicio().isBefore(recorte.janela().de()) && v.inicio().isBefore(recorte.janela().ate())) {
                return true;
            }
        }
        return false;
    }

    private Contagem contar(List<Pessoa> pessoas, Recorte recorte) {
        Contagem c = new Contagem();
        for (Pessoa pessoa : pessoas) {
            if (!noRecorte(pessoa, recorte)) {
                continue;
            }
            c.total++;
            Lugar lugar = pessoa.lugar();
            if (lugar == null) {
                c.semRegiao++;
                continue;
            }
            No uf = c.ufs.computeIfAbsent(lugar.uf(), u -> new No(Tipo.UF, "UF:" + u, u, u, u));
            uf.n++;
            TabelaIbge.Municipio m = lugar.municipio() == null ? null : tabela.porCodigo(lugar.municipio()).orElse(null);
            if (m == null || !m.uf().equals(lugar.uf())) {
                continue; // sem município: entra direto na UF (A19)
            }
            No regiao = uf.filhos.computeIfAbsent(m.regiaoCodigo(),
                    codigo -> new No(Tipo.REGIAO, "REGIAO:" + codigo, codigo, m.regiaoNome(), m.uf()));
            regiao.n++;
            No municipio = regiao.filhos.computeIfAbsent(m.codigo(),
                    codigo -> new No(Tipo.MUNICIPIO, "MUNICIPIO:" + codigo, codigo, m.nome(), m.uf()));
            municipio.n++;
            String bairro = lugar.bairro() == null ? "" : lugar.bairro().strip();
            if (!bairro.isEmpty()) {
                No no = municipio.filhos.computeIfAbsent(bairro,
                        nome -> new No(Tipo.BAIRRO, "BAIRRO:" + m.codigo() + ":" + nome, m.codigo(), nome, m.uf()));
                no.n++;
            }
        }
        return c;
    }

    /**
     * E11: com as janelas padrão, o recorte não pode se subtrair do mesmo recorte numa janela maior; o ADMIN, com filtro de
     * barbeiro, também não do mesmo recorte de todos. O barbeiro convidado só vê o dele (não há "todos" para ele). Janela livre
     * (de/ate fora das padrão) não tem referência (D-4).
     */
    public static Function<Recorte, List<Recorte>> referenciasPadrao(List<Janela> padroes, boolean admin) {
        return recorte -> {
            if (!padroes.contains(recorte.janela())) {
                return List.of();
            }
            List<Janela> janelas = new ArrayList<>();
            janelas.add(recorte.janela());
            for (Janela j : padroes) {
                if (j.de().isBefore(recorte.janela().de())) {
                    janelas.add(j);
                }
            }
            List<UUID> barbeiros = new ArrayList<>();
            barbeiros.add(recorte.barbeiro());
            if (admin && recorte.barbeiro() != null) {
                barbeiros.add(null);
            }
            List<Recorte> referencias = new ArrayList<>();
            for (UUID barbeiro : barbeiros) {
                for (Janela janela : janelas) {
                    Recorte candidato = new Recorte(barbeiro, janela);
                    if (!candidato.equals(recorte)) {
                        referencias.add(candidato);
                    }
                }
            }
            return referencias;
        };
    }

    // ---- publicação de um recorte -----------------------------------------------------------------------------------

    /**
     * A árvore publicável do recorte. {@code referencias} devolve os recortes MAIORES (que contêm este) contra os quais ele não
     * pode se subtrair (E11); o resultado deles é calculado antes, com as próprias referências.
     */
    public Publicacao publicar(List<Pessoa> pessoas, Recorte alvo, Function<Recorte, List<Recorte>> referencias) {
        return publicar(pessoas, alvo, referencias, new HashMap<>());
    }

    private Publicacao publicar(List<Pessoa> pessoas, Recorte alvo, Function<Recorte, List<Recorte>> referencias,
            Map<Recorte, Publicacao> memo) {
        Publicacao pronta = memo.get(alvo);
        if (pronta != null) {
            return pronta;
        }
        List<Publicacao> maiores = new ArrayList<>();
        if (regras.entreRecortes()) {
            for (Recorte maior : referencias.apply(alvo)) {
                maiores.add(publicar(pessoas, maior, referencias, memo));
            }
        }
        Contagem contagem = contar(pessoas, alvo);
        Set<String> forcados = new HashSet<>();
        Publicacao publicacao;
        while (true) {
            publicacao = construir(contagem, forcados);
            Set<String> violadas = violacoes(publicacao, maiores);
            if (violadas.isEmpty()) {
                break;
            }
            boolean mudou = false;
            for (String chave : violadas) {
                mudou |= forcar(chave, forcados);
            }
            if (!mudou) {
                break;
            }
        }
        memo.put(alvo, publicacao);
        return publicacao;
    }

    private boolean visivel(No no, Set<String> forcados) {
        return no.n >= k && !forcados.contains("H:" + no.chave);
    }

    private Publicacao construir(Contagem c, Set<String> forcados) {
        List<NoPublicado> ufs = new ArrayList<>();
        long outros = 0;
        for (No uf : ordenados(c.ufs.values())) {
            if (visivel(uf, forcados)) {
                ufs.add(publicar(uf, forcados));
            } else {
                outros += uf.n;
            }
        }
        boolean semRegiaoNumerico = c.semRegiao == 0 || (c.semRegiao >= k && !forcados.contains("H:SR"));
        boolean outrosNumerico = outros == 0 || (outros >= k && !forcados.contains("H:OE"));
        boolean totalVisivel = semRegiaoNumerico && outrosNumerico && !forcados.contains("H:TOTAL");
        Map<String, Long> celulas = new HashMap<>();
        for (NoPublicado uf : ufs) {
            coletar(uf, celulas);
        }
        if (c.semRegiao >= k && semRegiaoNumerico) {
            celulas.put("SR", c.semRegiao);
        }
        if (outros >= k && outrosNumerico) {
            celulas.put("OE", outros);
        }
        if (totalVisivel) {
            celulas.put("T", c.total);
        }
        return new Publicacao(c.total, c.semRegiao, semRegiaoNumerico, outros, outrosNumerico, totalVisivel, ufs, celulas);
    }

    private NoPublicado publicar(No no, Set<String> forcados) {
        if (no.tipo == Tipo.BAIRRO) {
            return new NoPublicado(no, List.of(), 0);
        }
        List<No> nomeados = new ArrayList<>();
        if (!forcados.contains("C:" + no.chave)) {
            for (No filho : no.filhos.values()) {
                if (visivel(filho, forcados)) {
                    nomeados.add(filho);
                }
            }
        }
        long resto = no.n - nomeados.stream().mapToLong(f -> f.n).sum();
        if (regras.complementar()) {
            // E9: o que sobra (o "Outros") nunca fica entre 1 e k - 1; o menor filho nomeado é rebaixado para dentro dele.
            nomeados.sort(Comparator.<No>comparingLong(f -> f.n).thenComparing(f -> f.nome));
            while (resto >= 1 && resto < k && !nomeados.isEmpty()) {
                resto += nomeados.removeFirst().n;
            }
        }
        List<NoPublicado> filhos = new ArrayList<>();
        for (No filho : ordenados(nomeados)) {
            filhos.add(publicar(filho, forcados));
        }
        return new NoPublicado(no, filhos, resto);
    }

    private static List<No> ordenados(java.util.Collection<No> nos) {
        List<No> lista = new ArrayList<>(nos);
        lista.sort(Comparator.<No>comparingLong(f -> f.n).reversed().thenComparing(f -> f.nome));
        return lista;
    }

    private void coletar(NoPublicado p, Map<String, Long> celulas) {
        celulas.put(p.no.chave, p.no.n);
        if (p.no.tipo != Tipo.BAIRRO && p.resto >= k) {
            celulas.put("O:" + p.no.chave, p.resto);
        }
        for (NoPublicado filho : p.filhos) {
            coletar(filho, celulas);
        }
    }

    /** E11: as células que, comparadas com a mesma célula de um recorte maior, deixam uma diferença entre 1 e k - 1. */
    private Set<String> violacoes(Publicacao publicacao, List<Publicacao> maiores) {
        Set<String> violadas = new HashSet<>();
        for (Publicacao maior : maiores) {
            for (Map.Entry<String, Long> celula : publicacao.celulas.entrySet()) {
                Long outra = maior.celulas.get(celula.getKey());
                if (outra != null) {
                    long diferenca = Math.abs(outra - celula.getValue());
                    if (diferenca >= 1 && diferenca < k) {
                        violadas.add(celula.getKey());
                    }
                }
            }
        }
        return violadas;
    }

    /** Esconde a célula (ou, se for um "Outros", primeiro recolhe o pai e, se persistir, esconde o pai). */
    private static boolean forcar(String chave, Set<String> forcados) {
        return switch (chave) {
            case "SR" -> forcados.add("H:SR");
            case "OE" -> forcados.add("H:OE");
            case "T" -> forcados.add("H:TOTAL");
            default -> {
                if (chave.startsWith("O:")) {
                    String pai = chave.substring(2);
                    yield forcados.add("C:" + pai) || forcados.add("H:" + pai);
                }
                yield forcados.add("H:" + chave);
            }
        };
    }

    // ---- projeção para o painel --------------------------------------------------------------------------------------

    /** O resultado do painel para um nível. {@code uf} nulo = todas. */
    public record Resultado(boolean totalVisivel, @Nullable Long clientes, SemRegiao semRegiao, List<Grupo> grupos) {
    }

    public Resultado painel(Publicacao p, Nivel nivel, @Nullable String uf) {
        List<Grupo> grupos = new ArrayList<>();
        for (NoPublicado ufPub : p.ufs) {
            if (uf != null && !uf.equals(ufPub.no.uf)) {
                continue;
            }
            switch (nivel) {
                case ESTADO -> grupos.add(linha(p, ufPub, null));
                case REGIAO -> {
                    for (NoPublicado regiao : ufPub.filhos) {
                        grupos.add(linha(p, regiao, null));
                    }
                    adicionarResto(p, grupos, ufPub, "UF");
                }
                case MUNICIPIO -> {
                    for (NoPublicado regiao : ufPub.filhos) {
                        for (NoPublicado municipio : regiao.filhos) {
                            grupos.add(linha(p, municipio, null));
                        }
                        adicionarResto(p, grupos, regiao, "REGIAO");
                    }
                    adicionarResto(p, grupos, ufPub, "UF");
                }
            }
        }
        if (uf == null && p.outrosEstados > 0) {
            boolean numerico = p.outrosEstadosNumerico && p.outrosEstados >= k;
            grupos.add(new Grupo("OUTROS", null, "Outros estados", null, numerico ? p.outrosEstados : null,
                    numerico ? percentual(p, p.outrosEstados) : null, !numerico, "PAIS", List.of()));
        }
        SemRegiao sem = p.semRegiao == 0 ? new SemRegiao(0L, false)
                : p.semRegiaoNumerico && p.semRegiao >= k ? new SemRegiao(p.semRegiao, false) : new SemRegiao(null, true);
        return new Resultado(p.totalVisivel, p.totalVisivel ? p.total : null, sem, List.copyOf(grupos));
    }

    private void adicionarResto(Publicacao p, List<Grupo> grupos, NoPublicado pai, String agrupadoEm) {
        if (pai.resto <= 0) {
            return;
        }
        boolean numerico = pai.resto >= k;
        grupos.add(new Grupo("OUTROS", pai.no.codigo, nomeDoResto(pai.no), pai.no.uf, numerico ? pai.resto : null,
                numerico ? percentual(p, pai.resto) : null, !numerico, agrupadoEm, List.of()));
    }

    private Grupo linha(Publicacao p, NoPublicado no, @Nullable String agrupadoEm) {
        List<Filho> filhos = new ArrayList<>();
        for (NoPublicado filho : no.filhos) {
            filhos.add(new Filho(filho.no.tipo.name(), filho.no.codigo, filho.no.nome, filho.no.n, percentual(p, filho.no.n),
                    false));
        }
        if (no.no.tipo != Tipo.BAIRRO && no.resto > 0) {
            boolean numerico = no.resto >= k;
            filhos.add(new Filho("OUTROS", no.no.codigo, nomeDoResto(no.no), numerico ? no.resto : null,
                    numerico ? percentual(p, no.resto) : null, !numerico));
        }
        return new Grupo(no.no.tipo.name(), no.no.codigo, no.no.nome, no.no.uf, no.no.n, percentual(p, no.no.n), false,
                agrupadoEm, List.copyOf(filhos));
    }

    private static String nomeDoResto(No pai) {
        return switch (pai.tipo) {
            case MUNICIPIO -> "Outros bairros";
            case REGIAO -> "Outros municípios da região imediata de " + pai.nome;
            case UF -> "Outras regiões de " + pai.nome;
            case BAIRRO -> throw new IllegalStateException("bairro não tem filhos");
        };
    }

    private @Nullable Double percentual(Publicacao p, long parte) {
        if (!p.totalVisivel || p.total == 0) {
            return null;
        }
        return BigDecimal.valueOf(parte * 100).divide(BigDecimal.valueOf(p.total), 1, RoundingMode.HALF_UP).doubleValue();
    }

    // ---- vitrine pública (E12) ---------------------------------------------------------------------------------------

    /** Menor município (em pessoas) que mostra faixas de porcentagem; abaixo disso, só os nomes dos bairros. */
    public static final int MUNICIPIO_MINIMO_PARA_FAIXAS = 20;

    /**
     * A vitrine: só municípios com pelo menos k pessoas e, dentro deles, os bairros com pelo menos k, em faixas de 5% para baixo
     * ("15", "&lt;5"). Município com menos de 20 pessoas: só os nomes. O "Outros bairros" só aparece com k ou mais e nunca tem faixa.
     * Nunca devolve número de pessoas.
     */
    public List<RegiaoDaVitrine> vitrine(List<Pessoa> pessoas, Janela janela) {
        Contagem c = contar(pessoas, new Recorte(null, janela));
        List<No> municipios = new ArrayList<>();
        for (No uf : c.ufs.values()) {
            for (No regiao : uf.filhos.values()) {
                for (No municipio : regiao.filhos.values()) {
                    if (municipio.n >= k) {
                        municipios.add(municipio);
                    }
                }
            }
        }
        List<RegiaoDaVitrine> resultado = new ArrayList<>();
        for (No municipio : ordenados(municipios)) {
            boolean comFaixas = municipio.n >= MUNICIPIO_MINIMO_PARA_FAIXAS;
            List<No> nomeados = new ArrayList<>(municipio.filhos.values().stream().filter(b -> b.n >= k).toList());
            long resto = municipio.n - nomeados.stream().mapToLong(b -> b.n).sum();
            List<BairroDaVitrine> bairros = new ArrayList<>();
            for (No bairro : ordenados(nomeados)) {
                bairros.add(new BairroDaVitrine(bairro.nome, comFaixas ? faixa(bairro.n, municipio.n) : null, false));
            }
            if (resto >= k) {
                bairros.add(new BairroDaVitrine("Outros bairros", null, true));
            }
            resultado.add(new RegiaoDaVitrine(municipio.nome, municipio.uf, municipio.codigo, comFaixas, List.copyOf(bairros)));
        }
        return List.copyOf(resultado);
    }

    /** A porcentagem arredondada PARA BAIXO em múltiplos de 5: "15", ou "&lt;5" abaixo de 5%. */
    static String faixa(long parte, long total) {
        long inteiro = parte * 100 / total;
        long faixa = inteiro / 5 * 5;
        return faixa < 5 ? "<5" : Long.toString(faixa);
    }
}
