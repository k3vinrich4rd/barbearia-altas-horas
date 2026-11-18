package io.github.k3vinrich4rd.altashoras.conta.usuario;

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

    public UUID getId() {
        return id;
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
