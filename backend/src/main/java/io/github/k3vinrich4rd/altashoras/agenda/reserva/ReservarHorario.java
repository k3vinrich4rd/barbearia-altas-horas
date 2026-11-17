package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Barbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroRepository;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Servico;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.ServicoRepository;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Regras;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.CalculadoraDeHorarios.Turno;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgendaRepository;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.FonteDeBloqueios;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.comum.valor.Telefone;
import io.github.k3vinrich4rd.altashoras.plataforma.LimitadorDeTentativas;

/**
 * Caso de uso "reservar este horário" (cliente logado ou visitante). Valida com a mesma CalculadoraDeHorarios da
 * tela, mas só com as folgas (E1): quem decide a disputa entre duas reservas é a constraint EXCLUDE do banco, o
 * único que vence uma corrida. A transação é aberta à mão (TransactionTemplate) para o erro do banco ser traduzido
 * FORA dela e o deadlock poder ser refeito numa transação nova (E4).
 * <p>
 * Contra a folga a constraint não ajuda (folga é outra tabela, E21): a transação começa travando a linha do barbeiro
 * com {@code FOR SHARE} e só DEPOIS relê configuração, serviço, barbeiro ativo, jornada e folga (D80), então a
 * jornada trocada ou o barbeiro desativado que ganhou a corrida recusa a reserva com os mesmos códigos de erro. A folga trava a mesma linha com {@code FOR UPDATE} antes de
 * conferir as reservas. Duas reservas não se bloqueiam ({@code FOR SHARE} é compatível com ele mesmo); reserva e
 * folga esperam uma à outra e, ao acordar, enxergam o que a outra gravou.
 */
@Service
public class ReservarHorario {

    /** SQLSTATE do PostgreSQL: violação de EXCLUDE (horário ocupado) e deadlock. */
    private static final String EXCLUSAO_VIOLADA = "23P01";
    private static final String DEADLOCK = "40P01";

    private final ConfiguracaoAgendaRepository configuracoes;
    private final ServicoRepository servicos;
    private final BarbeiroRepository barbeiros;
    private final FonteDeBloqueios bloqueios;
    private final ExpiracaoDeReservas expiracao;
    private final EntityManager em;
    private final TransactionTemplate transacao;
    private final LimitadorDeTentativas limitador;
    private final Clock clock;
    private final int limiteGuest;
    private final Duration janelaGuest;

    ReservarHorario(ConfiguracaoAgendaRepository configuracoes, ServicoRepository servicos, BarbeiroRepository barbeiros,
            FonteDeBloqueios bloqueios, ExpiracaoDeReservas expiracao, EntityManager em,
            PlatformTransactionManager gerenciador, LimitadorDeTentativas limitador, Clock clock,
            @Value("${app.limites.guest-max:3}") int limiteGuest,
            @Value("${app.limites.guest-janela:1h}") Duration janelaGuest) {
        this.configuracoes = configuracoes;
        this.servicos = servicos;
        this.barbeiros = barbeiros;
        this.bloqueios = bloqueios;
        this.expiracao = expiracao;
        this.em = em;
        this.transacao = new TransactionTemplate(gerenciador);
        this.limitador = limitador;
        this.clock = clock;
        this.limiteGuest = limiteGuest;
        this.janelaGuest = janelaGuest;
    }

    /** Cliente logado: o dono vem do token (claim sub), nunca do corpo. */
    UUID comoCliente(UUID clienteId, Pedidos.Reserva pedido) {
        return reservar(pedido, clienteId, null).getId();
    }

    /**
     * Visitante: até 3 reservas por hora por IP (R26, E9). Conta só reserva criada: um 409 ou 422 não gasta a cota,
     * o que importa em rede móvel, onde muita gente divide o mesmo IP (CGNAT). Devolve o id e o token, que só
     * aparece aqui.
     */
    ReservaDoVisitante comoVisitante(Pedidos.ReservaGuest pedido, String ip) {
        String chave = "guest|" + ip;
        long bloqueado = limitador.segundosBloqueado(chave, limiteGuest, janelaGuest);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }

        String token = TokenGuest.novo();
        Contato contato = new Contato(pedido.nome().strip(), Email.de(pedido.email()).valor(),
                Telefone.de(pedido.whatsapp()).digitos(), TokenGuest.hash(token));
        UUID id = reservar(pedido.horario(), null, contato).getId();
        limitador.registrar(chave, janelaGuest);
        return new ReservaDoVisitante(id, token);
    }

    record ReservaDoVisitante(UUID id, String token) {

        @Override
        public String toString() {
            return "ReservaDoVisitante[" + id + ", token omitido]";
        }
    }

    private record Contato(String nome, String email, String whatsapp, String tokenHash) {

        @Override
        public String toString() {
            return "Contato[dados pessoais omitidos]";
        }
    }

    private Reserva reservar(Pedidos.Reserva pedido, @Nullable UUID clienteId, @Nullable Contato contato) {
        ConfiguracaoAgenda config = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
        Instant agora = clock.instant();
        Servico servico = servicoAtivo(pedido.servicoId());
        // Conferência rápida, sem trava: recusa cedo (e com os mesmos códigos) o que já está errado.
        validar(pedido, servico, config, agora);

        for (int tentativa = 1; ; tentativa++) {
            try {
                return transacao.execute(status -> {
                    travarBarbeiroParaLeitura(pedido.barbeiroId());
                    // D80: depois da trava, relê tudo o que uma alteração do painel pode ter mudado (configuração,
                    // serviço, barbeiro ativo e jornada) e confere de novo, na mesma ordem de erros.
                    ConfiguracaoAgenda configAtual = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
                    Servico servicoAtual = servicoAtivo(pedido.servicoId());
                    Previa previa = validar(pedido, servicoAtual, configAtual, agora);
                    conferirNaGrade(previa, servicoAtual, agora);
                    Horario horario = previa.horario();
                    Instant expiraEm = agora.plus(Duration.ofMinutes(configAtual.getExpiracaoPagamentoMin()));
                    expiracao.vencidasNoHorario(horario.barbeiroId(), horario.inicio(), horario.fimOcupacao(), agora);
                    Reserva reserva = Reserva.nova(horario, clienteId, servicoAtual.getPrecoCentavos(), expiraEm, agora);
                    em.persist(reserva);
                    if (contato != null) {
                        em.persist(new ReservaGuest(reserva.getId(), contato.nome(), contato.email(), contato.whatsapp(),
                                contato.tokenHash(), agora));
                    }
                    em.flush();
                    return reserva;
                });
            } catch (ExcecaoDeNegocio regra) {
                throw regra;
            } catch (RuntimeException falha) {
                // O flush do EntityManager devolve a exceção do JPA sem tradução para DataAccessException: o que
                // decide é o SQLSTATE do PostgreSQL, achado na cadeia de causas.
                String estado = sqlState(falha);
                if (EXCLUSAO_VIOLADA.equals(estado)) {
                    throw new ExcecaoDeNegocio(CodigoErro.HORARIO_INDISPONIVEL);
                }
                if (DEADLOCK.equals(estado)) {
                    if (tentativa == 1) {
                        continue; // uma vez só (E4, D27): o PostgreSQL já desfez a transação que perdeu
                    }
                    throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_CONCORRENTE);
                }
                throw falha;
            }
        }
    }

    private Servico servicoAtivo(UUID servicoId) {
        Servico servico = servicos.findById(servicoId)
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Serviço não encontrado."));
        if (!servico.isAtivo()) {
            throw new ExcecaoDeNegocio(CodigoErro.SERVICO_INATIVO);
        }
        return servico;
    }

    /** O que {@link #validar} já conferiu e a transação ainda precisa (a folga, que só se confere com a trava). */
    private record Previa(Horario horario, ZoneId fuso, LocalDate data, List<Turno> turnos, Regras regras) {
    }

    /**
     * E1 e E2: a ordem das checagens decide o código de erro que o front recebe. Aqui vai tudo o que não depende da
     * folga; a grade (jornada, folga, granularidade) se confere depois, dentro da transação e com a trava do barbeiro.
     */
    private Previa validar(Pedidos.Reserva pedido, Servico servico, ConfiguracaoAgenda config, Instant agora) {
        Barbeiro barbeiro = barbeiros.listarAtivosComJornadas().stream()
                .filter(b -> b.getId().equals(pedido.barbeiroId()))
                .findFirst()
                .orElseThrow(() -> new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado."));

        ZoneId fuso = ZoneId.of(config.getFuso());
        Instant inicio = pedido.inicio().toInstant();
        LocalDate data = LocalDate.ofInstant(inicio, fuso);
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        if (data.isBefore(hoje) || data.isAfter(hoje.plusDays(config.getHorizonteDias()))) {
            throw new ExcecaoDeNegocio(CodigoErro.FORA_DO_HORIZONTE,
                    "A agenda aceita datas de hoje até " + config.getHorizonteDias() + " dias à frente.");
        }
        if (inicio.isBefore(agora.plus(Duration.ofMinutes(config.getAntecedenciaMinimaMin())))) {
            throw new ExcecaoDeNegocio(CodigoErro.ANTECEDENCIA_INSUFICIENTE,
                    "Reservas com pelo menos " + config.getAntecedenciaMinimaMin() / 60 + " h de antecedência.");
        }

        int diaDaSemana = data.getDayOfWeek().getValue();
        List<Turno> turnos = barbeiro.getJornadas().stream()
                .filter(j -> j.getDiaSemana() == diaDaSemana)
                .map(j -> new Turno(j.getHoraInicio(), j.getHoraFim()))
                .toList();
        // Antecedência 0 aqui: ela já foi checada acima, com o código de erro próprio.
        Regras regras = new Regras(config.getIntervaloEntreAtendimentosMin(), config.getGranularidadeMin(), 0);

        Instant fim = inicio.plus(Duration.ofMinutes(servico.getDuracaoMin()));
        Instant fimOcupacao = fim.plus(Duration.ofMinutes(config.getIntervaloEntreAtendimentosMin()));
        return new Previa(new Horario(barbeiro.getId(), servico.getId(), inicio, fim, fimOcupacao), fuso, data, turnos,
                regras);
    }

    /**
     * Trava a linha do barbeiro até o fim da transação (E21). Tem de ser a PRIMEIRA coisa dentro da transação: a folga
     * que está sendo criada nesse instante já terá sido gravada (ou esperará esta reserva) quando a conferência rodar.
     */
    private void travarBarbeiroParaLeitura(UUID barbeiroId) {
        em.createNativeQuery("SELECT 1 FROM barbeiro WHERE id = :id FOR SHARE").setParameter("id", barbeiroId)
                .getResultList();
    }

    /** A grade do dia com a folga já enxergada depois da trava: horário fora do expediente ou em folga é 422. */
    private void conferirNaGrade(Previa previa, Servico servico, Instant agora) {
        Horario horario = previa.horario();
        Instant inicioDoDia = previa.data().atStartOfDay(previa.fuso()).toInstant();
        Instant fimDoDia = previa.data().plusDays(1).atStartOfDay(previa.fuso()).toInstant();
        var folgas = bloqueios.folgasNoPeriodo(inicioDoDia, fimDoDia).getOrDefault(horario.barbeiroId(), List.of());
        boolean naGrade = CalculadoraDeHorarios.calcular(previa.data(), previa.fuso(), previa.turnos(), folgas,
                servico.getDuracaoMin(), previa.regras(), agora)
                .stream().anyMatch(livre -> livre.toInstant().equals(horario.inicio()));
        if (!naGrade) {
            throw new ExcecaoDeNegocio(CodigoErro.FORA_DO_EXPEDIENTE);
        }
    }

    private static @Nullable String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
