package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Os dados de contato do visitante sem conta, numa tabela separada da reserva (E8): anonimizar depois (Fatia 6)
 * é limpar esta linha. Sem CPF (R9). O token de acesso fica só como SHA-256 (E7).
 */
@Entity
@Table(name = "reserva_guest")
class ReservaGuest {

    @Id
    @Column(name = "reserva_id")
    private UUID reservaId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String whatsapp;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ReservaGuest() {
    }

    ReservaGuest(UUID reservaId, String nome, String email, String whatsapp, String tokenHash, Instant agora) {
        this.reservaId = reservaId;
        this.nome = nome;
        this.email = email;
        this.whatsapp = whatsapp;
        this.tokenHash = tokenHash;
        this.criadoEm = agora;
    }

    String getTokenHash() {
        return tokenHash;
    }

    @Override
    public String toString() {
        return "ReservaGuest[" + reservaId + ", dados pessoais omitidos]";
    }
}
