package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.cep.CepApi;
import io.github.k3vinrich4rd.altashoras.cep.Endereco;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;
import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.comum.valor.Telefone;
import io.github.k3vinrich4rd.altashoras.conta.usuario.PerfilResposta;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/** Caso de uso: criar a conta de um cliente. Sempre com o papel CLIENTE; o CPF não é pedido aqui (R9). */
@Service
public class CadastrarCliente {

    private static final Logger LOG = LoggerFactory.getLogger(CadastrarCliente.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final Consentimentos consentimentos;
    private final LimitadorDeTentativas limitador;
    private final CepApi cepApi;
    private final TransactionTemplate transacao;
    private final int limite;
    private final Duration janela;

    CadastrarCliente(UsuarioRepository usuarios, PasswordEncoder codificador, Consentimentos consentimentos,
            LimitadorDeTentativas limitador, CepApi cepApi, PlatformTransactionManager gerenciador,
            @Value("${app.limites.cadastro-max:5}") int limite,
            @Value("${app.limites.cadastro-janela:1h}") Duration janela) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.consentimentos = consentimentos;
        this.limitador = limitador;
        this.cepApi = cepApi;
        this.transacao = new TransactionTemplate(gerenciador);
        this.limite = limite;
        this.janela = janela;
    }

    public PerfilResposta cadastrar(CadastroRequest pedido, String ip) {
        String chave = "cadastro|" + ip;
        long bloqueado = limitador.segundosBloqueado(chave, limite, janela);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }
        limitador.registrar(chave, janela);

        String email = Email.de(pedido.email()).valor();
        if (usuarios.existsByEmail(email)) {
            throw new ExcecaoDeNegocio(CodigoErro.EMAIL_JA_CADASTRADO);
        }
        // O CEP é consultado ANTES de abrir a transação: nunca se segura conexão do banco esperando rede (E6).
        Optional<Endereco> endereco = pedido.cep() == null ? Optional.empty() : resolverEndereco(Cep.de(pedido.cep()));
        Usuario novo = new Usuario(pedido.nome().strip(), email, Telefone.de(pedido.whatsapp()).digitos(),
                codificador.encode(pedido.senha()), pedido.cep() == null ? null : Cep.de(pedido.cep()).digitos(),
                Set.of(Usuario.CLIENTE), false);
        // CEP "geral" de cidade pequena chega sem bairro: grava o que veio (cidade e uf sempre vêm, E4).
        endereco.ifPresent(e -> novo.resolverEndereco(e.bairro(), e.cidade(), e.uf()));
        try {
            // Conta e consentimento entram juntos ou não entram. O catch fica FORA da transação: dois cadastros
            // simultâneos com o mesmo e-mail passam pelo existsByEmail, e quem decide é o índice único (E18).
            return transacao.execute(status -> {
                usuarios.saveAndFlush(novo);
                consentimentos.registrarInicial(novo.getId(), Boolean.TRUE.equals(pedido.aceitaMarketing()));
                return PerfilResposta.de(novo);
            });
        } catch (DataIntegrityViolationException duplicado) {
            throw new ExcecaoDeNegocio(CodigoErro.EMAIL_JA_CADASTRADO);
        }
    }

    /**
     * R31: o CEP nunca derruba o cadastro. CEP inexistente, provedores fora ou qualquer erro inesperado viram "sem
     * endereço"; só o CEP é gravado. Do erro sobe só a classe (a mensagem pode trazer o CEP).
     */
    private Optional<Endereco> resolverEndereco(Cep cep) {
        try {
            return cepApi.buscar(cep);
        } catch (RuntimeException falha) {
            LOG.warn("cadastro sem endereço resolvido ({})", falha.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
