package io.github.k3vinrich4rd.altashoras.agenda.alcance;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Lugar;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Pessoa;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Regras;
import io.github.k3vinrich4rd.altashoras.agenda.alcance.NiveisDeAlcance.Visita;
import io.github.k3vinrich4rd.altashoras.cep.TabelaIbge;
import io.github.k3vinrich4rd.altashoras.conta.Contas;

/**
 * A leitura do alcance (Fatia 11, E4, E5, E14): lê as reservas que contam e as junta em PESSOAS DISTINTAS, e entrega ao
 * {@link NiveisDeAlcance} (que é só regra). Só CONFIRMADO e CONCLUIDO contam; a reserva manual da 6D fica de fora (D127), o
 * visitante anonimizado também (já não tem contato nem região, E3) e a conta excluída idem. Uma pessoa é:
 * <ul>
 *   <li>uma conta: o {@code usuario.id};</li>
 *   <li>um visitante: o WhatsApp só com dígitos ou, se faltar, o e-mail em minúsculas;</li>
 *   <li>visitante e conta com o mesmo WhatsApp OU o mesmo e-mail são UMA pessoa. A comparação usa um HMAC calculado só durante
 *       a consulta, com a chave {@code app.alcance.chave-de-pessoa} que só vive na memória: nenhum identificador, hash ou
 *       chave é gravado em coluna, em log ou em resposta (art. 6º, III).</li>
 * </ul>
 * O lugar da pessoa é o da reserva mais recente COM região, de qualquer barbeiro e período (E5). O banco nunca devolve nome.
 */
@Service
public class ConsultaAlcance {

    /** Abaixo disso a aplicação não sobe (ADR 0014, item 2). */
    public static final int K_MINIMO_PERMITIDO = 3;

    private record Linha(UUID id, @Nullable UUID cliente, UUID barbeiro, Instant inicio, @Nullable String bairro,
            @Nullable String uf, @Nullable String municipio, @Nullable String whatsapp, @Nullable String email) {

        @Override
        public String toString() {
            return "Linha[omitida]";
        }
    }

    private final JdbcClient jdbc;
    private final Contas contas;
    private final NiveisDeAlcance niveis;
    private final byte[] chave;

    ConsultaAlcance(JdbcClient jdbc, Contas contas, TabelaIbge tabela, @Value("${app.alcance.k:5}") int k,
            @Value("${app.alcance.chave-de-pessoa:}") String chaveDePessoa) {
        if (k < K_MINIMO_PERMITIDO) {
            throw new IllegalStateException("app.alcance.k precisa ser " + K_MINIMO_PERMITIDO + " ou mais (ADR 0014)");
        }
        this.jdbc = jdbc;
        this.contas = contas;
        this.niveis = new NiveisDeAlcance(tabela, k, Regras.TODAS);
        // Sem chave configurada, uma chave aleatória por processo: o resultado do HMAC nunca sai da consulta mesmo.
        if (chaveDePessoa == null || chaveDePessoa.isBlank()) {
            byte[] aleatoria = new byte[32];
            new SecureRandom().nextBytes(aleatoria);
            this.chave = aleatoria;
        } else {
            this.chave = chaveDePessoa.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public NiveisDeAlcance niveis() {
        return niveis;
    }

    /**
     * As pessoas atendidas desde {@code desde} (uma consulta só, no índice parcial {@code reserva_alcance_ix}, sem trazer o
     * histórico inteiro: o pool é de 3 conexões). O recorte é aplicado depois, em memória.
     */
    public List<Pessoa> pessoas(Instant desde) {
        List<Linha> linhas = jdbc.sql("""
                SELECT r.id, r.cliente_id, r.barbeiro_id, r.inicio, r.regiao_bairro, r.regiao_uf, r.regiao_municipio_ibge,
                       g.whatsapp, g.email
                FROM reserva r LEFT JOIN reserva_guest g ON g.reserva_id = r.id
                WHERE r.status IN ('CONFIRMADO', 'CONCLUIDO') AND r.origem <> 'MANUAL'
                  AND (g.reserva_id IS NULL OR g.anonimizado_em IS NULL) AND r.inicio >= :desde
                """).param("desde", java.time.OffsetDateTime.ofInstant(desde, java.time.ZoneOffset.UTC)).query((rs, n) -> new Linha(rs.getObject("id", UUID.class), rs.getObject("cliente_id", UUID.class),
                rs.getObject("barbeiro_id", UUID.class), rs.getObject("inicio", java.time.OffsetDateTime.class).toInstant(),
                rs.getString("regiao_bairro"), rs.getString("regiao_uf"), rs.getString("regiao_municipio_ibge"),
                rs.getString("whatsapp"), rs.getString("email"))).list();

        Set<UUID> idsDeConta = new HashSet<>();
        for (Linha l : linhas) {
            if (l.cliente() != null) {
                idsDeConta.add(l.cliente());
            }
        }
        Map<UUID, Contas.IdentificadoresDePessoa> contasAtivas = contas.identificadoresDePessoa(idsDeConta);

        // Une os identificadores de cada reserva: tudo o que aparece junto numa reserva é a mesma pessoa.
        UnionFind pessoas = new UnionFind();
        Map<UUID, String> raizDaReserva = new HashMap<>();
        List<Linha> validas = new ArrayList<>();
        for (Linha l : linhas) {
            List<String> chaves = new ArrayList<>();
            if (l.cliente() != null) {
                Contas.IdentificadoresDePessoa conta = contasAtivas.get(l.cliente());
                if (conta == null) {
                    continue; // conta excluída: some do alcance
                }
                chaves.add(hmac("u|" + l.cliente()));
                addSeNaoVazio(chaves, "w|", conta.whatsapp());
                addSeNaoVazio(chaves, "e|", conta.email());
            } else {
                addSeNaoVazio(chaves, "w|", l.whatsapp());
                addSeNaoVazio(chaves, "e|", l.email() == null ? null : l.email().toLowerCase(java.util.Locale.ROOT));
            }
            if (chaves.isEmpty()) {
                continue;
            }
            for (String chave : chaves) {
                pessoas.unir(chaves.getFirst(), chave);
            }
            raizDaReserva.put(l.id(), chaves.getFirst());
            validas.add(l);
        }

        Map<String, List<Linha>> porPessoa = new LinkedHashMap<>();
        for (Linha l : validas) {
            porPessoa.computeIfAbsent(pessoas.raiz(raizDaReserva.get(l.id())), r -> new ArrayList<>()).add(l);
        }
        List<Pessoa> resultado = new ArrayList<>();
        for (List<Linha> reservas : porPessoa.values()) {
            Linha maisRecente = reservas.stream().filter(r -> r.uf() != null)
                    .max(Comparator.comparing(Linha::inicio).thenComparing(Linha::id)).orElse(null);
            Lugar lugar = maisRecente == null ? null
                    : new Lugar(maisRecente.uf(), maisRecente.municipio(), maisRecente.bairro());
            resultado.add(new Pessoa(lugar, reservas.stream().map(r -> new Visita(r.barbeiro(), r.inicio())).toList()));
        }
        return resultado;
    }

    private void addSeNaoVazio(List<String> chaves, String prefixo, @Nullable String valor) {
        if (valor != null && !valor.isBlank()) {
            chaves.add(hmac(prefixo + valor));
        }
    }

    private String hmac(String texto) {
        return hmac(chave, texto);
    }

    private static String hmac(byte[] chave, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }

    private static final class UnionFind {

        private final Map<String, String> pai = new HashMap<>();

        String raiz(String x) {
            pai.putIfAbsent(x, x);
            String atual = x;
            while (!pai.get(atual).equals(atual)) {
                pai.put(atual, pai.get(pai.get(atual)));
                atual = pai.get(atual);
            }
            return atual;
        }

        void unir(String a, String b) {
            String ra = raiz(a);
            String rb = raiz(b);
            if (!ra.equals(rb)) {
                pai.put(rb, ra);
            }
        }
    }
}
