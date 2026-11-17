package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/**
 * Eventos do gateway em REVISAO_MANUAL (E14): listar e "marcar revisado". Marcar não reprocessa nada: o Eduardo confere
 * o gateway e tira o item da fila. A auditoria entra na mesma transação (E11). Sem payload e sem dado pessoal.
 */
@Service
class RevisarEvento {

    record EventoAdmin(UUID id, String tipo, String recebidoEm, int tentativas, @Nullable String ultimoErro,
            @Nullable String idCobrancaProvedor, String resultado, @Nullable String processadoEm) {
    }

    record Pagina(List<EventoAdmin> itens, int pagina, int tamanho, long total) {
    }

    private final PendenciasDePagamento pagamentos;
    private final Auditor auditor;
    private final ZoneId fuso;

    RevisarEvento(PendenciasDePagamento pagamentos, Auditor auditor, @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.pagamentos = pagamentos;
        this.auditor = auditor;
        this.fuso = ZoneId.of(fuso);
    }

    Pagina listar(int pagina, int tamanho) {
        PendenciasDePagamento.Pagina<PendenciasDePagamento.Evento> pedida = pagamentos.eventosEmRevisao(pagina, tamanho);
        return new Pagina(pedida.itens().stream().map(this::converter).toList(), pedida.pagina(), pedida.tamanho(),
                pedida.total());
    }

    @Transactional
    EventoAdmin revisar(Ator ator, UUID id) {
        PendenciasDePagamento.Evento evento = pagamentos.revisar(id);
        auditor.registrar(ator, "EVENTO_REVISADO", "evento_webhook", id,
                Map.of("tipo", evento.tipo(), "para", evento.resultado()));
        return converter(evento);
    }

    private EventoAdmin converter(PendenciasDePagamento.Evento e) {
        return new EventoAdmin(e.id(), e.tipo(), FormatoDeData.formatar(e.recebidoEm(), fuso), e.tentativas(),
                e.ultimoErro(), e.idCobrancaProvedor(), e.resultado(), FormatoDeData.formatar(e.processadoEm(), fuso));
    }
}
