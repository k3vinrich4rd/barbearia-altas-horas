package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.admin.Ator;
import io.github.k3vinrich4rd.altashoras.admin.FormatoDeData;
import io.github.k3vinrich4rd.altashoras.admin.auditoria.Auditor;
import io.github.k3vinrich4rd.altashoras.admin.notificacao.ApagaNotificacoesDaConta;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.AlteracaoDeBarbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Telefone;
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
    private final ApagaNotificacoesDaConta notificacoes;
    private final ZoneId fuso;

    CadastroDeBarbeiros(PainelDaAgenda agenda, Contas contas, Auditor auditor, ApagaNotificacoesDaConta notificacoes,
            @Value("${app.fuso:America/Sao_Paulo}") String fuso) {
        this.agenda = agenda;
        this.contas = contas;
        this.auditor = auditor;
        this.notificacoes = notificacoes;
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional(readOnly = true)
    List<BarbeiroAdmin> listar() {
        List<BarbeiroCadastrado> barbeiros = agenda.barbeiros();
        Map<UUID, Instant> convites = contas.convitesPendentes();
        Set<UUID> contasAtivas = contas.comPapel(barbeiros.stream().map(BarbeiroCadastrado::usuarioId)
                .filter(Objects::nonNull).toList(), "BARBEIRO");
        return barbeiros.stream().map(b -> BarbeiroAdmin.de(b, FormatoDeData.formatar(convites.get(b.id()), fuso),
                b.usuarioId() != null && contasAtivas.contains(b.usuarioId()))).toList();
    }

    /** O barbeiro com o estado do acesso dele (convite pendente, conta ativa), para as respostas de criar e alterar. */
    private BarbeiroAdmin visto(BarbeiroCadastrado b) {
        Instant convite = contas.convitesPendentes().get(b.id());
        boolean contaAtiva = b.usuarioId() != null && contas.comPapel(List.of(b.usuarioId()), "BARBEIRO").contains(b.usuarioId());
        return BarbeiroAdmin.de(b, FormatoDeData.formatar(convite, fuso), contaAtiva);
    }

    @Transactional
    BarbeiroAdmin criar(Ator ator, BarbeiroRequest pedido) {
        exigirContaDeBarbeiro(pedido.usuarioId());
        BarbeiroCadastrado criado = agenda.criarBarbeiro(pedido.nomeExibicao(), pedido.usuarioId(), digitos(pedido.whatsapp()));
        // Só nomes de campos na auditoria: nunca o nome nem o número (R30).
        String campos = "nomeExibicao" + (pedido.usuarioId() == null ? "" : ",usuarioId")
                + (pedido.whatsapp() == null ? "" : ",whatsapp");
        auditor.registrar(ator, "BARBEIRO_CRIADO", "barbeiro", criado.id(), Map.of("campos", campos));
        return visto(criado);
    }

    @Transactional
    BarbeiroAdmin alterar(Ator ator, UUID id, BarbeiroAlteracaoRequest pedido) {
        BarbeiroCadastrado anterior = agenda.barbeiro(id);
        // Desligar (E10, H31): o acesso sai, o vínculo com a conta fica (o histórico aponta para ela) e o WhatsApp comercial some.
        boolean desligando = anterior.ativo() && !pedido.ativo();
        UUID usuario = desligando ? anterior.usuarioId() : pedido.usuarioId();
        if (!Objects.equals(usuario, anterior.usuarioId())) {
            exigirContaDeBarbeiro(usuario);
        }
        PainelDaAgenda.Alterado<BarbeiroCadastrado> alterado = agenda.alterarBarbeiro(id,
                new AlteracaoDeBarbeiro(pedido.nomeExibicao(), usuario, desligando ? null : digitos(pedido.whatsapp()),
                        pedido.ativo(), pedido.version()));
        if (desligando) {
            contas.revogarConvite(id);
            if (anterior.usuarioId() != null) {
                contas.desligarContaDeEquipe(anterior.usuarioId());
                notificacoes.apagar(anterior.usuarioId());
            }
            auditor.registrar(ator, "BARBEIRO_DESLIGADO", "barbeiro", id,
                    Map.of("campos", String.join(",", alterado.campos())));
        } else {
            auditor.registrar(ator, "BARBEIRO_ALTERADO", "barbeiro", id,
                    Map.of("campos", String.join(",", alterado.campos())));
        }
        return visto(alterado.item());
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

    /** O número é guardado só com dígitos (a máscara do formulário não entra no banco). */
    private static @Nullable String digitos(@Nullable String whatsapp) {
        return whatsapp == null ? null : Telefone.de(whatsapp).digitos();
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
