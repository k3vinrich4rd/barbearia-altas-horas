package io.github.k3vinrich4rd.altashoras.pagamento.gateway;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador do Asaas (ADR 0007): fica desligado até a produção ({@code app.pagamento.modo=asaas}, D59) e é provado
 * só com MockRestServiceServer. Pix: cobrança {@code PIX} + QR Code; cartão: cobrança {@code CREDIT_CARD} e o link da
 * fatura vira o checkout, então o número do cartão nunca passa pela API (D32). Timeouts de 2 s (conexão) e 5 s
 * (leitura), e qualquer falha de rede ou resposta de erro vira {@link GatewayIndisponivelException} sem registrar o
 * corpo (nem o CPF, nem o nome) no log (R30).
 */
@Component
@ConditionalOnProperty(name = "app.pagamento.modo", havingValue = "asaas")
public class AsaasGateway implements GatewayDePagamento {

    private static final Logger LOG = LoggerFactory.getLogger(AsaasGateway.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cliente(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagamento(String id, @Nullable String customer, @Nullable String status, @Nullable String billingType,
            @Nullable String invoiceUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QrPix(@Nullable String payload, @Nullable String encodedImage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lista(List<Pagamento> data) {
    }

    private final RestClient http;
    private final ZoneId fuso;

    @Autowired
    public AsaasGateway(@Value("${app.pagamento.asaas.url}") String url, @Value("${app.pagamento.asaas.token:}") String token,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this(RestClient.builder().requestFactory(fabricaComTimeouts()), url, token, fuso);
    }

    /** Para o teste: o builder já vem ligado ao MockRestServiceServer. */
    AsaasGateway(RestClient.Builder construtor, String url, String token, String fuso) {
        this.http = construtor.baseUrl(url).defaultHeader("access_token", token).build();
        this.fuso = ZoneId.of(fuso);
    }

    private static JdkClientHttpRequestFactory fabricaComTimeouts() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return fabrica;
    }

    @Override
    public Provedor provedor() {
        return Provedor.ASAAS;
    }

    @Override
    public CobrancaNoGateway criar(NovaCobranca pedido) {
        return chamar(() -> {
            Cliente cliente = http.post().uri("/customers")
                    .body(Map.of("name", pedido.nomePagador(), "cpfCnpj", pedido.cpf().digitos()))
                    .retrieve().body(Cliente.class);

            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("customer", cliente.id());
            corpo.put("billingType", pedido.metodo() == MetodoPagamento.PIX ? "PIX" : "CREDIT_CARD");
            // Reais com 2 casas a partir dos centavos, sem passar por double.
            corpo.put("value", BigDecimal.valueOf(pedido.valorCentavos(), 2));
            corpo.put("dueDate", pedido.expiraEm().atZone(fuso).toLocalDate().toString());
            corpo.put("externalReference", pedido.cobrancaId().toString());
            Pagamento pagamento = http.post().uri("/payments").body(corpo).retrieve().body(Pagamento.class);
            return montar(pagamento, cliente.id());
        });
    }

    @Override
    public Optional<CobrancaNoGateway> buscarPorReferencia(UUID cobrancaId) {
        return chamar(() -> {
            Lista lista = http.get().uri("/payments?externalReference={ref}", cobrancaId).retrieve().body(Lista.class);
            if (lista == null || lista.data() == null || lista.data().isEmpty()) {
                return Optional.<CobrancaNoGateway>empty();
            }
            Pagamento primeiro = lista.data().getFirst();
            return Optional.of(montar(primeiro, primeiro.customer()));
        });
    }

    @Override
    public StatusNoGateway consultar(String idProvedor) {
        return chamar(() -> {
            Pagamento pagamento = http.get().uri("/payments/{id}", idProvedor).retrieve().body(Pagamento.class);
            String status = pagamento.status() == null ? "" : pagamento.status();
            return switch (status) {
                case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> StatusNoGateway.PAGA;
                case "PENDING", "OVERDUE" -> StatusNoGateway.PENDENTE;
                default -> StatusNoGateway.CANCELADA;
            };
        });
    }

    private CobrancaNoGateway montar(Pagamento pagamento, @Nullable String idCliente) {
        String cliente = idCliente == null ? "" : idCliente;
        if ("CREDIT_CARD".equals(pagamento.billingType())) {
            return new CobrancaNoGateway(pagamento.id(), cliente, null, null, pagamento.invoiceUrl());
        }
        QrPix qr = http.get().uri("/payments/{id}/pixQrCode", pagamento.id()).retrieve().body(QrPix.class);
        return new CobrancaNoGateway(pagamento.id(), cliente, qr.payload(), qr.encodedImage(), null);
    }

    private <T> T chamar(java.util.function.Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (RestClientException | NullPointerException falha) {
            // Só o tipo do erro: a mensagem ou o corpo da resposta poderiam trazer dado do pagador (R30).
            LOG.warn("Gateway Asaas indisponível ({})", falha.getClass().getSimpleName());
            throw new GatewayIndisponivelException();
        }
    }
}
