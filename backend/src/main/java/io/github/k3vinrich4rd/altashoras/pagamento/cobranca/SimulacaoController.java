package io.github.k3vinrich4rd.altashoras.pagamento.cobranca;

import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.k3vinrich4rd.altashoras.agenda.Reservas;
import io.github.k3vinrich4rd.altashoras.agenda.Reservas.Dono;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.GatewaySimulado;
import io.github.k3vinrich4rd.altashoras.pagamento.gateway.Provedor;

/**
 * "O cliente pagou", só no modo simulado do beta (E28, D59). Não confirma a reserva por conta própria: marca a cobrança
 * como paga no GatewaySimulado e passa pelo MESMO caminho do webhook (evento gravado, gateway reconsultado, reserva
 * confirmada), então o fluxo testado é o da produção. No modo {@code asaas} este controller não existe: a rota é 404.
 * Só o dono da reserva pode chamar (bearer ou X-Guest-Token); outro usuário recebe 404 (R23).
 */
@RestController
@ConditionalOnProperty(name = "app.pagamento.modo", havingValue = "simulado", matchIfMissing = true)
class SimulacaoController {

    private final CobrancaRepository cobrancas;
    private final Reservas reservas;
    private final GatewaySimulado gateway;
    private final ReceberWebhook receber;
    private final ZoneId fuso;

    SimulacaoController(CobrancaRepository cobrancas, Reservas reservas, GatewaySimulado gateway, ReceberWebhook receber,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.cobrancas = cobrancas;
        this.reservas = reservas;
        this.gateway = gateway;
        this.receber = receber;
        this.fuso = ZoneId.of(fuso);
    }

    @PostMapping("/pagamentos/simulacao/{cobrancaId}/confirmar")
    ResponseEntity<CobrancaResposta> confirmar(@PathVariable UUID cobrancaId, @AuthenticationPrincipal @Nullable Jwt token,
            @RequestHeader(name = "X-Guest-Token", required = false) @Nullable String tokenGuest) {
        // Autentica primeiro: anônimo recebe sempre 401, exista a cobrança ou não (sem oráculo de existência).
        Dono dono = Donos.de(token, tokenGuest);
        Cobranca cobranca = cobrancas.findById(cobrancaId).orElseThrow(SimulacaoController::naoEncontrada);
        reservas.paraCobranca(cobranca.getReservaId(), dono); // 404 se não for o dono
        String idProvedor = cobranca.getIdCobrancaProvedor();
        if (idProvedor == null) {
            throw naoEncontrada();
        }

        gateway.marcarPago(idProvedor);
        receber.receber(Provedor.SIMULADO, "sim_" + cobrancaId, "PAYMENT_RECEIVED", idProvedor);

        Cobranca atual = cobrancas.findById(cobrancaId).orElseThrow(SimulacaoController::naoEncontrada);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CobrancaResposta.de(atual, fuso, null, null));
    }

    private static ExcecaoDeNegocio naoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Cobrança não encontrada.");
    }
}
