package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

/**
 * A porta do provedor de e-mail (E12): quando o Kevin criar a conta num provedor gratuito, o adaptador dele implementa esta
 * interface (um cliente HTTP da API do provedor ou o {@code JavaMailSender}) e vira um bean; nada mais muda. Hoje NÃO existe
 * implementação: o canal de e-mail está desligado e, ligado sem provedor, devolve NAO_CONFIGURADO. Quem implementa nunca
 * imprime o destinatário (R30). Lançar exceção significa falha passageira (o aviso tenta de novo).
 */
public interface ClienteDeEmail {

    void enviar(String remetente, String destinatario, String assunto, String texto);
}
