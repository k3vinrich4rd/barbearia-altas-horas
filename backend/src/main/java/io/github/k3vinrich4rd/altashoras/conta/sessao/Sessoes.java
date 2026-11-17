package io.github.k3vinrich4rd.altashoras.conta.sessao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.conta.sessao.SessaoResposta.UsuarioDaSessao;
import io.github.k3vinrich4rd.altashoras.conta.usuario.RefreshToken;
import io.github.k3vinrich4rd.altashoras.conta.usuario.RefreshTokenRepository;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.EmissorDeToken;
import io.github.k3vinrich4rd.altashoras.plataforma.EmissorDeToken.TokenEmitido;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * Entrar, renovar, sair e trocar a senha. As exceções de negócio NÃO desfazem a transação (noRollbackFor):
 * quando um refresh é reutilizado, a revogação da família precisa ficar gravada mesmo respondendo 401.
 */
@Service
@Transactional(noRollbackFor = ExcecaoDeNegocio.class)
public class Sessoes {

    private static final DateTimeFormatter FORMATO_DO_CONTRATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String PREFIXO = "r1_";

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder codificador;
    private final EmissorDeToken emissor;
    private final LimitadorDeTentativas limitador;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();
    private final String hashFalso;
    private final Duration validadeDoRefresh;
    private final int limiteDeLogin;
    private final int limiteDeLoginPorIp;
    private final Duration janelaDeLogin;
    private final ZoneId fuso;

    Sessoes(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, PasswordEncoder codificador,
            EmissorDeToken emissor, LimitadorDeTentativas limitador, Clock clock,
            @Value("${app.sessao.refresh-validade:7d}") Duration validadeDoRefresh,
            @Value("${app.limites.login-max:5}") int limiteDeLogin,
            @Value("${app.limites.login-ip-max:20}") int limiteDeLoginPorIp,
            @Value("${app.limites.login-janela:15m}") Duration janelaDeLogin,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.codificador = codificador;
        this.emissor = emissor;
        this.limitador = limitador;
        this.clock = clock;
        this.validadeDoRefresh = validadeDoRefresh;
        this.limiteDeLogin = limiteDeLogin;
        this.limiteDeLoginPorIp = limiteDeLoginPorIp;
        this.janelaDeLogin = janelaDeLogin;
        this.fuso = ZoneId.of(fuso);
        // Hash de uma senha que ninguém conhece, só para gastar o mesmo tempo quando o e-mail não existe (E4).
        this.hashFalso = codificador.encode(UUID.randomUUID().toString());
    }

    public SessaoResposta entrar(String emailBruto, String senha, String ip) {
        String email = Email.de(emailBruto).valor();
        String chaveDoEmail = "login|" + sha256(email) + "|" + ip;
        String chaveDoIp = "login-ip|" + ip;
        long bloqueado = Math.max(limitador.segundosBloqueado(chaveDoEmail, limiteDeLogin, janelaDeLogin),
                limitador.segundosBloqueado(chaveDoIp, limiteDeLoginPorIp, janelaDeLogin));
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }

        Optional<Usuario> achado = usuarios.findByEmail(email).filter(u -> u.isAtivo() && u.getSenhaHash() != null);
        // Sempre roda um BCrypt, exista a conta ou não: tempo de resposta e mensagem iguais nos dois casos.
        boolean senhaCerta = codificador.matches(senha, achado.map(Usuario::getSenhaHash).orElse(hashFalso));
        if (achado.isEmpty() || !senhaCerta) {
            limitador.registrar(chaveDoEmail, janelaDeLogin);
            limitador.registrar(chaveDoIp, janelaDeLogin);
            throw new ExcecaoDeNegocio(CodigoErro.CREDENCIAIS_INVALIDAS);
        }
        limitador.zerar(chaveDoEmail);
        return emitirPar(achado.get(), UUID.randomUUID());
    }

    public SessaoResposta renovar(String refreshBruto) {
        Instant agora = clock.instant();
        String hash = sha256(refreshBruto);
        if (refreshTokens.marcarUsado(hash, agora) == 0) {
            // Não venceu a disputa: ou o token não serve, ou já foi usado (alguém copiou).
            RefreshToken token = refreshTokens.findByHash(hash)
                    .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.REFRESH_INVALIDO));
            if (token.getUsadoEm() != null) {
                refreshTokens.revogarFamilia(token.getFamiliaId(), agora);
                throw new ExcecaoDeNegocio(CodigoErro.REFRESH_REUTILIZADO);
            }
            throw new ExcecaoDeNegocio(CodigoErro.REFRESH_INVALIDO);
        }
        RefreshToken usado = refreshTokens.findByHash(hash).orElseThrow();
        Usuario usuario = usuarios.findById(usado.getUsuarioId()).filter(Usuario::isAtivo).orElse(null);
        if (usuario == null) {
            refreshTokens.revogarFamilia(usado.getFamiliaId(), agora);
            throw new ExcecaoDeNegocio(CodigoErro.REFRESH_INVALIDO);
        }
        return emitirPar(usuario, usado.getFamiliaId());
    }

    /** Idempotente: token desconhecido também "sai", sem revelar se existia (E9). */
    public void encerrar(String refreshBruto) {
        refreshTokens.findByHash(sha256(refreshBruto))
                .ifPresent(token -> refreshTokens.revogarFamilia(token.getFamiliaId(), clock.instant()));
    }

    public void trocarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::isAtivo)
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.NAO_AUTENTICADO));
        if (!codificador.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new ExcecaoDeNegocio(CodigoErro.CREDENCIAIS_INVALIDAS, "Senha atual incorreta.");
        }
        usuario.trocarSenha(codificador.encode(novaSenha));
        usuarios.saveAndFlush(usuario);
        // Todas as sessões abertas param de renovar; o acesso já emitido vale no máximo mais 15 min (ADR 0006).
        refreshTokens.revogarDoUsuario(usuarioId, clock.instant());
    }

    private SessaoResposta emitirPar(Usuario usuario, UUID familia) {
        Instant agora = clock.instant();
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String refresh = PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = agora.plus(validadeDoRefresh);
        refreshTokens.save(new RefreshToken(usuario.getId(), familia, sha256(refresh), agora, expira));

        List<String> papeis = usuario.getPapeis().stream().sorted().toList();
        TokenEmitido acesso = emissor.emitir(usuario.getId(), papeis, usuario.getVersaoCredencial(),
                usuario.isDeveTrocarSenha());
        return new SessaoResposta(acesso.valor(), "Bearer", acesso.expiraEmSegundos(), refresh,
                FORMATO_DO_CONTRATO.format(expira.atZone(fuso)),
                new UsuarioDaSessao(usuario.getId(), usuario.getNome(), papeis), usuario.isDeveTrocarSenha());
    }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
