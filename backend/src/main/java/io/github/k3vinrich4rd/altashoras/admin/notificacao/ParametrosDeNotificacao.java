package io.github.k3vinrich4rd.altashoras.admin.notificacao;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.k3vinrich4rd.altashoras.admin.notificacao.canal.Canal;

/**
 * Toda a configuração da notificação (E7, E8, E9, E13, D175). A chave VAPID privada é segredo: o toString não a imprime
 * e nenhuma mensagem de erro a repete (R30).
 */
@Component
public class ParametrosDeNotificacao {

    private final String canais;
    private final String vapidPublica;
    private final String vapidPrivada;
    private final String assunto;
    private final List<String> hostsPermitidos;
    private final boolean exigirChaves;
    private final int retencaoAvisosDias;
    private final int retencaoInscricaoDias;
    private final int testeMax;
    private final Duration testeJanela;

    public ParametrosDeNotificacao(
            @Value("${app.notificacao.canais:webpush}") String canais,
            @Value("${app.notificacao.webpush.vapid-publica:}") String vapidPublica,
            @Value("${app.notificacao.webpush.vapid-privada:}") String vapidPrivada,
            @Value("${app.notificacao.webpush.assunto:https://barbearia-altas-horas.vercel.app}") String assunto,
            @Value("${app.notificacao.webpush.hosts-permitidos:fcm.googleapis.com,updates.push.services.mozilla.com,*.push.apple.com,*.notify.windows.com}") String hosts,
            @Value("${app.notificacao.webpush.exigir-chaves:false}") boolean exigirChaves,
            @Value("${app.notificacao.retencao-avisos-dias:30}") int retencaoAvisosDias,
            @Value("${app.notificacao.retencao-inscricao-dias:90}") int retencaoInscricaoDias,
            @Value("${app.limites.notificacao-teste-max:5}") int testeMax,
            @Value("${app.limites.notificacao-teste-janela:1h}") Duration testeJanela) {
        this.canais = canais;
        this.vapidPublica = vapidPublica.strip();
        this.vapidPrivada = vapidPrivada.strip();
        this.assunto = assunto;
        this.hostsPermitidos = Arrays.stream(hosts.split(",")).map(String::strip).filter(h -> !h.isEmpty()).toList();
        this.exigirChaves = exigirChaves;
        this.retencaoAvisosDias = retencaoAvisosDias;
        this.retencaoInscricaoDias = retencaoInscricaoDias;
        this.testeMax = testeMax;
        this.testeJanela = testeJanela;
    }

    /** O texto bruto da propriedade, para a mensagem de erro do Despachante. */
    public String canaisBrutos() {
        return canais;
    }

    /** Os nomes digitados, sem validar (o Despachante valida e derruba a partida se algum for desconhecido, E7). */
    public List<String> nomesDeCanais() {
        return Arrays.stream(canais.split(",")).map(String::strip).filter(n -> !n.isEmpty()).toList();
    }

    public boolean ligado(Canal canal) {
        return nomesDeCanais().contains(canal.nome());
    }

    public String vapidPublica() {
        return vapidPublica;
    }

    public String vapidPrivada() {
        return vapidPrivada;
    }

    public boolean vapidConfigurada() {
        return !vapidPublica.isEmpty() && !vapidPrivada.isEmpty();
    }

    public String assunto() {
        return assunto;
    }

    public List<String> hostsPermitidos() {
        return hostsPermitidos;
    }

    public boolean exigirChaves() {
        return exigirChaves;
    }

    public int retencaoAvisosDias() {
        return retencaoAvisosDias;
    }

    public int retencaoInscricaoDias() {
        return retencaoInscricaoDias;
    }

    public int testeMax() {
        return testeMax;
    }

    public Duration testeJanela() {
        return testeJanela;
    }

    @Override
    public String toString() {
        return "ParametrosDeNotificacao[canais=" + canais + ", chaves omitidas]";
    }
}
