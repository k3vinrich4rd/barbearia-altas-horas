package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.pagamento.gateway.MetodoPagamento;

/**
 * A "Cobranca" do contrato (e a "CobrancaCriada", quando traz {@code cpfMascarado}, que só existe no 201 da
 * criação). {@code pix} só vem no Pix e {@code cartao} só no cartão; o outro é nulo. {@code qrCodeBase64} é nulo no
 * modo simulado e na repetição da criação: o front gera o QR a partir do copia e cola (D68).
 */
public record CobrancaResposta(UUID id, UUID reservaId, StatusCobranca status, MetodoPagamento metodo,
        long valorCentavos, @Nullable Pix pix, @Nullable Cartao cartao,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String cpfMascarado) {

    public record Pix(String copiaECola, @Nullable String qrCodeBase64, @Nullable String expiraEm) {
    }

    public record Cartao(String urlCheckout, @Nullable String expiraEm) {
    }

    /** O mesmo formato da agenda: sempre com segundos e offset (2026-12-01T10:00:00-03:00). */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static CobrancaResposta de(Cobranca c, ZoneId fuso, @Nullable String qrCodeBase64, @Nullable String cpfMascarado) {
        String expiraEm = c.getExpiraEm() == null ? null : FORMATO.format(c.getExpiraEm().atZone(fuso));
        Pix pix = c.getMetodo() == MetodoPagamento.PIX && c.getPixCopiaECola() != null
                ? new Pix(c.getPixCopiaECola(), qrCodeBase64, expiraEm) : null;
        Cartao cartao = c.getMetodo() == MetodoPagamento.CARTAO_CREDITO && c.getUrlCheckout() != null
                ? new Cartao(c.getUrlCheckout(), expiraEm) : null;
        return new CobrancaResposta(c.getId(), c.getReservaId(), c.getStatus(), c.getMetodo(), c.getValorCentavos(),
                pix, cartao, cpfMascarado);
    }

    @Override
    public String toString() {
        return "CobrancaResposta[" + id + ", " + status + "]";
    }
}
