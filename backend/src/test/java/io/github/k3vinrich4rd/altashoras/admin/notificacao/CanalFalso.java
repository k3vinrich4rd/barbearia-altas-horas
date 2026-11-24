package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.AvisoParaEnvio;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.CanalDeNotificacao;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.DestinoDoAviso;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.ResultadoDoEnvio;

/**
 * O canal dos testes de integração: grava o que recebeu, decide o resultado por quem chama e anota se havia transação
 * ativa na hora do envio (A14). Nenhum teste fala com a rede real.
 */
public class CanalFalso implements CanalDeNotificacao {

    public record Envio(AvisoParaEnvio aviso, DestinoDoAviso destino, boolean emTransacao) {
    }

    private final Canal canal;
    private final boolean porAparelho;
    private final List<Envio> envios = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<DestinoDoAviso, ResultadoDoEnvio> resultado = d -> ResultadoDoEnvio.ENVIADO;

    public CanalFalso(Canal canal, boolean porAparelho) {
        this.canal = canal;
        this.porAparelho = porAparelho;
    }

    @Override
    public Canal canal() {
        return canal;
    }

    @Override
    public boolean porAparelho() {
        return porAparelho;
    }

    @Override
    public ResultadoDoEnvio enviar(AvisoParaEnvio aviso, DestinoDoAviso destino) {
        envios.add(new Envio(aviso, destino, TransactionSynchronizationManager.isActualTransactionActive()));
        return resultado.apply(destino);
    }

    public List<Envio> envios() {
        synchronized (envios) {
            return List.copyOf(envios);
        }
    }

    public void responder(Function<DestinoDoAviso, ResultadoDoEnvio> novo) {
        this.resultado = novo;
    }

    public void limpar() {
        envios.clear();
        resultado = d -> ResultadoDoEnvio.ENVIADO;
    }
}
