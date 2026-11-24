package io.github.k3vinrich4rd.altashoras.admin.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.ProrrogacaoDoPagamento;

/**
 * O "AgendamentoAdmin" do contrato (1.6.0 acrescentou {@code observacao} e a origem {@code MANUAL}): o Agendamento mais o contato de quem vem (nome e WhatsApp, nunca e-mail
 * nem CPF, D-4) e o status da cobrança. O toString só mostra o id e o status (R30).
 */
public record AgendamentoAdmin(UUID id, String status, String origem, Servico servico, Barbeiro barbeiro,
        String inicio, String fim, long precoCentavos, @Nullable String expiraEm, @Nullable String motivoCancelamento,
        long version, Cliente cliente, @Nullable String cobrancaStatus, @Nullable String observacao,
        @Nullable Prorrogacao prorrogacao) {

    /** A janela do "Acrescentar 1 minuto" (1.11.0): o Eduardo entende um pagamento que chegou perto do fim do prazo. */
    public record Prorrogacao(boolean usada, long segundos, String abreEm, String fechaEm) {
    }

    /** Reserva manual sem nome informado (ou já anonimizada): a tela mostra este texto. */
    static final String SEM_NOME = "Sem nome";

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

    /** Sem janela de prorrogação: serve às respostas que nunca estão AGUARDANDO_PAGAMENTO (reserva manual, fechamento e cancelamento). */
    static AgendamentoAdmin de(PainelDaAgenda.Item item, @Nullable String cobrancaStatus, ZoneId fuso) {
        return de(item, cobrancaStatus, fuso, null, null);
    }

    static AgendamentoAdmin de(PainelDaAgenda.Item item, @Nullable String cobrancaStatus, ZoneId fuso,
            @Nullable Instant agora, @Nullable Duration prorrogacao) {
        ProrrogacaoDoPagamento janela = agora == null || prorrogacao == null ? null
                : ProrrogacaoDoPagamento.de(item.status(), item.origem(), item.expiraEm(), item.prorrogadaEm(), agora, prorrogacao);
        return new AgendamentoAdmin(item.id(), item.status(), item.origem(),
                new Servico(item.servicoId(), item.servicoNome(), item.duracaoMin()),
                new Barbeiro(item.barbeiroId(), item.barbeiroNome()), FormatoDeData.formatar(item.inicio(), fuso),
                FormatoDeData.formatar(item.fim(), fuso), item.precoCentavos(),
                FormatoDeData.formatar(item.expiraEm(), fuso), item.motivoCancelamento(), item.version(),
                new Cliente(item.clienteNome() == null ? SEM_NOME : item.clienteNome(), item.clienteWhatsapp()), cobrancaStatus,
                item.observacao(), janela == null ? null : new Prorrogacao(janela.usada(), janela.segundos(),
                        FormatoDeData.formatar(janela.abreEm(), fuso), FormatoDeData.formatar(janela.fechaEm(), fuso)));
    }

    @Override
    public String toString() {
        return "AgendamentoAdmin[" + id + ", " + status + "]";
    }
}
