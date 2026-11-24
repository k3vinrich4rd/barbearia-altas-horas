package io.github.k3vinrich4rd.altashoras.cep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * A tabela do IBGE embutida no jar (Fatia 11, E16, ADR 0014): município → região imediata e UF, carregada UMA vez na subida e
 * imutável. Nenhuma chamada de rede traduz região em tempo de execução; o arquivo {@code ibge/municipios.csv} é gerado por
 * {@code scripts/gerar-tabela-ibge.sh} e conferido na revisão. A primeira linha é um cabeçalho de comentário com a fonte, a data
 * e a contagem, e a subida falha se a contagem não bater (D-8). O log só diz a {@link #versao()}.
 */
@Component
public class TabelaIbge {

    private static final Logger LOG = LoggerFactory.getLogger(TabelaIbge.class);
    static final String ARQUIVO = "ibge/municipios.csv";

    /** Um município e a região imediata a que pertence. */
    public record Municipio(String codigo, String nome, String uf, String regiaoCodigo, String regiaoNome) {
    }

    private final Map<String, Municipio> porCodigo;
    private final Map<String, List<Municipio>> porNomeEUf;
    private final Map<String, String> nomeDaRegiao;
    private final Set<String> ufs;
    private final String versao;

    public TabelaIbge() {
        this(ARQUIVO);
    }

    /** Para os testes: carrega outro arquivo do classpath (por exemplo, uma tabela pequena). */
    public static TabelaIbge doClasspath(String caminho) {
        return new TabelaIbge(caminho);
    }

    private TabelaIbge(String caminho) {
        Map<String, Municipio> codigos = new HashMap<>();
        Map<String, List<Municipio>> nomes = new HashMap<>();
        Map<String, String> regioes = new HashMap<>();
        Set<String> estados = new LinkedHashSet<>();
        String cabecalho = null;
        try (InputStream entrada = new ClassPathResource(caminho).getInputStream();
                BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.startsWith("#")) {
                    cabecalho = linha;
                    continue;
                }
                if (linha.isBlank() || linha.startsWith("codigo;")) {
                    continue;
                }
                String[] campos = linha.split(";", -1);
                if (campos.length != 5 || !campos[0].matches("[0-9]{7}") || !campos[2].matches("[A-Z]{2}")) {
                    throw new IllegalStateException("Linha inválida na tabela do IBGE");
                }
                Municipio municipio = new Municipio(campos[0], campos[1], campos[2], campos[3], campos[4]);
                codigos.put(municipio.codigo(), municipio);
                nomes.computeIfAbsent(chaveDoNome(municipio.nome(), municipio.uf()), k -> new ArrayList<>()).add(municipio);
                regioes.put(municipio.regiaoCodigo(), municipio.regiaoNome());
                estados.add(municipio.uf());
            }
        } catch (IOException falha) {
            throw new UncheckedIOException("Tabela do IBGE ausente: " + caminho, falha);
        }
        this.versao = conferirCabecalho(cabecalho, codigos.size(), regioes.size(), estados.size());
        this.porCodigo = Map.copyOf(codigos);
        Map<String, List<Municipio>> copia = new HashMap<>();
        nomes.forEach((chave, lista) -> copia.put(chave, List.copyOf(lista)));
        this.porNomeEUf = Map.copyOf(copia);
        this.nomeDaRegiao = Map.copyOf(regioes);
        this.ufs = Set.copyOf(estados);
        LOG.info("tabela do IBGE carregada: versao={}, municipios={}", versao, porCodigo.size());
    }

    private static String conferirCabecalho(String cabecalho, int municipios, int regioes, int ufs) {
        if (cabecalho == null) {
            throw new IllegalStateException("A tabela do IBGE não tem o cabeçalho com a fonte e a contagem");
        }
        String esperado = "municipios=" + municipios + " regioesImediatas=" + regioes + " ufs=" + ufs;
        if (!cabecalho.contains(esperado)) {
            throw new IllegalStateException("A contagem da tabela do IBGE não bate com o cabeçalho");
        }
        int data = cabecalho.indexOf("data=");
        return data < 0 ? "desconhecida" : cabecalho.substring(data + 5, Math.min(cabecalho.length(), data + 15));
    }

    /** O município pelo código de 7 dígitos; vazio se o código não está na tabela. */
    public Optional<Municipio> porCodigo(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(porCodigo.get(codigo));
    }

    /** Pelo nome (sem acento, sem diferenciar maiúsculas) e UF: só devolve se houver exatamente um. */
    public Optional<Municipio> porNomeEUf(String nome, String uf) {
        if (nome == null || uf == null) {
            return Optional.empty();
        }
        List<Municipio> achados = porNomeEUf.getOrDefault(chaveDoNome(nome, uf), List.of());
        return achados.size() == 1 ? Optional.of(achados.getFirst()) : Optional.empty();
    }

    /** O nome da região imediata, ou vazio se o código não existe. */
    public Optional<String> nomeDaRegiao(String regiaoCodigo) {
        return Optional.ofNullable(nomeDaRegiao.get(regiaoCodigo));
    }

    public int totalDeMunicipios() {
        return porCodigo.size();
    }

    public int totalDeRegioes() {
        return nomeDaRegiao.size();
    }

    public int totalDeUfs() {
        return ufs.size();
    }

    /** A data da geração da tabela (a única coisa que o log da subida mostra). */
    public String versao() {
        return versao;
    }

    private static String chaveDoNome(String nome, String uf) {
        String semAcento = Normalizer.normalize(nome.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT) + "|" + uf.strip().toUpperCase(Locale.ROOT);
    }
}
