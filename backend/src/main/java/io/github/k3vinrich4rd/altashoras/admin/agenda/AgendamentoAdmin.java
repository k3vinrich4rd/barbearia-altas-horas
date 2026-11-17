package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;

/**
 * O "AgendamentoAdmin" do contrato 1.4.0: o Agendamento mais o contato de quem vem (nome e WhatsApp, nunca e-mail
 * nem CPF, D-4) e o status da cobrança. O toString só mostra o id e o status (R30).
 */
public record AgendamentoAdmin(UUID id, String status, String origem, Servico servico, Barbeiro barbeiro,
        String inicio, String fim, long precoCentavos, @Nullable String expiraEm, @Nullable String motivoCancelamento,
        long version, Cliente cliente, @Nullable String cobrancaStatus) {

    public record Servico(UUID id, String nome, int duracaoMin) {
    }

    public record Barbeiro(UUID id, String nomeExibicao) {
    }

    public record Cliente(String nome, @Nullable String whatsapp) {

        @Override
        public String toString() {
            return "Cliente[dados pessoais omitidos]";
        }
    }

    static AgendamentoAdmin de(PainelDaAgenda.Item item, @Nullable String cobrancaStatus, ZoneId fuso) {
        return new AgendamentoAdmin(item.id(), item.status(), item.origem(),
                new Servico(item.servicoId(), item.servicoNome(), item.duracaoMin()),
                new Barbeiro(item.barbeiroId(), item.barbeiroNome()), FormatoDeData.formatar(item.inicio(), fuso),
                FormatoDeData.formatar(item.fim(), fuso), item.precoCentavos(),
                FormatoDeData.formatar(item.expiraEm(), fuso), item.motivoCancelamento(), item.version(),
                new Cliente(item.clienteNome(), item.clienteWhatsapp()), cobrancaStatus);
    }

    @Override
    public String toString() {
        return "AgendamentoAdmin[" + id + ", " + status + "]";
    }
}
