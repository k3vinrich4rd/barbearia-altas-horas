package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.CobrancaNoGateway;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.MetodoPagamento;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.Provedor;

/**
 * A cobrança de 100% do preço (R8). Nasce PENDENTE, ainda sem o id do provedor (E14); o CPF nunca é guardado
 * (ADR 0009): só os dois hashes da idempotência (R10). O toString() não imprime nada além do id e do status.
 */
@Entity
@Table(name = "cobranca")
class Cobranca {

    @Id
    private UUID id;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provedor provedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @Column(name = "id_cobranca_provedor")
    private @Nullable String idCobrancaProvedor;

    @Column(name = "id_cliente_provedor")
    private @Nullable String idClienteProvedor;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "chave_idempotencia_hash", nullable = false, length = 64)
    private String chaveIdempotenciaHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_corpo", nullable = false, length = 64)
    private String hashCorpo;

    @Column(name = "pix_copia_e_cola")
    private @Nullable String pixCopiaECola;

    @Column(name = "url_checkout")
    private @Nullable String urlCheckout;

    @Column(name = "expira_em")
    private @Nullable Instant expiraEm;

    @Column(name = "paga_em")
    private @Nullable Instant pagaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /** Objeto, não primitivo: nulo = entidade nova, e o persist não confunde com uma cobrança que já existe. */
    @Version
    private @Nullable Long version;

    protected Cobranca() {
    }

    static Cobranca nova(UUID reservaId, Provedor provedor, MetodoPagamento metodo, long valorCentavos,
            String chaveIdempotenciaHash, String hashCorpo, @Nullable Instant expiraEm, Instant agora) {
        Cobranca c = new Cobranca();
        c.id = UUID.randomUUID();
        c.reservaId = reservaId;
        c.provedor = provedor;
        c.metodo = metodo;
        c.valorCentavos = valorCentavos;
        c.status = StatusCobranca.PENDENTE;
        c.chaveIdempotenciaHash = chaveIdempotenciaHash;
        c.hashCorpo = hashCorpo;
        c.expiraEm = expiraEm;
        c.criadoEm = agora;
        c.atualizadoEm = agora;
        return c;
    }

    /** Terceiro passo da criação (E14): o gateway respondeu. O QR em imagem não é guardado (o front refaz do texto). */
    void registrarNoProvedor(CobrancaNoGateway resposta, Instant agora) {
        this.idCobrancaProvedor = resposta.idProvedor();
        this.idClienteProvedor = resposta.idClienteProvedor();
        this.pixCopiaECola = metodo == MetodoPagamento.PIX ? resposta.pixCopiaECola() : null;
        this.urlCheckout = metodo == MetodoPagamento.CARTAO_CREDITO ? resposta.urlCheckout() : null;
        this.atualizadoEm = agora;
    }

    UUID getId() {
        return id;
    }

    UUID getReservaId() {
        return reservaId;
    }

    Provedor getProvedor() {
        return provedor;
    }

    MetodoPagamento getMetodo() {
        return metodo;
    }

    @Nullable String getIdCobrancaProvedor() {
        return idCobrancaProvedor;
    }

    long getValorCentavos() {
        return valorCentavos;
    }

    StatusCobranca getStatus() {
        return status;
    }

    String getHashCorpo() {
        return hashCorpo;
    }

    @Nullable String getPixCopiaECola() {
        return pixCopiaECola;
    }

    @Nullable String getUrlCheckout() {
        return urlCheckout;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    @Nullable Instant getExpiraEm() {
        return expiraEm;
    }

    @Override
    public String toString() {
        return "Cobranca[" + id + ", " + status + "]";
    }
}
