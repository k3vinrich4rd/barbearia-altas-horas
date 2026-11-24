package io.github.k3vinrich4rd.altashoras.conta;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.conta.convite.CodigoDeConvite;
import io.github.k3vinrich4rd.altashoras.conta.convite.ConvitesDeConta;
import io.github.k3vinrich4rd.altashoras.conta.convite.ParametrosDoConvite;
import io.github.k3vinrich4rd.altashoras.conta.usuario.RefreshTokenRepository;
import io.github.k3vinrich4rd.altashoras.conta.usuario.Usuario;
import io.github.k3vinrich4rd.altashoras.conta.usuario.UsuarioRepository;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;

/**
 * API pública da conta para os outros módulos (ADR 0002, D74): "esta conta existe e tem o papel X?". O painel
 * pergunta aqui antes de ligar uma conta a um barbeiro, sem ler a tabela {@code usuario}. Responde sim ou não e, para a
 * agenda copiar na reserva (6C), bairro, cidade e uf: nome, e-mail, WhatsApp e CEP nunca saem.
 */
@Service
public class Contas {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final ConvitesDeConta convites;
    private final ParametrosDoConvite parametros;
    private final Consentimentos consentimentos;
    private final Clock clock;

    Contas(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, ConvitesDeConta convites,
            ParametrosDoConvite parametros, Consentimentos consentimentos, Clock clock) {
        this.consentimentos = consentimentos;
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.convites = convites;
        this.parametros = parametros;
        this.clock = clock;
    }

    /**
     * Onde mora o cliente, só bairro, cidade, uf e município do IBGE (Fatia 11, E30): a reserva copia isto no momento em que
     * nasce, junto com a versão do texto de autorização que a conta aceitou (a prova, art. 8º, § 2º). O CEP nunca existe aqui
     * (não é guardado), e nome e contato não saem. O bairro e o município podem faltar; sem cidade ou uf a região não se conhece.
     */
    public record Regiao(@Nullable String bairro, String cidade, String uf, @Nullable String municipioIbge,
            String versaoConsentimento) {

        @Override
        public String toString() {
            return "Regiao[omitida]";
        }
    }

    /**
     * A região da conta, ou vazio quando o endereço não foi resolvido (R31), a conta foi excluída ou, principalmente, quando a
     * conta NÃO autoriza o uso (a linha mais recente de REGIAO_ALCANCE não é um aceite): sem consentimento a região não sai.
     */
    @Transactional(readOnly = true)
    public Optional<Regiao> regiaoDoCliente(UUID usuarioId) {
        Optional<String> versao = consentimentos.versaoDaRegiaoAceita(usuarioId);
        if (versao.isEmpty()) {
            return Optional.empty();
        }
        return usuarios.findById(usuarioId)
                .filter(u -> u.getCidade() != null && u.getUf() != null)
                .map(u -> new Regiao(u.getBairro(), u.getCidade(), u.getUf(), u.getRegiaoMunicipioIbge(), versao.get()));
    }

    /**
     * Os identificadores que o alcance usa para saber quando uma conta e um visitante são a MESMA pessoa (Fatia 11, E4): o
     * WhatsApp só com dígitos e o e-mail em minúsculas. Só de contas ativas; a exclusão já tirou os dois da conta anonimizada.
     * Saem apenas para a consulta agregada, que os mistura com uma chave que só vive na memória e nunca os guarda nem loga.
     */
    public record IdentificadoresDePessoa(@Nullable String whatsapp, @Nullable String email) {

        @Override
        public String toString() {
            return "IdentificadoresDePessoa[omitidos]";
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, IdentificadoresDePessoa> identificadoresDePessoa(Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, IdentificadoresDePessoa> resultado = new java.util.HashMap<>();
        for (Usuario u : usuarios.findAllById(usuarioIds)) {
            if (u.isAtivo()) {
                resultado.put(u.getId(), new IdentificadoresDePessoa(u.getWhatsapp(),
                        u.getEmail() == null ? null : u.getEmail().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        return resultado;
    }

    /** Das contas pedidas, as ativas com o papel: uma consulta só, para a lista de barbeiros do painel. */
    @Transactional(readOnly = true)
    public Set<UUID> comPapel(Collection<UUID> usuarioIds, String papel) {
        return usuarioIds.isEmpty() ? Set.of() : Set.copyOf(usuarios.ativasComPapel(usuarioIds, papel));
    }

    /**
     * Gera o convite de uso único do barbeiro (Fatia 10B): o código de 32 bytes, o SHA-256 no banco e a validade de
     * {@code app.conta.convite.validade}. É também o "reenviar": o convite pendente anterior é revogado na mesma transação.
     * O nome de exibição vira o nome da conta quando o barbeiro aceitar.
     */
    @Transactional
    public ConviteEmitido emitirConvite(UUID barbeiroId, String nomeDoBarbeiro, UUID geradoPor) {
        Instant agora = clock.instant();
        convites.revogarPendente(barbeiroId, agora);
        String codigo = CodigoDeConvite.gerar();
        Instant expiraEm = agora.plus(parametros.validade());
        convites.inserir(barbeiroId, nomeDoBarbeiro, CodigoDeConvite.hash(codigo), expiraEm, geradoPor);
        return new ConviteEmitido(codigo, expiraEm);
    }

    /** Revoga o convite pendente do barbeiro. Idempotente: sem convite pendente devolve {@code false}. */
    @Transactional
    public boolean revogarConvite(UUID barbeiroId) {
        return convites.revogarPendente(barbeiroId, clock.instant());
    }

    /** Quando vence o convite pendente de cada barbeiro (só os que ainda valem). */
    @Transactional(readOnly = true)
    public Map<UUID, Instant> convitesPendentes() {
        return convites.pendentes(clock.instant());
    }

    /**
     * O desligamento do barbeiro (E10, H31, H33): a conta perde o papel BARBEIRO e a versão da credencial sobe, o que
     * derruba o token antigo na hora. Se a conta é também de cliente (ou do dono), ela continua ativa com os dados e as reservas
     * dela. Se era só de equipe, a conta é desativada e anonimizada no mesmo passo: sem senha, sem sessão, sem identidade
     * social, nome e e-mail trocados por "lápide". A linha (id e papel) fica, porque reservas e auditoria apontam para ela.
     */
    @Transactional
    public void desligarContaDeEquipe(UUID usuarioId) {
        usuarios.findById(usuarioId).ifPresent(usuario -> {
            boolean temOutraConta = usuario.getPapeis().contains(Usuario.CLIENTE) || usuario.getPapeis().contains(Usuario.ADMIN);
            if (temOutraConta) {
                usuario.tirarPapel(Usuario.BARBEIRO);
            } else {
                refreshTokens.apagarDoUsuario(usuarioId);
                usuarios.apagarIdentidadesSociais(usuarioId);
                usuario.anonimizar(clock.instant());
            }
            usuarios.saveAndFlush(usuario);
        });
    }

    /**
     * O e-mail de login de uma conta ATIVA, só para o canal de e-mail da notificação ao barbeiro (Fatia 13, D-8: o e-mail que ele
     * mesmo digitou no convite). Quem chama não grava nem imprime o valor (R30). Conta inexistente ou desativada: vazio.
     */
    @Transactional(readOnly = true)
    public Optional<String> emailDaConta(UUID usuarioId) {
        return usuarios.findById(usuarioId).filter(Usuario::isAtivo).map(Usuario::getEmail);
    }

    /** Conta ativa com o papel pedido (CLIENTE, BARBEIRO ou ADMIN). Conta inexistente ou desativada: {@code false}. */
    @Transactional(readOnly = true)
    public boolean temPapel(UUID usuarioId, String papel) {
        return usuarios.ativoComPapel(usuarioId, papel);
    }
}
