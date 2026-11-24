package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O canal de e-mail, hoje um STUB (E12, D172): a porta está pronta e a implementação existe, mas o canal só é chamado se
 * {@code email} estiver em {@code app.notificacao.canais} (desligado por padrão). Ligado sem {@code
 * app.notificacao.email.remetente} ou sem um {@link ClienteDeEmail}, devolve NAO_CONFIGURADO e registra uma linha SEM dado
 * pessoal. O e-mail leva só o que o aviso leva (serviço, dia e hora), nunca nome de cliente. O que falta para ligar de
 * verdade está em {@code docs/planejamento/notificacao-por-email.md}.
 */
@Component
public class EmailCanal implements CanalDeNotificacao {

    private static final Logger LOG = LoggerFactory.getLogger(EmailCanal.class);

    private final String remetente;
    private final @Nullable ClienteDeEmail cliente;

    @Autowired
    public EmailCanal(@Value("${app.notificacao.email.remetente:}") String remetente, ObjectProvider<ClienteDeEmail> cliente) {
        this(remetente, cliente.getIfAvailable());
    }

    public EmailCanal(String remetente, @Nullable ClienteDeEmail cliente) {
        this.remetente = remetente == null ? "" : remetente.strip();
        this.cliente = cliente;
    }

    @Override
    public Canal canal() {
        return Canal.EMAIL;
    }

    /** E-mail é um por conta, não um por aparelho. */
    @Override
    public boolean porAparelho() {
        return false;
    }

    @Override
    public ResultadoDoEnvio enviar(AvisoParaEnvio aviso, DestinoDoAviso destino) {
        if (remetente.isEmpty() || cliente == null) {
            LOG.warn("canal email ligado sem remetente ou sem provedor configurado; aviso não enviado");
            return ResultadoDoEnvio.NAO_CONFIGURADO;
        }
        String endereco = destino.email();
        if (endereco == null || endereco.isBlank()) {
            return ResultadoDoEnvio.DESTINO_INVALIDO;
        }
        try {
            cliente.enviar(remetente, endereco, aviso.titulo() + ": " + aviso.texto(), aviso.texto() + "\n\nAbra o painel: " + aviso.link());
            return ResultadoDoEnvio.ENVIADO;
        } catch (RuntimeException falha) {
            // Só a classe da falha: a mensagem pode trazer o endereço (R30).
            LOG.warn("canal email falhou: {}", falha.getClass().getSimpleName());
            return ResultadoDoEnvio.TENTAR_DEPOIS;
        }
    }

    @Override
    public String toString() {
        return "EmailCanal[" + (cliente == null ? "sem provedor" : "com provedor") + "]";
    }
}
