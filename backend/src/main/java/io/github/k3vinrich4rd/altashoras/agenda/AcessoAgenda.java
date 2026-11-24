package io.github.k3vinrich4rd.altashoras.agenda;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Barbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroRepository;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * A política única de quem mexe na agenda de quem (Fatia 10A, ADR 0013, D151). Só esta classe liga uma conta ao barbeiro
 * dela; o painel, o alcance e os cadastros perguntam aqui e não repetem a regra.
 * <ul>
 *   <li>O ADMIN (o dono) vê e mexe na agenda de qualquer barbeiro: ver, criar reserva manual, fechar e cancelar.</li>
 *   <li>O BARBEIRO só mexe na própria. O {@code barbeiroId} dele sai sempre do token ({@code sub}, só barbeiro ativo),
 *       nunca da URL nem do corpo. O que é de outro, não existe ou a conta sem vínculo respondem 404 igual (R23, ADR 0005).</li>
 * </ul>
 */
@Component
class AcessoAgenda {

    private final BarbeiroRepository barbeiros;

    AcessoAgenda(BarbeiroRepository barbeiros) {
        this.barbeiros = barbeiros;
    }

    /**
     * O barbeiro que o pedido enxerga. ADMIN: todos ({@code null}) ou o filtrado, que precisa existir. BARBEIRO: sempre o
     * próprio; pedir outro é 404.
     */
    @Transactional(readOnly = true)
    @Nullable UUID alvo(PainelDaAgenda.Escopo escopo, @Nullable UUID barbeiroId) {
        if (escopo.admin()) {
            if (barbeiroId == null) {
                return null;
            }
            if (!barbeiros.existsById(barbeiroId)) {
                throw barbeiroNaoEncontrado();
            }
            return barbeiroId;
        }
        UUID proprio = proprio(escopo.usuarioId()).getId();
        if (barbeiroId != null && !barbeiroId.equals(proprio)) {
            throw barbeiroNaoEncontrado();
        }
        return proprio;
    }

    /**
     * O alcance (6C, E36): o ADMIN vê tudo ou filtra; o BARBEIRO vê sempre o próprio, e o {@code barbeiroId} que mandar é
     * trocado pelo dele, sem erro e sem sinal de que foi ignorado.
     */
    @Transactional(readOnly = true)
    @Nullable UUID alvoDoAlcance(PainelDaAgenda.Escopo escopo, @Nullable UUID barbeiroId) {
        return escopo.admin() ? alvo(escopo, barbeiroId) : proprio(escopo.usuarioId()).getId();
    }

    /** A reserva é do barbeiro da conta (ou a conta é ADMIN); senão responde como se não existisse. */
    @Transactional(readOnly = true)
    void exigirDonoDaReserva(PainelDaAgenda.Escopo escopo, UUID barbeiroDaReserva) {
        if (!escopo.admin() && !barbeiroDaReserva.equals(proprio(escopo.usuarioId()).getId())) {
            throw reservaNaoEncontrada();
        }
    }

    /** O barbeiro ativo ligado a esta conta. Sem vínculo, ou desligado: 404 (R23, A8). */
    @Transactional(readOnly = true)
    Barbeiro proprio(UUID usuarioId) {
        return barbeiros.findByUsuarioId(usuarioId).filter(Barbeiro::isAtivo)
                .orElseThrow(AcessoAgenda::barbeiroNaoEncontrado);
    }

    private static ExcecaoDeNegocio barbeiroNaoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado.");
    }

    private static ExcecaoDeNegocio reservaNaoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }
}
