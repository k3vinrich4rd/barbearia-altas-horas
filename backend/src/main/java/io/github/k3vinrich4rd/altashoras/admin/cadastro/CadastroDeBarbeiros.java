package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.AlteracaoDeBarbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.conta.Contas;

/**
 * Barbeiros e jornada do painel. O cadastro é da agenda; a pergunta "esta conta existe e é de um barbeiro?" é da
 * conta ({@link Contas}): o admin não lê a tabela de ninguém. A mudança e a auditoria ficam na MESMA transação (E11);
 * o detalhe da auditoria leva só nomes de campos, nunca o nome do barbeiro (R30).
 */
@Service
class CadastroDeBarbeiros {

    private static final int MAXIMO_DE_INTERVALOS = 28;
    private static final Pattern HORA = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    private final PainelDaAgenda agenda;
    private final Contas contas;
    private final Auditor auditor;

    CadastroDeBarbeiros(PainelDaAgenda agenda, Contas contas, Auditor auditor) {
        this.agenda = agenda;
        this.contas = contas;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    List<BarbeiroAdmin> listar() {
        return agenda.barbeiros().stream().map(BarbeiroAdmin::de).toList();
    }

    @Transactional
    BarbeiroAdmin criar(Ator ator, BarbeiroRequest pedido) {
        exigirContaDeBarbeiro(pedido.usuarioId());
        BarbeiroCadastrado criado = agenda.criarBarbeiro(pedido.nomeExibicao(), pedido.usuarioId());
        auditor.registrar(ator, "BARBEIRO_CRIADO", "barbeiro", criado.id(),
                Map.of("campos", pedido.usuarioId() == null ? "nomeExibicao" : "nomeExibicao,usuarioId"));
        return BarbeiroAdmin.de(criado);
    }

    @Transactional
    BarbeiroAdmin alterar(Ator ator, UUID id, BarbeiroAlteracaoRequest pedido) {
        exigirContaDeBarbeiro(pedido.usuarioId());
        PainelDaAgenda.Alterado<BarbeiroCadastrado> alterado = agenda.alterarBarbeiro(id,
                new AlteracaoDeBarbeiro(pedido.nomeExibicao(), pedido.usuarioId(), pedido.ativo(), pedido.version()));
        auditor.registrar(ator, "BARBEIRO_ALTERADO", "barbeiro", id,
                Map.of("campos", String.join(",", alterado.campos())));
        return BarbeiroAdmin.de(alterado.item());
    }

    /**
     * Substitui a semana inteira (E19). Formato errado é 400 com o campo apontado; sobreposição é 422 e vem da agenda,
     * junto com o 404 do barbeiro. A auditoria guarda só quantos intervalos ficaram.
     */
    @Transactional
    List<IntervaloJornada> substituirJornada(Ator ator, UUID barbeiroId, @Nullable List<IntervaloJornada> pedido) {
        List<Intervalo> intervalos = intervalosValidos(pedido);
        List<Intervalo> gravados = agenda.substituirJornada(barbeiroId, intervalos);
        auditor.registrar(ator, "JORNADA_SUBSTITUIDA", "barbeiro", barbeiroId,
                Map.of("intervalos", Integer.toString(gravados.size())));
        return gravados.stream().map(IntervaloJornada::de).toList();
    }

    /** Conta que não existe ou não tem o papel BARBEIRO: 400 no campo {@code usuarioId} (E18). */
    private void exigirContaDeBarbeiro(@Nullable UUID usuarioId) {
        if (usuarioId != null && !contas.temPapel(usuarioId, "BARBEIRO")) {
            throw ExcecaoDeNegocio.campoInvalido("usuarioId", "Informe uma conta existente com o papel BARBEIRO.");
        }
    }

    private static List<Intervalo> intervalosValidos(@Nullable List<IntervaloJornada> pedido) {
        if (pedido == null) {
            throw ExcecaoDeNegocio.campoInvalido("jornada", "Envie a lista de intervalos (pode ser vazia).");
        }
        if (pedido.size() > MAXIMO_DE_INTERVALOS) {
            throw ExcecaoDeNegocio.campoInvalido("jornada",
                    "No máximo " + MAXIMO_DE_INTERVALOS + " intervalos por semana.");
        }
        List<Intervalo> intervalos = new ArrayList<>();
        for (int i = 0; i < pedido.size(); i++) {
            IntervaloJornada item = pedido.get(i);
            String onde = "jornada[" + i + "]";
            if (item == null) {
                throw ExcecaoDeNegocio.campoInvalido(onde, "obrigatório");
            }
            Integer dia = item.diaSemana();
            if (dia == null || dia < 1 || dia > 7) {
                throw ExcecaoDeNegocio.campoInvalido(onde + ".diaSemana", "deve ser de 1 (segunda) a 7 (domingo)");
            }
            LocalTime inicio = hora(item.horaInicio(), onde + ".horaInicio");
            LocalTime fim = hora(item.horaFim(), onde + ".horaFim");
            if (!fim.isAfter(inicio)) {
                throw ExcecaoDeNegocio.campoInvalido(onde + ".horaFim", "deve ser depois da hora de início");
            }
            intervalos.add(new Intervalo(dia, inicio, fim));
        }
        return intervalos;
    }

    private static LocalTime hora(@Nullable String texto, String campo) {
        if (texto == null || !HORA.matcher(texto).matches()) {
            throw ExcecaoDeNegocio.campoInvalido(campo, "deve ser uma hora válida no formato HH:mm");
        }
        return LocalTime.parse(texto);
    }
}
