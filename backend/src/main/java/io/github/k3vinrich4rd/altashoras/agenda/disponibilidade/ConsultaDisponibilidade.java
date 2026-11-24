package io.github.k3vinrich4rd.altashoras.agenda.disponibilidade;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Barbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroRepository;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Servico;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.ServicoRepository;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Bloqueio;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Regras;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Turno;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.DisponibilidadeResposta.HorariosDoBarbeiro;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * Caso de uso "quais horários estão livres neste dia?". Só orquestra: valida, busca os dados em 4 consultas
 * (configuração, serviço, barbeiros com jornada, bloqueios), nunca uma por barbeiro, e entrega para a
 * CalculadoraDeHorarios. É uma foto: quem garante que dois clientes não pegam o mesmo horário é a constraint
 * do banco, na reserva (E11).
 */
@Service
public class ConsultaDisponibilidade {

    /** O ISO padrão do Java omite ":00" dos segundos; o contrato sempre mostra (E7). */
    private static final DateTimeFormatter FORMATO_DO_CONTRATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ConfiguracaoAgendaRepository configuracoes;
    private final ServicoRepository servicos;
    private final BarbeiroRepository barbeiros;
    private final FonteDeBloqueios bloqueios;
    private final Clock clock;

    ConsultaDisponibilidade(ConfiguracaoAgendaRepository configuracoes, ServicoRepository servicos,
            BarbeiroRepository barbeiros, FonteDeBloqueios bloqueios, Clock clock) {
        this.configuracoes = configuracoes;
        this.servicos = servicos;
        this.barbeiros = barbeiros;
        this.bloqueios = bloqueios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DisponibilidadeResposta consultar(UUID servicoId, LocalDate data, @Nullable UUID barbeiroId) {
        ConfiguracaoAgenda config = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
        ZoneId fuso = ZoneId.of(config.getFuso());
        Instant agora = clock.instant();

        Servico servico = servicos.findById(servicoId)
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Serviço não encontrado."));
        if (!servico.isAtivo()) {
            throw new ExcecaoDeNegocio(CodigoErro.SERVICO_INATIVO);
        }

        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        // D197: a única regra de data é não ser anterior a hoje (no fuso da barbearia); qualquer data futura é aceita.
        if (data.isBefore(hoje)) {
            throw new ExcecaoDeNegocio(CodigoErro.ANTECEDENCIA_INSUFICIENTE, "Este horário já passou.");
        }

        List<Barbeiro> ativos = barbeiros.listarAtivosComJornadas();
        if (barbeiroId != null) {
            ativos = ativos.stream().filter(b -> b.getId().equals(barbeiroId)).toList();
            if (ativos.isEmpty()) {
                throw new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado.");
            }
        }

        Instant inicioDoDia = data.atStartOfDay(fuso).toInstant();
        Instant fimDoDia = data.plusDays(1).atStartOfDay(fuso).toInstant();
        Map<UUID, List<Bloqueio>> bloqueiosDoDia = bloqueios.noPeriodo(inicioDoDia, fimDoDia, agora);

        Regras regras = new Regras(config.getIntervaloEntreAtendimentosMin(), config.getGranularidadeMin());
        int diaDaSemana = data.getDayOfWeek().getValue();

        List<HorariosDoBarbeiro> resposta = ativos.stream().map(barbeiro -> {
            List<Turno> turnos = barbeiro.getJornadas().stream()
                    .filter(j -> j.getDiaSemana() == diaDaSemana)
                    .map(j -> new Turno(j.getHoraInicio(), j.getHoraFim()))
                    .toList();
            List<String> horarios = CalculadoraDeHorarios.calcular(data, fuso, turnos,
                            bloqueiosDoDia.getOrDefault(barbeiro.getId(), List.of()), servico.getDuracaoMin(), regras, agora)
                    .stream().map(FORMATO_DO_CONTRATO::format).toList();
            return new HorariosDoBarbeiro(barbeiro.getId(), barbeiro.getNomeExibicao(), horarios);
        }).toList();

        return new DisponibilidadeResposta(data.toString(), config.getFuso(), servico.getId(), servico.getDuracaoMin(),
                resposta);
    }
}
