package io.github.k3vinrich4rd.altashoras.conta.usuario;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Conta de quem entra no sistema: cliente, barbeiro ou admin. A senha existe só como hash. */
@Entity
@Table(name = "usuario")
public class Usuario {

    public static final String CLIENTE = "CLIENTE";
    public static final String BARBEIRO = "BARBEIRO";
    public static final String ADMIN = "ADMIN";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    private String whatsapp;

    @Column(name = "senha_hash")
    private String senhaHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 8)
    private String cep;

    private String bairro;

    private String cidade;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String uf;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "versao_credencial", nullable = false)
    private int versaoCredencial;

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    @Column(name = "deve_trocar_senha", nullable = false)
    private boolean deveTrocarSenha;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papel", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "papel")
    private Set<String> papeis = new TreeSet<>();

    protected Usuario() {
    }

    public Usuario(String nome, String email, String whatsapp, String senhaHash, String cep, Set<String> papeis,
            boolean deveTrocarSenha) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.email = email;
        this.whatsapp = whatsapp;
        this.senhaHash = senhaHash;
        this.cep = cep;
        this.ativo = true;
        this.versaoCredencial = 0;
        this.deveTrocarSenha = deveTrocarSenha;
        this.papeis = new TreeSet<>(papeis);
    }

    /** Endereço que o CEP resolveu (Fatia 6A, R31): só é chamado quando o provedor respondeu. */
    public void resolverEndereco(String bairro, String cidade, String uf) {
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
    }

    /**
     * Senha nova: sobe a versão da credencial. Os tokens já emitidos carregam a versão antiga e param de ser
     * renovados (E8, E23).
     */
    public void trocarSenha(String novoHash) {
        this.senhaHash = novoHash;
        this.versaoCredencial++;
        this.deveTrocarSenha = false;
    }

    /**
     * Exclusão da conta (LGPD, Fatia 6B, E16): a linha fica (reservas, auditoria e solicitações apontam para ela), mas
     * sem nenhum dado pessoal. O e-mail vira um valor único e inválido, o que o deixa livre para um novo cadastro; a
     * conta é desativada e a versão da credencial sobe, o que derruba os tokens já emitidos.
     */
    public void anonimizar(Instant agora) {
        this.nome = "Conta excluída";
        this.email = "excluido-" + id + "@anonimo.invalid";
        this.whatsapp = null;
        this.senhaHash = null;
        this.cep = null;
        this.bairro = null;
        this.cidade = null;
        this.uf = null;
        this.ativo = false;
        this.deveTrocarSenha = false;
        this.versaoCredencial++;
        this.anonimizadoEm = agora;
    }

    /** Soma um papel à conta (o aceite do convite, H32): sobe a versão da credencial. */
    public void somarPapel(String papel) {
        if (papeis.add(papel)) {
            this.versaoCredencial++;
        }
    }

    /** Tira um papel da conta (o desligamento, H31): sobe a versão da credencial. */
    public void tirarPapel(String papel) {
        if (papeis.remove(papel)) {
            this.versaoCredencial++;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getCep() {
        return cep;
    }

    public String getBairro() {
        return bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public String getUf() {
        return uf;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getVersaoCredencial() {
        return versaoCredencial;
    }

    public boolean isDeveTrocarSenha() {
        return deveTrocarSenha;
    }

    public Set<String> getPapeis() {
        return papeis;
    }
}
