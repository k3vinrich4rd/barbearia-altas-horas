package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/**
 * A "CobrancaAdmin" do contrato 1.4.0: a Cobranca sem Pix e sem cartão (o painel não precisa do copia e cola nem do
 * link), com a data da devolução, o resumo da reserva e o contato de quem pagou, para o Eduardo saber para quem fazer
 * o Pix de volta. Nunca CPF nem e-mail. O toString só mostra o id e o status (R30).
 */
public record CobrancaAdmin(UUID id, UUID reservaId, String status, String metodo, long valorCentavos,
        String criadoEm, @Nullable String devolvidaEm, @Nullable ReservaResumo reserva, @Nullable Contato contato) {

    public record ReservaResumo(UUID id, String inicio, String servico, String statusReserva,
            @Nullable String motivoCancelamento) {
    }

    public record Contato(String nome, @Nullable String whatsapp) {

        @Override
        public String toString() {
            return "Contato[dados pessoais omitidos]";
        }
    }

    static CobrancaAdmin de(PendenciasDePagamento.Cobranca cobranca, Reservas.@Nullable Resumo resumo, ZoneId fuso) {
        return new CobrancaAdmin(cobranca.id(), cobranca.reservaId(), cobranca.status(), cobranca.metodo(),
                cobranca.valorCentavos(), FormatoDeData.formatar(cobranca.criadoEm(), fuso),
                FormatoDeData.formatar(cobranca.devolvidaEm(), fuso),
                resumo == null ? null : new ReservaResumo(resumo.id(), FormatoDeData.formatar(resumo.inicio(), fuso),
                        resumo.servico(), resumo.status(), resumo.motivoCancelamento()),
                resumo == null ? null : new Contato(resumo.contatoNome(), resumo.contatoWhatsapp()));
    }

    @Override
    public String toString() {
        return "CobrancaAdmin[" + id + ", " + status + "]";
    }
}
