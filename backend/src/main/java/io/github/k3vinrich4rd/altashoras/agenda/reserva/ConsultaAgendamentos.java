package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Leituras de agendamento. Reserva de outra pessoa e token errado respondem igual a "não existe" (404, R23):
 * a resposta não confirma que o id é válido.
 */
@Service
@Transactional(readOnly = true)
class ConsultaAgendamentos {

    static final int TAMANHO_MAXIMO = 50;
    private static final String HASH_INEXISTENTE = "0".repeat(64);

    private final ReservaRepository reservas;
    private final ReservaGuestRepository visitantes;
    private final ZoneId fuso;

    ConsultaAgendamentos(ReservaRepository reservas, ReservaGuestRepository visitantes,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.reservas = reservas;
        this.visitantes = visitantes;
        this.fuso = ZoneId.of(fuso);
    }

    Pagina meus(UUID clienteId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ExcecaoDeNegocio(CodigoErro.VALIDACAO_INVALIDA,
                    "pagina deve ser >= 0 e tamanho de 1 a " + TAMANHO_MAXIMO + ".");
        }
        Page<LinhaAgendamento> resultado = reservas.doCliente(clienteId, PageRequest.of(pagina, tamanho));
        return new Pagina(resultado.map(linha -> AgendamentoResposta.de(linha, fuso)).getContent(), pagina, tamanho,
                resultado.getTotalElements());
    }

    AgendamentoResposta meu(UUID clienteId, UUID id) {
        return reservas.doCliente(clienteId, id).map(linha -> AgendamentoResposta.de(linha, fuso))
                .orElseThrow(ConsultaAgendamentos::naoEncontrado);
    }

    AgendamentoResposta doVisitante(UUID id, @Nullable String token) {
        // Compara sempre, mesmo sem visitante: o tempo de resposta não separa "id inexistente" de "token errado".
        String hashGuardado = visitantes.findById(id).map(ReservaGuest::getTokenHash).orElse(HASH_INEXISTENTE);
        if (!TokenGuest.confere(token, hashGuardado) || HASH_INEXISTENTE.equals(hashGuardado)) {
            throw naoEncontrado();
        }
        return reservas.doVisitante(id).map(linha -> AgendamentoResposta.de(linha, fuso))
                .orElseThrow(ConsultaAgendamentos::naoEncontrado);
    }

    AgendamentoResposta criadaPeloVisitante(UUID id, String token) {
        return reservas.doVisitante(id).map(linha -> AgendamentoResposta.de(linha, fuso, token)).orElseThrow();
    }

    private static ExcecaoDeNegocio naoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Agendamento não encontrado.");
    }

    record Pagina(List<AgendamentoResposta> itens, int pagina, int tamanho, long total) {
    }
}
