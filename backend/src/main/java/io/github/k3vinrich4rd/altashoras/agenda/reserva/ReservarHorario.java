package io.github.k3vinrich4rd.altashoras.agenda.reserva;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import io.github.k3vinrich4rd.altashoras.cep.CepApi;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeLimite;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;
import io.github.k3vinrich4rd.altashoras.comum.valor.Cep;
import io.github.k3vinrich4rd.altashoras.privacidade.Consentimentos;
import io.github.k3vinrich4rd.altashoras.comum.valor.Email;
import io.github.k3vinrich4rd.altashoras.comum.valor.Telefone;
import io.github.k3vinrich4rd.altashoras.conta.Contas;
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
    /** O mesmo formato de {@code GET /cep/{cep}}: 8 dígitos, com ou sem hífen (E2). */
    private static final Pattern FORMATO_DO_CEP = Pattern.compile("[0-9]{5}-?[0-9]{3}");
    private static final Logger LOG = LoggerFactory.getLogger(ReservarHorario.class);

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
    private final CepApi cepApi;
    private final Contas contas;
    private final int limiteCep;
    private final Duration janelaCep;

    ReservarHorario(ConfiguracaoAgendaRepository configuracoes, ServicoRepository servicos, BarbeiroRepository barbeiros,
            FonteDeBloqueios bloqueios, ExpiracaoDeReservas expiracao, EntityManager em,
            PlatformTransactionManager gerenciador, LimitadorDeTentativas limitador, Clock clock, CepApi cepApi,
            Contas contas, @Value("${app.limites.guest-max:3}") int limiteGuest,
            @Value("${app.limites.guest-janela:1h}") Duration janelaGuest,
            @Value("${app.limites.cep-max:30}") int limiteCep, @Value("${app.limites.cep-janela:1m}") Duration janelaCep) {
        this.cepApi = cepApi;
        this.contas = contas;
        this.limiteCep = limiteCep;
        this.janelaCep = janelaCep;
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

    /**
     * Cliente logado: o dono vem do token (claim sub), nunca do corpo. A região é a do cadastro dele, copiada agora (E30):
     * a conta é lida FORA da transação da reserva e, se ele se mudar depois, a reserva não muda.
     */
    UUID comoCliente(UUID clienteId, Pedidos.Reserva pedido) {
        RegiaoDaReserva regiao = null;
        try {
            regiao = contas.regiaoDoCliente(clienteId)
                    .map(r -> new RegiaoDaReserva(r.bairro(), r.cidade(), r.uf(), r.municipioIbge(), r.versaoConsentimento()))
                    .orElse(null);
        } catch (RuntimeException falha) {
            // R31: a região nunca derruba a reserva. Do erro sobe só a classe (a mensagem pode trazer dado da conta, R30).
            LOG.warn("reserva de cliente sem região ({})", falha.getClass().getSimpleName());
        }
        return reservar(pedido, clienteId, null, regiao, null, null).getId();
    }

    /**
     * Visitante: até 3 reservas por hora por IP (R26, E9). Conta só reserva criada: um 409 ou 422 não gasta a cota,
     * o que importa em rede móvel, onde muita gente divide o mesmo IP (CGNAT). Devolve o id e o token, que só
     * aparece aqui.
     */
    ReservaDoVisitante comoVisitante(Pedidos.ReservaGuest pedido, String ip) {
        // O formato do CEP opcional é conferido antes de qualquer limite ou chamada externa (A64, E2).
        Cep cep = pedido.cep() == null ? null : cepValido(pedido.cep());
        // Fatia 11 (E1, E2): sem a caixinha de região o CEP é ignorado (nem consultado, nem guardado); com ela, o texto precisa ser o vigente.
        if (Boolean.TRUE.equals(pedido.autorizaRegiao()) && !Consentimentos.VERSAO_DO_TEXTO.equals(pedido.versaoTextoRegiao())) {
            throw ExcecaoDeNegocio.campoInvalido("versaoTextoRegiao",
                    "Informe a versão vigente do texto de autorização de região (" + Consentimentos.VERSAO_DO_TEXTO + ").");
        }
        boolean autorizou = cep != null && Boolean.TRUE.equals(pedido.autorizaRegiao());
        String chave = "guest|" + ip;
        long bloqueado = limitador.segundosBloqueado(chave, limiteGuest, janelaGuest);
        if (bloqueado > 0) {
            throw new ExcecaoDeLimite(bloqueado);
        }

        // Fora da transação da reserva (E30): nunca se segura conexão do banco esperando a rede, e o CEP nunca a impede (R31).
        Optional<RegiaoDaReserva> regiao = autorizou ? resolverRegiao(cep, ip, pedido.versaoTextoRegiao()) : Optional.empty();
        if (autorizou) {
            LOG.info("Reserva de visitante: região resolvida: {}", regiao.isPresent() ? "sim" : "não");
        }
        String token = TokenGuest.novo();
        Contato contato = new Contato(pedido.nome().strip(), Email.de(pedido.email()).valor(),
                Telefone.de(pedido.whatsapp()).digitos(), TokenGuest.hash(token));
        UUID id = reservar(pedido.horario(), null, contato, regiao.orElse(null), null, null).getId();
        limitador.registrar(chave, janelaGuest);
        return new ReservaDoVisitante(id, token);
    }

    /** O que a barbearia informa ao travar um horário combinado por fora (Fatia 6D). Nome e observação são dado pessoal. */
    public record Manual(UUID servicoId, UUID barbeiroId, OffsetDateTime inicio, @Nullable String nomeCliente,
            @Nullable String observacao, UUID criadoPor) {

        @Override
        public String toString() {
            return "Manual[dados pessoais omitidos]";
        }
    }

    /**
     * Reserva manual (D124): as mesmas regras de grade, jornada, almoço, folga e disputa da reserva do cliente, mas a
     * antecedência mínima não vale (quem trava é a barbearia) e nada é cobrado. {@code aoGravar} roda na mesma transação,
     * depois do flush: o painel grava a auditoria ali e, se ela falhar, a reserva desfaz.
     */
    public UUID comoBarbearia(Manual manual, Consumer<UUID> aoGravar) {
        return reservar(new Pedidos.Reserva(manual.servicoId(), manual.barbeiroId(), manual.inicio()), null, null, null,
                manual, aoGravar).getId();
    }

    private static Cep cepValido(String bruto) {
        if (!FORMATO_DO_CEP.matcher(bruto).matches()) {
            throw new ExcecaoDeNegocio(CodigoErro.CEP_INVALIDO);
        }
        return Cep.de(bruto);
    }

    /**
     * CEP inexistente, provedores fora, limite de consultas do IP estourado ou qualquer erro inesperado viram "sem região":
     * a reserva segue (R31). A consulta gasta a mesma cota de {@code GET /cep} do IP (H21): sem isso, um robô usaria a
     * reserva para martelar o ViaCEP. Do erro sobe só a classe, porque a mensagem de um cliente HTTP pode trazer o CEP (R30).
     */
    private Optional<RegiaoDaReserva> resolverRegiao(Cep cep, String ip, String versao) {
        String chaveDoCep = "cep|" + ip;
        if (limitador.segundosBloqueado(chaveDoCep, limiteCep, janelaCep) > 0) {
            return Optional.empty();
        }
        limitador.registrar(chaveDoCep, janelaCep);
        try {
            return cepApi.buscar(cep)
                    .filter(e -> e.cidade() != null && e.uf() != null)
                    .map(e -> new RegiaoDaReserva(e.bairro(), e.cidade(), e.uf(), e.municipioIbge(), versao));
        } catch (RuntimeException falha) {
            LOG.warn("reserva de visitante sem região ({})", falha.getClass().getSimpleName());
            return Optional.empty();
        }
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

    private Reserva reservar(Pedidos.Reserva pedido, @Nullable UUID clienteId, @Nullable Contato contato,
            @Nullable RegiaoDaReserva regiao, @Nullable Manual manual, @Nullable Consumer<UUID> aoGravar) {
        ConfiguracaoAgenda config = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
        Instant agora = clock.instant();
        Servico servico = servicoAtivo(pedido.servicoId());
        // Conferência rápida, sem trava: recusa cedo (e com os mesmos códigos) o que já está errado.
        validar(pedido, servico, config, agora, manual != null);

        for (int tentativa = 1; ; tentativa++) {
            try {
                return transacao.execute(status -> {
                    travarBarbeiroParaLeitura(pedido.barbeiroId());
                    // D80: depois da trava, relê tudo o que uma alteração do painel pode ter mudado (configuração,
                    // serviço, barbeiro ativo e jornada) e confere de novo, na mesma ordem de erros.
                    ConfiguracaoAgenda configAtual = configuracoes.findById(ConfiguracaoAgenda.ID_UNICO).orElseThrow();
                    Servico servicoAtual = servicoAtivo(pedido.servicoId());
                    Previa previa = validar(pedido, servicoAtual, configAtual, agora, manual != null);
                    conferirNaGrade(previa, servicoAtual, agora);
                    Horario horario = previa.horario();
                    Instant expiraEm = agora.plus(Duration.ofMinutes(configAtual.getExpiracaoPagamentoMin()));
                    expiracao.vencidasNoHorario(horario.barbeiroId(), horario.inicio(), horario.fimOcupacao(), agora);
                    Reserva reserva = manual == null
                            ? Reserva.nova(horario, clienteId, servicoAtual.getPrecoCentavos(), expiraEm, agora)
                            : Reserva.manual(horario, servicoAtual.getPrecoCentavos(), manual.nomeCliente(),
                                    manual.observacao(), manual.criadoPor(), agora);
                    if (regiao != null) {
                        reserva.definirRegiao(regiao);
                    }
                    em.persist(reserva);
                    if (contato != null) {
                        em.persist(new ReservaGuest(reserva.getId(), contato.nome(), contato.email(), contato.whatsapp(),
                                contato.tokenHash(), agora));
                    }
                    em.flush();
                    if (aoGravar != null) {
                        aoGravar.accept(reserva.getId());
                    }
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
    private Previa validar(Pedidos.Reserva pedido, Servico servico, ConfiguracaoAgenda config, Instant agora,
            boolean manual) {
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
        if (manual) {
            // D124: quem trava é a barbearia, então a antecedência mínima não vale; mas o horário não pode ter passado.
            if (inicio.isBefore(agora)) {
                throw new ExcecaoDeNegocio(CodigoErro.ANTECEDENCIA_INSUFICIENTE, "Este horário já passou.");
            }
        } else if (inicio.isBefore(agora.plus(Duration.ofMinutes(config.getAntecedenciaMinimaMin())))) {
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
