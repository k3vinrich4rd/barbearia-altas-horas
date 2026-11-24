package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.AvisoParaEnvio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.CanalDeNotificacao;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso.Aparelho;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ResultadoDoEnvio;

/**
 * Escolhe e chama os canais LIGADOS em {@code app.notificacao.canais} (E7). Recebe todas as implementações como beans e
 * usa só as da lista: nome desconhecido derruba a partida com uma mensagem que cita a propriedade e os nomes válidos
 * (A18). Lista vazia: só o aviso do painel. Nunca roda dentro de transação (E5): quem chama garante.
 */
@Component
public class Despachante {

    private static final Logger LOG = LoggerFactory.getLogger(Despachante.class);

    /** O que o envio fez, para quem chama gravar: aparelhos entregues, aparelhos a apagar e se falta tentar de novo. */
    public record Relatorio(List<Aparelho> enviados, List<Aparelho> invalidos, boolean pendente) {
    }

    private final Map<Canal, CanalDeNotificacao> ligados = new EnumMap<>(Canal.class);

    public Despachante(List<CanalDeNotificacao> implementacoes, ParametrosDeNotificacao parametros) {
        List<String> validos = Arrays.stream(Canal.values()).map(Canal::nome).toList();
        for (String nome : parametros.nomesDeCanais()) {
            if (!validos.contains(nome)) {
                throw new IllegalStateException("app.notificacao.canais (APP_NOTIFICACAO_CANAIS) tem um canal desconhecido: '"
                        + nome + "'. Nomes válidos: " + String.join(", ", validos) + ".");
            }
        }
        if (parametros.ligado(Canal.WEBPUSH) && parametros.exigirChaves() && parametros.vapidPrivada().isEmpty()) {
            throw new IllegalStateException("O canal webpush está ligado e app.notificacao.webpush.exigir-chaves é verdadeiro, "
                    + "mas falta a chave VAPID privada (APP_NOTIFICACAO_VAPID_PRIVADA). Gere o par e cadastre no ambiente.");
        }
        for (Canal canal : Canal.values()) {
            if (!parametros.ligado(canal)) {
                continue;
            }
            // Com mais de uma implementação do mesmo canal (só acontece nos testes), vale a primeira da ordem do Spring.
            implementacoes.stream().filter(c -> c.canal() == canal).findFirst()
                    .ifPresent(c -> ligados.put(canal, c));
        }
    }

    public List<Canal> canaisLigados() {
        return List.copyOf(ligados.keySet());
    }

    public boolean algumPorEmail() {
        return ligados.values().stream().anyMatch(c -> !c.porAparelho());
    }

    /**
     * Entrega o aviso. {@code pendente} só quando algum canal falhou de forma passageira e NENHUM destino dele recebeu
     * (E6): o resultado de um canal nunca impede o outro (A16).
     */
    public Relatorio entregar(AvisoParaEnvio aviso, UUID usuarioId, List<Aparelho> aparelhos,
            Supplier<@Nullable String> email) {
        List<Aparelho> enviados = new ArrayList<>();
        List<Aparelho> invalidos = new ArrayList<>();
        boolean pendente = false;
        for (CanalDeNotificacao canal : ligados.values()) {
            boolean algumEnviado = false;
            boolean algumPassageiro = false;
            if (canal.porAparelho()) {
                for (Aparelho aparelho : aparelhos) {
                    ResultadoDoEnvio resultado = chamar(canal, aviso, new DestinoDoAviso(usuarioId, aparelho, null));
                    switch (resultado) {
                        case ENVIADO -> {
                            algumEnviado = true;
                            enviados.add(aparelho);
                        }
                        case DESTINO_INVALIDO -> invalidos.add(aparelho);
                        case TENTAR_DEPOIS, RECUSADO -> algumPassageiro = true;
                        case NAO_CONFIGURADO -> LOG.warn("canal {} ligado sem configuração; aviso não enviado", canal.canal().nome());
                    }
                }
            } else {
                String endereco = email.get();
                if (endereco == null) {
                    continue;
                }
                ResultadoDoEnvio resultado = chamar(canal, aviso, new DestinoDoAviso(usuarioId, null, endereco));
                algumEnviado = resultado == ResultadoDoEnvio.ENVIADO;
                algumPassageiro = resultado == ResultadoDoEnvio.TENTAR_DEPOIS || resultado == ResultadoDoEnvio.RECUSADO;
                if (resultado == ResultadoDoEnvio.NAO_CONFIGURADO) {
                    LOG.warn("canal {} ligado sem configuração; aviso não enviado", canal.canal().nome());
                }
            }
            pendente |= algumPassageiro && !algumEnviado;
        }
        return new Relatorio(enviados, invalidos, pendente);
    }

    private static ResultadoDoEnvio chamar(CanalDeNotificacao canal, AvisoParaEnvio aviso, DestinoDoAviso destino) {
        try {
            return canal.enviar(aviso, destino);
        } catch (RuntimeException falha) {
            // Só a classe da falha: a mensagem pode trazer o endpoint (R30).
            LOG.warn("canal {} falhou: {}", canal.canal().nome(), falha.getClass().getSimpleName());
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        }
    }

    @Override
    public String toString() {
        return "Despachante[" + ligados.keySet().stream().map(Canal::nome).collect(Collectors.joining(",")) + "]";
    }
}
