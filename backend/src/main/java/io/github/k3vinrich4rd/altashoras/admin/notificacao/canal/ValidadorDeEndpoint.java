package io.github.k3vinrich4rd.altashoras.admin.notificacao.canal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Defesa contra SSRF (E9, OWASP API Top 10): o backend faz uma requisição para uma URL que o USUÁRIO informou, então só
 * hosts de serviço de push são aceitos, em https. Lista exata ({@code fcm.googleapis.com}) ou com curinga na frente
 * ({@code *.push.apple.com}, que casa só subdomínios). IP, {@code localhost}, porta fora da 443, usuário na URL e
 * qualquer host fora da lista são recusados e NUNCA chamados.
 */
public final class ValidadorDeEndpoint {

    private static final Pattern SO_NUMEROS_E_PONTOS = Pattern.compile("[0-9.:\\[\\]]+");
    private static final int MAXIMO = 600;

    private final List<String> hosts;
    private final boolean exigirHttps;

    public ValidadorDeEndpoint(List<String> hostsPermitidos) {
        this(hostsPermitidos, true);
    }

    /** {@code exigirHttps=false} só nos testes, contra um servidor HTTP local; a produção usa o construtor acima. */
    public ValidadorDeEndpoint(List<String> hostsPermitidos, boolean exigirHttps) {
        this.hosts = hostsPermitidos.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        this.exigirHttps = exigirHttps;
    }

    public boolean aceita(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAXIMO) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException erro) {
            return false;
        }
        String esquema = uri.getScheme();
        String host = uri.getHost();
        if (esquema == null || host == null || uri.getUserInfo() != null) {
            return false;
        }
        if (exigirHttps ? !esquema.equalsIgnoreCase("https") : !(esquema.equalsIgnoreCase("https") || esquema.equalsIgnoreCase("http"))) {
            return false;
        }
        if (exigirHttps && uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String minusculo = host.toLowerCase(Locale.ROOT);
        if (SO_NUMEROS_E_PONTOS.matcher(minusculo).matches()) {
            return false; // endereço IP
        }
        return hosts.stream().anyMatch(permitido -> casa(permitido, minusculo));
    }

    private static boolean casa(String permitido, String host) {
        if (permitido.startsWith("*.")) {
            String sufixo = permitido.substring(1); // ".push.apple.com"
            return host.endsWith(sufixo) && host.length() > sufixo.length();
        }
        return permitido.equals(host);
    }

    /** A origem ({@code https://host}) que o JWT do VAPID declara como audiência. */
    public static String audiencia(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
