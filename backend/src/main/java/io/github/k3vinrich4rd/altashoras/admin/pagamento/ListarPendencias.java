package io.github.k3vinrich4rd.altashoras.admin.pagamento;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.pagamento.PendenciasDePagamento;

/**
 * As pendências de pagamento do painel (E12): o pagamento traz a página de cobranças (lista e contagem); o resumo e o
 * contato das reservas vêm em lote pela API da agenda. Três consultas, com 2 ou 20 pendências.
 */
@Service
class ListarPendencias {

    /** A página de cobranças do contrato (PaginaCobrancas). */
    record Pagina(List<CobrancaAdmin> itens, int pagina, int tamanho, long total) {
    }

    private final PendenciasDePagamento pagamentos;
    private final Reservas reservas;
    private final ZoneId fuso;

    ListarPendencias(PendenciasDePagamento pagamentos, Reservas reservas,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.pagamentos = pagamentos;
        this.reservas = reservas;
        this.fuso = ZoneId.of(fuso);
    }

    Pagina listar(@Nullable String status, int pagina, int tamanho) {
        PendenciasDePagamento.Pagina<PendenciasDePagamento.Cobranca> pedida = pagamentos.listar(status, pagina, tamanho);
        Map<UUID, Reservas.Resumo> resumos = reservas
                .resumos(pedida.itens().stream().map(PendenciasDePagamento.Cobranca::reservaId).toList());
        return new Pagina(pedida.itens().stream().map(c -> CobrancaAdmin.de(c, resumos.get(c.reservaId()), fuso)).toList(),
                pedida.pagina(), pedida.tamanho(), pedida.total());
    }
}
