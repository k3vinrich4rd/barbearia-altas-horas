package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.BairroDaVitrine;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Filho;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Grupo;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.Nivel;
import io.github.k3vinrich4rd.altashoras.agenda.AlcanceDaAgenda.RegiaoDaVitrine;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Janela;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Lugar;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Pessoa;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Publicacao;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Recorte;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Regras;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Visita;
import io.github.k3vinrich4rd.altashoras.cep.TabelaIbge;

/**
 * O teste de PROPRIEDADE do alcance (Fatia 11, A43 e A44, ADR 0014, critério 6): para dados aleatórios, nenhuma combinação das
 * respostas do painel e da vitrine deixa calcular uma contagem positiva menor que k. 200 sementes FIXAS (sem biblioteca nova,
 * D-7), com k = 3, 5 e 8; a tabela do IBGE é uma versão pequena de teste. Cada semente gera pessoas (conta e visitante, de 1 a 3
 * reservas, de 1 a 4 barbeiros, algumas sem região, algumas sem município, algumas que revogaram) e junta TODAS as respostas
 * possíveis: nível × UF × barbeiro (todos e cada um) × as três janelas padrão.
 * <ul>
 *   <li>P1: nenhum número publicado é positivo e menor que k;</li>
 *   <li>P2: grupo menos a soma dos filhos nomeados, mesmo recorte entre níveis diferentes, e recorte maior menos recorte menor
 *       (todos × um barbeiro, janela maior × menor): a diferença é 0 ou pelo menos k;</li>
 *   <li>P3: com o total visível, o que sobra no topo é 0;</li>
 *   <li>P4: a vitrine não traz número de pessoas, só faixas em múltiplos de 5, e o resto escondido do município não fica
 *       determinado como um único valor positivo menor que k.</li>
 * </ul>
 * O A44 desliga a supressão complementar (E9) e a regra entre recortes (E11) e prova que este mesmo teste FALHA.
 */
class AlcancePropriedadeTest {

    private static final TabelaIbge TABELA = TabelaIbge.doClasspath("ibge/municipios-teste.csv");
    private static final String[] MUNICIPIOS = {"3534401", "3510609", "3550308", "3505708", "3547304", "3509502", "3556206",
            "3556701", "3304557", "3303302", "3106200", "3118601"};
    private static final String[] BAIRROS = {"Centro", "Vila Nova", "Jardim Alto", "Presidente Altino", "Bela Vista", "Parque Sul"};
    private static final Instant FIM = Instant.parse("2026-11-18T03:00:00Z");
    private static final List<Janela> JANELAS = List.of(new Janela(FIM.minus(Duration.ofDays(30)), FIM),
            new Janela(FIM.minus(Duration.ofDays(90)), FIM), new Janela(FIM.minus(Duration.ofDays(365)), FIM));
    private static final int SEMENTES = 200;

    // ---- o teste ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A43: com 200 sementes fixas e k = 3, 5 e 8, nenhuma combinação das respostas do painel e da vitrine deixa calcular uma contagem positiva menor que k")
    void nenhumaSubtracaoRevelaMenosQueK() {
        for (int k : new int[] {3, 5, 8}) {
            List<String> violacoes = new ArrayList<>();
            for (int semente = 1; semente <= SEMENTES; semente++) {
                violacoes.addAll(violacoes(semente, k, Regras.TODAS));
            }
            assertThat(violacoes).as("violações com k = " + k).isEmpty();
        }
    }

    @Test
    @DisplayName("A44: com a supressão complementar (E9) e a regra entre recortes (E11) DESLIGADAS, o mesmo teste falha: prova que ele pega o vazamento por subtração")
    void semAsDefesasOTesteFalha() {
        int semDefesas = 0;
        int semComplementar = 0;
        int semEntreRecortes = 0;
        for (int semente = 1; semente <= SEMENTES; semente++) {
            semDefesas += violacoes(semente, 5, new Regras(false, false)).size();
            semComplementar += violacoes(semente, 5, new Regras(false, true)).size();
            semEntreRecortes += violacoes(semente, 5, new Regras(true, false)).size();
        }
        assertThat(semDefesas).as("sem nenhuma defesa").isPositive();
        assertThat(semComplementar).as("sem a supressão complementar (E9)").isPositive();
        assertThat(semEntreRecortes).as("sem a regra entre recortes (E11)").isPositive();
    }

    // ---- as respostas de uma semente ---------------------------------------------------------------------------------

    private record Resposta(int barbeiro, int janela, Nivel nivel, String uf, NiveisDeAlcance.Resultado resultado) {
    }

    private List<String> violacoes(int semente, int k, Regras regras) {
        Random sorteio = new Random(semente * 7919L + k);
        List<UUID> barbeiros = new ArrayList<>();
        int quantosBarbeiros = 1 + sorteio.nextInt(4);
        for (int i = 0; i < quantosBarbeiros; i++) {
            barbeiros.add(new UUID(sorteio.nextLong(), sorteio.nextLong()));
        }
        List<Pessoa> pessoas = pessoasAleatorias(sorteio, barbeiros);
        NiveisDeAlcance niveis = new NiveisDeAlcance(TABELA, k, regras);
        Function<Recorte, List<Recorte>> referencias = NiveisDeAlcance.referenciasPadrao(JANELAS, true);

        List<Resposta> respostas = new ArrayList<>();
        for (int b = -1; b < barbeiros.size(); b++) { // -1 = todos os barbeiros
            for (int j = 0; j < JANELAS.size(); j++) {
                Publicacao publicacao = niveis.publicar(pessoas, new Recorte(b < 0 ? null : barbeiros.get(b), JANELAS.get(j)),
                        referencias);
                for (Nivel nivel : Nivel.values()) {
                    for (String uf : new String[] {null, "SP", "RJ", "MG"}) {
                        respostas.add(new Resposta(b, j, nivel, uf, niveis.painel(publicacao, nivel, uf)));
                    }
                }
            }
        }
        List<String> achados = new ArrayList<>();
        String rotulo = "semente " + semente + " k " + k + ": ";
        verificarP1P2P3(respostas, k, rotulo, achados);
        verificarP4(pessoas, niveis, k, rotulo, achados);
        return achados;
    }

    private static List<Pessoa> pessoasAleatorias(Random sorteio, List<UUID> barbeiros) {
        int quantas = 25 + sorteio.nextInt(120);
        List<Pessoa> pessoas = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            Lugar lugar;
            double sorte = sorteio.nextDouble();
            if (sorte < 0.08) {
                lugar = null; // nunca informou, ou revogou
            } else {
                int indice = (int) Math.floor(MUNICIPIOS.length * Math.pow(sorteio.nextDouble(), 2));
                String municipio = MUNICIPIOS[indice];
                String uf = TABELA.porCodigo(municipio).orElseThrow().uf();
                if (sorte < 0.18) {
                    lugar = new Lugar(uf, null, sorteio.nextBoolean() ? BAIRROS[sorteio.nextInt(BAIRROS.length)] : null);
                } else {
                    int b = (int) Math.floor(BAIRROS.length * Math.pow(sorteio.nextDouble(), 1.6));
                    lugar = new Lugar(uf, municipio, sorteio.nextDouble() < 0.15 ? null : BAIRROS[b]);
                }
            }
            int reservas = 1 + sorteio.nextInt(3);
            List<Visita> visitas = new ArrayList<>();
            for (int r = 0; r < reservas; r++) {
                long dias = (long) (Math.pow(sorteio.nextDouble(), 1.7) * 364);
                visitas.add(new Visita(barbeiros.get(sorteio.nextInt(barbeiros.size())), FIM.minus(Duration.ofDays(dias + 1))));
            }
            pessoas.add(new Pessoa(lugar, visitas));
        }
        return pessoas;
    }

    // ---- P1, P2 e P3 -------------------------------------------------------------------------------------------------

    private static void verificarP1P2P3(List<Resposta> respostas, int k, String rotulo, List<String> achados) {
        Map<String, Map<String, Long>> valoresPorRecorte = new HashMap<>();
        Map<String, Map<String, Set<String>>> filhosPorRecorte = new HashMap<>();
        for (Resposta r : respostas) {
            String recorte = r.barbeiro() + "|" + r.janela();
            Map<String, Long> valores = valoresPorRecorte.computeIfAbsent(recorte, x -> new HashMap<>());
            Map<String, Set<String>> filhos = filhosPorRecorte.computeIfAbsent(recorte, x -> new HashMap<>());
            NiveisDeAlcance.Resultado res = r.resultado();

            // P1: nenhum número publicado é positivo e menor que k; escondido = nulo + abaixoDoLimite
            publicado(res.clientes(), k, rotulo + "total", achados);
            publicado(res.semRegiao().clientes(), k, rotulo + "semRegiao", achados);
            if (res.semRegiao().clientes() == null && !res.semRegiao().abaixoDoLimite()) {
                achados.add(rotulo + "semRegiao sem número e sem abaixoDoLimite");
            }
            if (res.totalVisivel() != (res.clientes() != null)) {
                achados.add(rotulo + "totalVisivel não bate com o total");
            }
            if (res.clientes() != null) {
                valores.merge("T", res.clientes(), (a, b) -> conflito(a, b, rotulo + "T", achados));
            }
            if (res.semRegiao().clientes() != null && res.semRegiao().clientes() > 0) {
                valores.merge("SR", res.semRegiao().clientes(), (a, b) -> conflito(a, b, rotulo + "SR", achados));
            }
            long somaDoTopo = 0;
            for (Grupo g : res.grupos()) {
                String chaveDoGrupo = chave(g);
                if (g.abaixoDoLimite() != (g.clientes() == null) || (g.clientes() == null && g.percentual() != null)) {
                    achados.add(rotulo + "grupo com abaixoDoLimite incoerente: " + chaveDoGrupo);
                }
                if (g.percentual() != null && (g.percentual().isNaN() || g.percentual() < 0 || g.percentual() > 100)) {
                    achados.add(rotulo + "percentual inválido em " + chaveDoGrupo);
                }
                publicado(g.clientes(), k, rotulo + chaveDoGrupo, achados);
                if (g.clientes() != null) {
                    valores.merge(chaveDoGrupo, g.clientes(), (a, b) -> conflito(a, b, rotulo + chaveDoGrupo, achados));
                    if (r.nivel() == Nivel.ESTADO && r.uf() == null) {
                        somaDoTopo += g.clientes();
                    }
                }
                long nomeados = 0;
                for (Filho f : g.filhos()) {
                    String chaveDoFilho = chave(g, f);
                    publicado(f.clientes(), k, rotulo + chaveDoFilho, achados);
                    if (f.abaixoDoLimite() != (f.clientes() == null)) {
                        achados.add(rotulo + "filho com abaixoDoLimite incoerente: " + chaveDoFilho);
                    }
                    if (f.clientes() != null) {
                        valores.merge(chaveDoFilho, f.clientes(), (a, b) -> conflito(a, b, rotulo + chaveDoFilho, achados));
                    }
                    if (!"OUTROS".equals(f.tipo())) {
                        filhos.computeIfAbsent(chaveDoGrupo, x -> new TreeSet<>()).add(chaveDoFilho);
                        if (f.clientes() != null) {
                            nomeados += f.clientes();
                        }
                    }
                }
                // P2 (dentro da resposta): o grupo menos os filhos nomeados é 0 ou pelo menos k
                if (g.clientes() != null && !"OUTROS".equals(g.tipo())) {
                    long resto = g.clientes() - nomeados;
                    if (resto != 0 && resto < k) {
                        achados.add(rotulo + "P2: " + chaveDoGrupo + " menos os filhos nomeados dá " + resto);
                    }
                }
            }
            // P3: com o total visível, o que sobra no topo (nível ESTADO, todas as UFs) é 0
            if (res.totalVisivel() && r.nivel() == Nivel.ESTADO && r.uf() == null) {
                long resto = res.clientes() - somaDoTopo - (res.semRegiao().clientes() == null ? 0 : res.semRegiao().clientes());
                if (resto != 0 && resto < k) {
                    achados.add(rotulo + "P3: o total menos o que aparece dá " + resto);
                }
            }
        }

        // P2 (entre níveis, mesmo recorte): o pai menos os filhos nomeados que aparecem em QUALQUER resposta do recorte
        for (Map.Entry<String, Map<String, Set<String>>> recorte : filhosPorRecorte.entrySet()) {
            Map<String, Long> valores = valoresPorRecorte.get(recorte.getKey());
            for (Map.Entry<String, Set<String>> pai : recorte.getValue().entrySet()) {
                Long valorDoPai = valores.get(pai.getKey());
                if (valorDoPai == null) {
                    continue;
                }
                long soma = 0;
                for (String filho : pai.getValue()) {
                    soma += valores.getOrDefault(filho, 0L);
                }
                long resto = valorDoPai - soma;
                if (resto != 0 && resto < k) {
                    achados.add(rotulo + "P2 entre níveis: " + pai.getKey() + " no recorte " + recorte.getKey() + " dá " + resto);
                }
            }
        }

        // P2 (entre recortes): para todo par "recorte menor dentro do maior", a diferença da mesma célula é 0 ou pelo menos k
        for (Map.Entry<String, Map<String, Long>> menor : valoresPorRecorte.entrySet()) {
            for (Map.Entry<String, Map<String, Long>> maior : valoresPorRecorte.entrySet()) {
                if (!contido(menor.getKey(), maior.getKey())) {
                    continue;
                }
                for (Map.Entry<String, Long> celula : menor.getValue().entrySet()) {
                    Long outra = maior.getValue().get(celula.getKey());
                    if (outra != null) {
                        long diferenca = Math.abs(outra - celula.getValue());
                        if (diferenca != 0 && diferenca < k) {
                            achados.add(rotulo + "P2 entre recortes: " + celula.getKey() + " em " + menor.getKey() + " e "
                                    + maior.getKey() + " difere por " + diferenca);
                        }
                    }
                }
            }
        }
    }

    /** O recorte "b|j" menor está dentro do maior: mesmo barbeiro ou o maior é "todos", e a janela é igual ou menor. */
    private static boolean contido(String menor, String maior) {
        if (menor.equals(maior)) {
            return false;
        }
        String[] a = menor.split("\\|");
        String[] b = maior.split("\\|");
        boolean barbeiroOk = a[0].equals(b[0]) || b[0].equals("-1");
        return barbeiroOk && Integer.parseInt(a[1]) <= Integer.parseInt(b[1]);
    }

    private static long conflito(long a, long b, String chave, List<String> achados) {
        if (a != b) {
            achados.add("mesma célula com valores diferentes no mesmo recorte: " + chave + " " + a + " e " + b);
        }
        return a;
    }

    private static void publicado(Long valor, int k, String onde, List<String> achados) {
        if (valor != null && valor > 0 && valor < k) {
            achados.add("P1: " + onde + " publicou " + valor);
        }
        if (valor != null && valor < 0) {
            achados.add("P1: " + onde + " publicou negativo");
        }
    }

    private static String chave(Grupo g) {
        return switch (g.tipo()) {
            case "OUTROS" -> "OUTROS|" + (g.codigo() == null ? "" : g.codigo()) + "|" + g.nome();
            default -> g.tipo() + "|" + g.codigo();
        };
    }

    private static String chave(Grupo pai, Filho f) {
        return switch (f.tipo()) {
            case "BAIRRO" -> "BAIRRO|" + pai.codigo() + "|" + f.nome();
            case "OUTROS" -> "OUTROS|" + f.codigo() + "|" + f.nome();
            default -> f.tipo() + "|" + f.codigo();
        };
    }

    // ---- P4: a vitrine -------------------------------------------------------------------------------------------------

    private static void verificarP4(List<Pessoa> pessoas, NiveisDeAlcance niveis, int k, String rotulo, List<String> achados) {
        Janela doze = JANELAS.get(2);
        List<RegiaoDaVitrine> vitrine = niveis.vitrine(pessoas, doze);
        // Nenhum campo é número de pessoas: só texto, booleano e listas
        for (Class<?> tipo : new Class<?>[] {RegiaoDaVitrine.class, BairroDaVitrine.class}) {
            for (var componente : tipo.getRecordComponents()) {
                Class<?> classe = componente.getType();
                if (!(classe == String.class || classe == boolean.class || classe == List.class)) {
                    achados.add(rotulo + "P4: campo numérico na vitrine: " + componente.getName());
                }
            }
        }
        for (RegiaoDaVitrine municipio : vitrine) {
            int n = pessoasNoMunicipio(pessoas, municipio.codigoIbge(), doze);
            if (n < k) {
                achados.add(rotulo + "P4: município da vitrine com menos de k pessoas");
            }
            if (municipio.comFaixas() != (n >= NiveisDeAlcance.MUNICIPIO_MINIMO_PARA_FAIXAS)) {
                achados.add(rotulo + "P4: faixas aparecem fora da regra dos 20");
            }
            List<Integer> inferiores = new ArrayList<>();
            List<Integer> superiores = new ArrayList<>();
            boolean temOutros = false;
            for (BairroDaVitrine bairro : municipio.bairrosComFaixa()) {
                if (bairro.outros()) {
                    temOutros = true;
                    if (bairro.faixaPercentual() != null) {
                        achados.add(rotulo + "P4: 'Outros bairros' com faixa");
                    }
                    continue;
                }
                int nb = pessoasNoBairro(pessoas, municipio.codigoIbge(), bairro.bairro(), doze);
                if (nb < k) {
                    achados.add(rotulo + "P4: bairro da vitrine com menos de k pessoas");
                }
                if (!municipio.comFaixas()) {
                    if (bairro.faixaPercentual() != null) {
                        achados.add(rotulo + "P4: faixa em município com menos de 20");
                    }
                    continue;
                }
                String faixa = bairro.faixaPercentual();
                if (faixa == null) {
                    achados.add(rotulo + "P4: bairro sem faixa em município com faixas");
                    continue;
                }
                int piso = faixa.equals("<5") ? 0 : Integer.parseInt(faixa);
                if (!faixa.equals("<5") && piso % 5 != 0) {
                    achados.add(rotulo + "P4: faixa que não é múltiplo de 5");
                }
                if (nb * 100 / n / 5 * 5 != piso && !(faixa.equals("<5") && nb * 100 / n < 5)) {
                    achados.add(rotulo + "P4: faixa não é a porcentagem arredondada para baixo");
                }
                inferiores.add(piso);
                superiores.add(faixa.equals("<5") ? 5 : piso + 5);
            }
            if (municipio.comFaixas() && !restoIndeterminado(n, inferiores, superiores, temOutros, k)) {
                achados.add(rotulo + "P4: o resto escondido do município ficou determinado como positivo e menor que k");
            }
        }
    }

    /**
     * Quem só vê as faixas (e sabe que o município tem 20 ou mais) não consegue fixar o resto escondido num único valor
     * positivo menor que k: percorre os totais T possíveis e junta os restos que cabem nas faixas.
     */
    private static boolean restoIndeterminado(int n, List<Integer> inferiores, List<Integer> superiores, boolean temOutros, int k) {
        Set<Integer> restos = new HashSet<>();
        for (int total = NiveisDeAlcance.MUNICIPIO_MINIMO_PARA_FAIXAS; total <= 600; total++) {
            long minimo = 0;
            long maximo = 0;
            boolean cabe = true;
            for (int i = 0; i < inferiores.size(); i++) {
                long baixo = Math.max(k, (long) Math.ceil(inferiores.get(i) * total / 100.0));
                long alto = (long) Math.ceil(superiores.get(i) * total / 100.0) - 1;
                if (baixo > alto) {
                    cabe = false;
                    break;
                }
                minimo += baixo;
                maximo += alto;
            }
            if (!cabe || minimo > total) {
                continue;
            }
            for (long soma = minimo; soma <= Math.min(maximo, total); soma++) {
                long resto = total - soma;
                boolean coerente = temOutros ? resto >= k : resto < k;
                if (coerente) {
                    restos.add((int) resto);
                }
            }
        }
        if (restos.isEmpty()) {
            return true; // nenhuma leitura possível: nada se determina
        }
        return restos.size() >= 2 || restos.contains(0) || restos.iterator().next() >= k;
    }

    private static int pessoasNoMunicipio(List<Pessoa> pessoas, String municipio, Janela janela) {
        return (int) pessoas.stream().filter(p -> noRecorte(p, janela) && p.lugar() != null
                && municipio.equals(p.lugar().municipio())).count();
    }

    private static int pessoasNoBairro(List<Pessoa> pessoas, String municipio, String bairro, Janela janela) {
        return (int) pessoas.stream().filter(p -> noRecorte(p, janela) && p.lugar() != null
                && municipio.equals(p.lugar().municipio()) && bairro.equals(p.lugar().bairro())).count();
    }

    private static boolean noRecorte(Pessoa p, Janela janela) {
        return p.visitas().stream().anyMatch(v -> !v.inicio().isBefore(janela.de()) && v.inicio().isBefore(janela.ate()));
    }
}
