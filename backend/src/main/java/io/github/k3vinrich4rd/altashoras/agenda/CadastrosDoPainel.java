package io.github.k3vinrich4rd.altashoras.agenda;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Alterado;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.AlteracaoDeBarbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.AlteracaoDeServico;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.BarbeiroCadastrado;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConfiguracaoCadastrada;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ConflitoDaFolga;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaCadastrada;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.FolgaGravada;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.Intervalo;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.NovaConfiguracao;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.NovoServico;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.PedidoDeFolga;
import io.github.k3vinrich4rd.altashoras.agenda.PainelDaAgenda.ServicoCadastrado;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Barbeiro;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.BarbeiroRepository;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.Servico;
import io.github.k3vinrich4rd.altashoras.agenda.catalogo.ServicoRepository;
import io.github.k3vinrich4rd.altashoras.agenda.disponibilidade.ConfiguracaoAgenda;
import io.github.k3vinrich4rd.altashoras.comum.erro.CodigoErro;
import io.github.k3vinrich4rd.altashoras.comum.erro.ExcecaoDeNegocio;

/**
 * As regras dos cadastros do painel (Fatia 5B): serviço, barbeiro, jornada, folga e configuração. Visível só dentro
 * do módulo: quem chama de fora usa a fachada {@link PainelDaAgenda}. Cada método roda na transação de quem chama (o
 * admin grava a auditoria nela, E11) e nunca grava auditoria: isso é do painel.
 * <p>
 * Edição concorrente (E23): a linha é travada ({@code FOR UPDATE}/{@code NO KEY UPDATE}) e a {@code version} da tela é
 * comparada à atual; quem chega depois lê a versão nova e recebe 409. Violação de único do banco ({@code 23505}) vira
 * 409 do código certo, nunca 500.
 */
@Service
class CadastrosDoPainel {

    private static final String UNICO_VIOLADO = "23505";

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final ServicoRepository servicos;
    private final BarbeiroRepository barbeiros;
    private final Clock clock;
    private final AcessoAgenda acesso;

    CadastrosDoPainel(EntityManager em, JdbcClient jdbc, ServicoRepository servicos, BarbeiroRepository barbeiros,
            Clock clock, AcessoAgenda acesso) {
        this.acesso = acesso;
        this.em = em;
        this.jdbc = jdbc;
        this.servicos = servicos;
        this.barbeiros = barbeiros;
        this.clock = clock;
    }

    // ---- serviços ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<ServicoCadastrado> servicos() {
        return servicos.listarTodos().stream().map(CadastrosDoPainel::servicoDe).toList();
    }

    @Transactional
    ServicoCadastrado criarServico(NovoServico novo) {
        if (servicos.existeComNome(novo.nome())) {
            throw new ExcecaoDeNegocio(CodigoErro.NOME_JA_EXISTE);
        }
        Servico servico = Servico.novo(novo.nome(), novo.descricao(), novo.duracaoMin(), novo.precoCentavos(),
                clock.instant());
        em.persist(servico);
        descarregar(CodigoErro.NOME_JA_EXISTE);
        return servicoDe(servico);
    }

    @Transactional
    Alterado<ServicoCadastrado> alterarServico(UUID id, AlteracaoDeServico alteracao) {
        Servico servico = em.find(Servico.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (servico == null) {
            throw new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Serviço não encontrado.");
        }
        if (servico.getVersion() != alteracao.version()) {
            throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_DE_VERSAO);
        }
        if (servicos.existeOutroComNome(alteracao.nome(), id)) {
            throw new ExcecaoDeNegocio(CodigoErro.NOME_JA_EXISTE);
        }
        List<String> campos = camposAlterados(
                mudou("nome", servico.getNome(), alteracao.nome()),
                mudou("descricao", servico.getDescricao(), alteracao.descricao()),
                mudou("duracaoMin", servico.getDuracaoMin(), alteracao.duracaoMin()),
                mudou("precoCentavos", servico.getPrecoCentavos(), alteracao.precoCentavos()),
                mudou("ativo", servico.isAtivo(), alteracao.ativo()));
        servico.alterar(alteracao.nome(), alteracao.descricao(), alteracao.duracaoMin(), alteracao.precoCentavos(),
                alteracao.ativo(), clock.instant());
        descarregar(CodigoErro.NOME_JA_EXISTE);
        return new Alterado<>(servicoDe(servico), campos);
    }

    private static ServicoCadastrado servicoDe(Servico s) {
        return new ServicoCadastrado(s.getId(), s.getNome(), s.getDescricao(), s.getDuracaoMin(), s.getPrecoCentavos(),
                s.isAtivo(), s.getVersion());
    }

    // ---- barbeiros e jornada ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<BarbeiroCadastrado> barbeiros() {
        return barbeiros.listarTodosComJornadas().stream()
                .map(b -> barbeiroDe(b, b.getJornadas().stream()
                        .map(j -> new Intervalo(j.getDiaSemana(), j.getHoraInicio(), j.getHoraFim())).toList()))
                .toList();
    }

    /** Um barbeiro pelo id, com a jornada. Inexistente: 404. */
    @Transactional(readOnly = true)
    BarbeiroCadastrado barbeiro(UUID id) {
        return barbeiros().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow(CadastrosDoPainel::barbeiroNaoEncontrado);
    }

    @Transactional
    BarbeiroCadastrado criarBarbeiro(String nomeExibicao, @Nullable UUID usuarioId, @Nullable String whatsapp) {
        if (usuarioId != null && barbeiros.existeComUsuario(usuarioId)) {
            throw new ExcecaoDeNegocio(CodigoErro.USUARIO_JA_VINCULADO);
        }
        Barbeiro barbeiro = Barbeiro.novo(nomeExibicao, usuarioId, whatsapp, clock.instant());
        em.persist(barbeiro);
        descarregar(CodigoErro.USUARIO_JA_VINCULADO);
        return barbeiroDe(barbeiro, List.of());
    }

    @Transactional
    Alterado<BarbeiroCadastrado> alterarBarbeiro(UUID id, AlteracaoDeBarbeiro alteracao) {
        Barbeiro barbeiro = em.find(Barbeiro.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (barbeiro == null) {
            throw barbeiroNaoEncontrado();
        }
        if (barbeiro.getVersion() != alteracao.version()) {
            throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_DE_VERSAO);
        }
        UUID usuario = alteracao.usuarioId();
        if (usuario != null && barbeiros.existeOutroComUsuario(usuario, id)) {
            throw new ExcecaoDeNegocio(CodigoErro.USUARIO_JA_VINCULADO);
        }
        List<String> campos = camposAlterados(
                mudou("nomeExibicao", barbeiro.getNomeExibicao(), alteracao.nomeExibicao()),
                mudou("usuarioId", barbeiro.getUsuarioId(), usuario),
                mudou("whatsapp", barbeiro.getWhatsapp(), alteracao.whatsapp()),
                mudou("ativo", barbeiro.isAtivo(), alteracao.ativo()));
        barbeiro.alterar(alteracao.nomeExibicao(), usuario, alteracao.whatsapp(), alteracao.ativo(), clock.instant());
        descarregar(CodigoErro.USUARIO_JA_VINCULADO);
        return new Alterado<>(barbeiroDe(barbeiro, jornadaDe(id)), campos);
    }

    /**
     * Substitui a semana inteira (E19). A linha do barbeiro é travada: dois PUT ao mesmo tempo não misturam os
     * intervalos (o segundo apagaria só o que enxerga). Reservas já feitas fora da nova jornada continuam valendo.
     */
    @Transactional
    List<Intervalo> substituirJornada(UUID barbeiroId, List<Intervalo> intervalos) {
        travarBarbeiro(barbeiroId);
        List<Intervalo> ordenados = intervalos.stream()
                .sorted(Comparator.comparingInt(Intervalo::diaSemana).thenComparing(Intervalo::horaInicio)).toList();
        for (int i = 1; i < ordenados.size(); i++) {
            Intervalo anterior = ordenados.get(i - 1);
            Intervalo atual = ordenados.get(i);
            // Encostar (12:00 e 12:00) não é sobrepor: o intervalo é meio aberto, como na disponibilidade.
            if (anterior.diaSemana() == atual.diaSemana() && atual.horaInicio().isBefore(anterior.horaFim())) {
                throw new ExcecaoDeNegocio(CodigoErro.JORNADA_SOBREPOSTA);
            }
        }
        jdbc.sql("DELETE FROM jornada WHERE barbeiro_id = :barbeiro").param("barbeiro", barbeiroId).update();
        for (Intervalo intervalo : ordenados) {
            jdbc.sql("""
                    INSERT INTO jornada (barbeiro_id, dia_semana, hora_inicio, hora_fim)
                    VALUES (:barbeiro, :dia, :inicio, :fim)
                    """)
                    .param("barbeiro", barbeiroId).param("dia", (short) intervalo.diaSemana())
                    .param("inicio", intervalo.horaInicio()).param("fim", intervalo.horaFim()).update();
        }
        return ordenados;
    }

    /**
     * A jornada do barbeiro ligado a esta conta (D142): é o que a agenda do BARBEIRO precisa para listrar o almoço. Conta
     * sem vínculo é 404, como no resto do painel (R23).
     */
    @Transactional(readOnly = true)
    PainelDaAgenda.JornadaDoBarbeiro minhaJornada(UUID usuarioId) {
        Barbeiro barbeiro = acesso.proprio(usuarioId);
        return new PainelDaAgenda.JornadaDoBarbeiro(barbeiro.getId(), barbeiro.getNomeExibicao(),
                jornadaDe(barbeiro.getId()));
    }

    private List<Intervalo> jornadaDe(UUID barbeiroId) {
        return jdbc.sql("""
                SELECT dia_semana, hora_inicio, hora_fim FROM jornada
                WHERE barbeiro_id = :barbeiro ORDER BY dia_semana, hora_inicio
                """).param("barbeiro", barbeiroId)
                .query((rs, n) -> new Intervalo(rs.getInt("dia_semana"), rs.getObject("hora_inicio", LocalTime.class),
                        rs.getObject("hora_fim", LocalTime.class)))
                .list();
    }

    private static BarbeiroCadastrado barbeiroDe(Barbeiro b, List<Intervalo> jornada) {
        List<Intervalo> ordenada = jornada.stream()
                .sorted(Comparator.comparingInt(Intervalo::diaSemana).thenComparing(Intervalo::horaInicio)).toList();
        return new BarbeiroCadastrado(b.getId(), b.getNomeExibicao(), b.getUsuarioId(), b.getWhatsapp(), b.isAtivo(),
                b.getVersion(), ordenada);
    }

    // ---- folgas -----------------------------------------------------------------------------------------------------

    /** Reserva manual sem nome (ou já anonimizada): o painel mostra este texto (o mesmo do AgendamentoAdmin). */
    private static final String SEM_NOME = "Sem nome";

    @Transactional(readOnly = true)
    List<FolgaCadastrada> folgas(UUID barbeiroId) {
        if (!barbeiroExiste(barbeiroId)) {
            throw barbeiroNaoEncontrado();
        }
        ZoneId fuso = ZoneId.of(em.find(ConfiguracaoAgenda.class, ConfiguracaoAgenda.ID_UNICO).getFuso());
        Instant hoje = clock.instant().atZone(fuso).toLocalDate().atStartOfDay(fuso).toInstant();
        return jdbc.sql("""
                SELECT id, inicio, fim, motivo, tipo, atualizado_em FROM folga
                WHERE barbeiro_id = :barbeiro AND fim > :hoje ORDER BY inicio, id
                """).param("barbeiro", barbeiroId).param("hoje", utc(hoje))
                .query((rs, n) -> folgaDe(rs)).list();
    }

    /**
     * E20 e E21 (Fatia 5), E4 e E10 (Fatia 15). A transação trava a linha do barbeiro com {@code FOR UPDATE} ANTES de
     * conferir as reservas; o {@code ReservarHorario} trava a mesma linha com {@code FOR SHARE} ANTES de conferir a
     * folga. Cada lado espera o outro terminar e então enxerga o que ele gravou: nunca existem folga e reserva ativa
     * nova no mesmo horário. Com {@code manterReservas}, as reservas ativas que já estavam no período ficam como estão
     * e voltam na resposta, lidas dentro da trava.
     */
    @Transactional
    FolgaGravada criarFolga(UUID barbeiroId, UUID criadaPor, PedidoDeFolga pedido) {
        travarBarbeiro(barbeiroId);
        Instant agora = clock.instant();
        validarPeriodo(pedido.inicio(), pedido.fim(), agora);
        List<ConflitoDaFolga> conflitos = conflitos(barbeiroId, pedido.inicio(), pedido.fim(), agora);
        if (!conflitos.isEmpty() && !pedido.manterReservas()) {
            throw new FolgaComReservas(conflitos);
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO folga (id, barbeiro_id, inicio, fim, motivo, tipo, criado_por, criado_em)
                VALUES (:id, :barbeiro, :inicio, :fim, :motivo, :tipo, :por, :agora)
                """).param("id", id).param("barbeiro", barbeiroId).param("inicio", utc(pedido.inicio()))
                .param("fim", utc(pedido.fim())).param("motivo", pedido.motivo()).param("tipo", pedido.tipo())
                .param("por", criadaPor).param("agora", utc(agora)).update();
        return new FolgaGravada(new FolgaCadastrada(id, pedido.inicio(), pedido.fim(), pedido.motivo(), pedido.tipo(),
                null), conflitos);
    }

    /**
     * PUT (ADR 0015, seção 3): substitui início, fim, motivo e tipo, com as mesmas validações e a mesma trava da
     * criação, então um PUT que aumenta o período nunca passa junto com uma reserva nova nele (A21). Folga de outro
     * barbeiro, ou que não existe, é 404 (R23). Sem controle de versão: a última gravação vale (E13).
     */
    @Transactional
    FolgaGravada alterarFolga(UUID barbeiroId, UUID folgaId, UUID alteradaPor, PedidoDeFolga pedido) {
        travarBarbeiro(barbeiroId);
        boolean existe = jdbc.sql("SELECT count(*) FROM folga WHERE id = :id AND barbeiro_id = :barbeiro")
                .param("id", folgaId).param("barbeiro", barbeiroId).query(Long.class).single() > 0;
        if (!existe) {
            throw folgaNaoEncontrada();
        }
        Instant agora = clock.instant();
        validarPeriodo(pedido.inicio(), pedido.fim(), agora);
        List<ConflitoDaFolga> conflitos = conflitos(barbeiroId, pedido.inicio(), pedido.fim(), agora);
        if (!conflitos.isEmpty() && !pedido.manterReservas()) {
            throw new FolgaComReservas(conflitos);
        }
        jdbc.sql("""
                UPDATE folga SET inicio = :inicio, fim = :fim, motivo = :motivo, tipo = :tipo,
                                 atualizado_por = :por, atualizado_em = :agora
                WHERE id = :id AND barbeiro_id = :barbeiro
                """).param("inicio", utc(pedido.inicio())).param("fim", utc(pedido.fim()))
                .param("motivo", pedido.motivo()).param("tipo", pedido.tipo()).param("por", alteradaPor)
                .param("agora", utc(agora)).param("id", folgaId).param("barbeiro", barbeiroId).update();
        return new FolgaGravada(new FolgaCadastrada(folgaId, pedido.inicio(), pedido.fim(), pedido.motivo(),
                pedido.tipo(), agora), conflitos);
    }

    /** A prévia (E12): só lê, sem trava. Barbeiro inexistente é 404; período inválido é 400, como no POST (E14). */
    @Transactional(readOnly = true)
    List<ConflitoDaFolga> conflitosDaFolga(UUID barbeiroId, Instant inicio, Instant fim) {
        if (!barbeiroExiste(barbeiroId)) {
            throw barbeiroNaoEncontrado();
        }
        Instant agora = clock.instant();
        validarPeriodo(inicio, fim, agora);
        return conflitos(barbeiroId, inicio, fim, agora);
    }

    /**
     * Não trava a linha do barbeiro de propósito: remover folga só LIBERA horário, e uma reserva que chegue durante a
     * remoção ou a vê (e recusa, como se ela ainda existisse) ou não a vê (e passa); nunca cria folga sobre reserva.
     */
    @Transactional
    void removerFolga(UUID barbeiroId, UUID folgaId) {
        int removidas = jdbc.sql("DELETE FROM folga WHERE id = :id AND barbeiro_id = :barbeiro")
                .param("id", folgaId).param("barbeiro", barbeiroId).update();
        if (removidas == 0) {
            throw folgaNaoEncontrada();
        }
    }

    /** E14: fim depois do início e não todo no passado; vale para o POST, o PUT e a prévia. */
    private static void validarPeriodo(Instant inicio, Instant fim, Instant agora) {
        if (!fim.isAfter(inicio)) {
            throw ExcecaoDeNegocio.campoInvalido("fim", "O fim da folga deve ser depois do início.");
        }
        if (!fim.isAfter(agora)) {
            throw ExcecaoDeNegocio.campoInvalido("fim", "A folga não pode estar toda no passado.");
        }
    }

    /**
     * Reserva ativa = a mesma definição da constraint e da disponibilidade (R15, E3): confirmada, ou aguardando
     * pagamento com o prazo ainda aberto. Meio aberto: a folga que começa quando a reserva termina é permitida (R2).
     * Só o que o contrato pede: nada de e-mail, CPF, WhatsApp ou observação.
     */
    private List<ConflitoDaFolga> conflitos(UUID barbeiroId, Instant inicio, Instant fim, Instant agora) {
        return jdbc.sql("""
                SELECT r.id, r.inicio, r.fim, r.servico_id, s.nome AS servico_nome, r.origem, r.status,
                       COALESCE(g.nome, u.nome, r.nome_cliente) AS cliente_nome
                FROM reserva r
                JOIN servico s ON s.id = r.servico_id
                LEFT JOIN usuario u ON u.id = r.cliente_id
                LEFT JOIN reserva_guest g ON g.reserva_id = r.id
                WHERE r.barbeiro_id = :barbeiro AND r.inicio < :fim AND r.fim_ocupacao > :inicio
                  AND (r.status = 'CONFIRMADO' OR (r.status = 'AGUARDANDO_PAGAMENTO' AND r.expira_em > :agora))
                ORDER BY r.inicio, r.id
                """).param("barbeiro", barbeiroId).param("inicio", utc(inicio)).param("fim", utc(fim))
                .param("agora", utc(agora))
                .query((rs, n) -> {
                    String nome = rs.getString("cliente_nome");
                    return new ConflitoDaFolga(rs.getObject("id", UUID.class),
                            rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                            rs.getObject("fim", OffsetDateTime.class).toInstant(), rs.getObject("servico_id", UUID.class),
                            rs.getString("servico_nome"), nome == null || nome.isBlank() ? SEM_NOME : nome,
                            rs.getString("origem"), rs.getString("status"));
                })
                .list();
    }

    private static FolgaCadastrada folgaDe(java.sql.ResultSet rs) throws SQLException {
        OffsetDateTime atualizada = rs.getObject("atualizado_em", OffsetDateTime.class);
        return new FolgaCadastrada(rs.getObject("id", UUID.class), rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                rs.getObject("fim", OffsetDateTime.class).toInstant(), rs.getString("motivo"), rs.getString("tipo"),
                atualizada == null ? null : atualizada.toInstant());
    }

    private static ExcecaoDeNegocio folgaNaoEncontrada() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Folga não encontrada.");
    }

    // ---- configuração -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    ConfiguracaoCadastrada configuracao() {
        return configuracaoDe(em.find(ConfiguracaoAgenda.class, ConfiguracaoAgenda.ID_UNICO));
    }

    /** Vale na próxima requisição: nada fica em cache. Reservas existentes não são reescritas (E22). */
    @Transactional
    Alterado<ConfiguracaoCadastrada> alterarConfiguracao(UUID alteradaPor, NovaConfiguracao nova) {
        ConfiguracaoAgenda config = em.find(ConfiguracaoAgenda.class, ConfiguracaoAgenda.ID_UNICO,
                LockModeType.PESSIMISTIC_WRITE);
        if (config.getVersion() != nova.version()) {
            throw new ExcecaoDeNegocio(CodigoErro.CONFLITO_DE_VERSAO);
        }
        List<String> campos = camposAlterados(
                mudou("intervaloEntreAtendimentosMin", config.getIntervaloEntreAtendimentosMin(),
                        nova.intervaloEntreAtendimentosMin()),
                mudou("granularidadeMin", config.getGranularidadeMin(), nova.granularidadeMin()),
                mudou("expiracaoPagamentoMin", config.getExpiracaoPagamentoMin(), nova.expiracaoPagamentoMin()));
        config.alterar(nova.intervaloEntreAtendimentosMin(), nova.granularidadeMin(), nova.expiracaoPagamentoMin(),
                alteradaPor, clock.instant());
        em.flush();
        return new Alterado<>(configuracaoDe(config), campos);
    }

    private static ConfiguracaoCadastrada configuracaoDe(ConfiguracaoAgenda c) {
        return new ConfiguracaoCadastrada(c.getFuso(), c.getIntervaloEntreAtendimentosMin(), c.getGranularidadeMin(),
                c.getExpiracaoPagamentoMin(), c.getVersion());
    }

    // ---- apoio ------------------------------------------------------------------------------------------------------

    /** Trava a linha do barbeiro até o fim da transação. Barbeiro inexistente: 404. */
    private void travarBarbeiro(UUID barbeiroId) {
        boolean achou = jdbc.sql("SELECT id FROM barbeiro WHERE id = :id FOR UPDATE").param("id", barbeiroId)
                .query(UUID.class).optional().isPresent();
        if (!achou) {
            throw barbeiroNaoEncontrado();
        }
    }

    private boolean barbeiroExiste(UUID barbeiroId) {
        return jdbc.sql("SELECT count(*) FROM barbeiro WHERE id = :id").param("id", barbeiroId).query(Long.class)
                .single() > 0;
    }

    /**
     * Grava agora, para a violação de único aparecer aqui e virar o 409 do contrato (E23). Outro erro (inclusive a
     * versão otimista) sobe como veio: o {@code TratadorGlobalDeErros} cuida.
     */
    private void descarregar(CodigoErro seUnicoViolado) {
        try {
            em.flush();
        } catch (RuntimeException falha) {
            if (UNICO_VIOLADO.equals(sqlState(falha))) {
                throw new ExcecaoDeNegocio(seUnicoViolado);
            }
            throw falha;
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

    /** O nome do campo se o valor mudou, senão nulo. */
    private static @Nullable String mudou(String campo, @Nullable Object antes, @Nullable Object depois) {
        return Objects.equals(antes, depois) ? null : campo;
    }

    private static List<String> camposAlterados(@Nullable String... campos) {
        List<String> alterados = new ArrayList<>();
        for (String campo : campos) {
            if (campo != null) {
                alterados.add(campo);
            }
        }
        return alterados;
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static ExcecaoDeNegocio barbeiroNaoEncontrado() {
        return new ExcecaoDeNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO, "Barbeiro não encontrado.");
    }
}
