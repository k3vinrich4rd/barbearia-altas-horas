package io.github.k3vinrich4rd.altashoras.conta.cadastro;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.usuario.PerfilResposta;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/**
 * {@code PUT /me} (Fatia 11, A2, A5): a conta autoriza ou retira o uso do bairro e da cidade dela. Com a caixinha marcada, um
 * CEP e o texto vigente: consulta o CEP (fora da transação, R31), grava a localidade e a linha {@code REGIAO_ALCANCE} aceita.
 * Sem a caixinha, ou sem CEP, é revogação: a localidade do perfil e a região das reservas da conta somem (E3, pelo evento
 * {@code RegiaoRevogada}) e fica a linha de revogação no histórico. O CEP nunca é guardado. O id vem do token, nunca do corpo.
 */
@Service
class AtualizarRegiaoDoPerfil {

    private final UsuarioRepository usuarios;
    private final Consentimentos consentimentos;
    private final RegiaoDoCliente regioes;
    private final TransactionTemplate transacao;

    AtualizarRegiaoDoPerfil(UsuarioRepository usuarios, Consentimentos consentimentos, RegiaoDoCliente regioes,
            PlatformTransactionManager gerenciador) {
        this.usuarios = usuarios;
        this.consentimentos = consentimentos;
        this.regioes = regioes;
        this.transacao = new TransactionTemplate(gerenciador);
    }

    PerfilResposta atualizar(UUID usuarioId, AtualizarPerfilRequest pedido) {
        RegiaoDoCliente.conferirVersao(pedido.autorizaRegiao(), pedido.versaoTextoRegiao());
        boolean autorizou = RegiaoDoCliente.autorizou(pedido.autorizaRegiao(), pedido.cep());
        Optional<RegiaoDoCliente.Resolvida> regiao = autorizou ? regioes.resolver(pedido.cep()) : Optional.empty();
        return transacao.execute(status -> {
            Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.NAO_AUTENTICADO));
            if (!autorizou) {
                // O evento apaga a localidade do perfil e das reservas na mesma transação; a linha fica no histórico.
                consentimentos.registrarRegiao(usuarioId, false, Consentimentos.VERSAO_DO_TEXTO);
                return PerfilResposta.de(usuarios.findById(usuarioId).orElseThrow(), false);
            }
            consentimentos.registrarRegiao(usuarioId, true, pedido.versaoTextoRegiao());
            regiao.ifPresentOrElse(r -> usuario.resolverEndereco(r.bairro(), r.cidade(), r.uf(), r.municipioIbge()),
                    usuario::apagarRegiao);
            usuarios.saveAndFlush(usuario);
            return PerfilResposta.de(usuario, true);
        });
    }
}
