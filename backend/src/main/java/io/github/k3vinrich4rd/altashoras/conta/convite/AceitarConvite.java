package io.github.k3vinrich4rd.altashoras.conta.convite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.conta.ConviteAceito;
import io.github.k3vinrich4rd.altashoras.conta.ConviteRevogadoPorTentativas;
import io.github.k3vinrich4rd.altashoras.conta.convite.ConvitesDeConta.Pendente;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * Aceitar o convite do barbeiro (Fatia 10B). A ordem é a da spec (E6): a forma do corpo já foi conferida sem tocar no banco;
 * aqui vêm o limite por IP e só então a busca pelo SHA-256 do código, com o relógio. Tudo o que não é um aceite certo vira o
 * mesmo {@code 422 CONVITE_INVALIDO} (código inexistente, vencido, usado, revogado, e-mail de conta que não pode usar o
 * convite): ninguém descobre o que existe (E7).
 *
 * <p>O barbeiro digita o próprio e-mail e a senha (D-1). E-mail novo cria a conta com o papel BARBEIRO. E-mail de um cliente
 * só serve se a pessoa já está logada nessa conta (H32): o convite soma o papel e nunca define nem troca a senha. E-mail de
 * equipe (ADMIN ou BARBEIRO) ou de conta desativada não serve. A falha com o código certo soma uma tentativa (E8) e, na quinta,
 * revoga o convite.
 */
@Service
public class AceitarConvite {

    private enum Resultado { ACEITO, RECUSADO }

    private final ConvitesDeConta convites;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final LimitadorDeTentativas limitador;
    private final ApplicationEventPublisher eventos;
    private final ParametrosDoConvite parametros;
    private final Clock clock;
    private final TransactionTemplate transacao;
    private final int limite;
    private final Duration janela;

    AceitarConvite(ConvitesDeConta convites, UsuarioRepository usuarios, PasswordEncoder codificador,
            LimitadorDeTentativas limitador, ApplicationEventPublisher eventos, ParametrosDoConvite parametros, Clock clock,
            PlatformTransactionManager gerenciador, @Value("${app.limites.convite-max:10}") int limite,
            @Value("${app.limites.convite-janela:1h}") Duration janela) {
        this.convites = convites;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.limitador = limitador;
        this.eventos = eventos;
        this.parametros = parametros;
        this.clock = clock;
        this.transacao = new TransactionTemplate(gerenciador);
        this.limite = limite;
        this.janela = janela;
    }

    /** {@code contaLogada} é o {@code sub} do token, quando o pedido veio com um; nunca um parâmetro do corpo. */
    public void aceitar(ConviteAceiteRequest pedido, String ip, @Nullable UUID contaLogada) {
        String chave = "convite|" + ip;
        long bloqueado = limitador.segundosBloqueado(chave, limite, janela);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        // Conta todo pedido que chega aqui, certo ou errado: o limite protege o código de quem tenta adivinhar.
        limitador.registrar(chave, janela);

        String email = Email.de(pedido.email()).valor();
        String hash = CodigoDeConvite.hash(pedido.codigo());
        Resultado resultado;
        try {
            resultado = transacao.execute(status -> tentar(hash, email, pedido.senha(), contaLogada, ip));
        } catch (DataIntegrityViolationException corrida) {
            // Dois pedidos criando o mesmo e-mail ao mesmo tempo: o índice único decide, e quem perdeu recebe o 422 igual.
            throw new ExcecaoDeNegocio(CodigoErro.CONVITE_INVALIDO);
        }
        if (resultado != Resultado.ACEITO) {
            throw new ExcecaoDeNegocio(CodigoErro.CONVITE_INVALIDO);
        }
    }

    private Resultado tentar(String hash, String email, @Nullable String senha, @Nullable UUID contaLogada, String ip) {
        Instant agora = clock.instant();
        Optional<Pendente> achado = convites.travarValido(hash, agora);
        if (achado.isEmpty()) {
            return Resultado.RECUSADO;
        }
        Pendente convite = achado.get();
        Optional<Usuario> existente = usuarios.findByEmail(email);
        UUID usuarioId;
        if (existente.isPresent()) {
            Usuario conta = existente.get();
            // Mesmo custo do ramo "e-mail novo" (que gera o BCrypt da senha): o tempo da resposta não diz se o e-mail tem conta.
            codificador.encode(UUID.randomUUID().toString());
            boolean ehEquipe = conta.getPapeis().contains(Usuario.BARBEIRO) || conta.getPapeis().contains(Usuario.ADMIN);
            boolean logadaNela = conta.getId().equals(contaLogada);
            if (!conta.isAtivo() || ehEquipe || !logadaNela) {
                registrarFalha(convite, ip, agora);
                return Resultado.RECUSADO;
            }
            if (senha != null) {
                // Conta que já tem senha: o convite nunca a define nem a troca (H32).
                throw new ExcecaoDeNegocio(CodigoErro.CAMPO_DESCONHECIDO,
                        "O campo senha não vale para uma conta que já tem senha.");
            }
            conta.somarPapel(Usuario.BARBEIRO);
            usuarios.saveAndFlush(conta);
            usuarioId = conta.getId();
        } else {
            if (senha == null) {
                // Antes de consumir: o convite continua valendo e o mesmo código aceita uma senha boa (A15).
                throw ExcecaoDeNegocio.campoInvalido("senha", "obrigatório");
            }
            Usuario nova = new Usuario(convite.nome(), email, null, codificador.encode(senha), null,
                    Set.of(Usuario.BARBEIRO), false);
            usuarios.saveAndFlush(nova);
            usuarioId = nova.getId();
        }
        if (!convites.consumir(convite.id(), agora)) {
            return Resultado.RECUSADO;
        }
        eventos.publishEvent(new ConviteAceito(convite.barbeiroId(), usuarioId, ip));
        return Resultado.ACEITO;
    }

    private void registrarFalha(Pendente convite, String ip, Instant agora) {
        if (convites.registrarFalha(convite.id(), parametros.tentativasMax(), agora)) {
            eventos.publishEvent(new ConviteRevogadoPorTentativas(convite.barbeiroId(), convite.criadoPor(), ip));
        }
    }
}
