package io.github.k3vinrich4rd.altashoras.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;

import io.github.k3vinrich4rd.altashoras.DadosDeTeste;

/** A57 e A58: o job de retenção em lotes, sem log de dado pessoal, com ShedLock e desligável (E18). */
@ExtendWith(OutputCaptureExtension.class)
class RetencaoJobIT extends PrivacidadeBase {

    private static final Pattern LINHAS_DO_GUEST = Pattern.compile("limpeza=reserva_guest linhas=(\\d+)");

    @Autowired
    RetencaoDeDados retencao;

    @Autowired
    ApplicationContext contexto;

    /** {@code quantos} visitantes vencidos de uma vez: uma reserva por hora de janeiro de 2026, bem antes dos 90 dias. */
    private void visitantesVencidos(int quantos) {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        jdbc.update("""
                WITH novas AS (
                    INSERT INTO reserva (barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos)
                    SELECT ?, ?, 'GUEST', ts, ts + interval '30 minutes', ts + interval '30 minutes', 'CANCELADO', 4500
                    FROM generate_series(timestamptz '2026-01-02 10:00-03', timestamptz '2026-01-02 10:00-03'
                                         + (? - 1) * interval '1 hour', interval '1 hour') AS ts
                    RETURNING id)
                INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
                SELECT id, 'Visitante Fictício ' || id, 'visitante.' || id || '@exemplo.test', '11988880002',
                       encode(sha256(convert_to(id::text, 'UTF8')), 'hex')
                FROM novas
                """, barbeiro, servico, quantos);
    }

    private static int somaDasLinhasLogadas(String saida) {
        Matcher m = LINHAS_DO_GUEST.matcher(saida);
        int soma = 0;
        while (m.find()) {
            soma += Integer.parseInt(m.group(1));
        }
        return soma;
    }

    @Test
    @DisplayName("A57: 1200 visitantes vencidos: todos anonimizados, em lotes de no máximo 500 linhas por transação (500, 500 e 200); a contagem logada é 1200 e o log não tem nome, e-mail nem WhatsApp")
    void milEDuzentosEmLotes(CapturedOutput saida) {
        visitantesVencidos(1200);
        assertThat(contar("SELECT count(*) FROM reserva_guest WHERE anonimizado_em IS NULL")).isEqualTo(1200);
        // Um gatilho de comando conta quantas linhas cada UPDATE de reserva_guest alterou: cada UPDATE é um lote.
        jdbc.execute("CREATE TABLE lote_do_teste (qtd integer)");
        jdbc.execute("""
                CREATE FUNCTION conta_lote_do_teste() RETURNS trigger AS $$
                BEGIN INSERT INTO lote_do_teste SELECT count(*) FROM novas; RETURN NULL; END $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER conta_lote_do_teste AFTER UPDATE ON reserva_guest REFERENCING NEW TABLE AS novas
                FOR EACH STATEMENT EXECUTE FUNCTION conta_lote_do_teste()
                """);
        try {
            retencao.executar();

            assertThat(contar("SELECT count(*) FROM reserva_guest WHERE anonimizado_em IS NOT NULL")).isEqualTo(1200);
            assertThat(contar("SELECT count(*) FROM reserva_guest WHERE nome = 'Visitante anonimizado'")).isEqualTo(1200);
            assertThat(jdbc.queryForList("SELECT qtd FROM lote_do_teste ORDER BY qtd DESC", Integer.class))
                    .containsExactly(500, 500, 200);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS conta_lote_do_teste ON reserva_guest");
            jdbc.execute("DROP FUNCTION IF EXISTS conta_lote_do_teste()");
            jdbc.execute("DROP TABLE IF EXISTS lote_do_teste");
        }
        assertThat(somaDasLinhasLogadas(saida.getAll())).isEqualTo(1200);
        assertThat(saida.getAll()).contains("limpeza=reserva_guest linhas=1200")
                .doesNotContain("Visitante Fictício").doesNotContain("@exemplo.test").doesNotContain("11988880002");
    }

    @Test
    @DisplayName("A10: visitante com região e reserva de 91 dias: o job apaga a região junto com o contato (a reserva fica, sem identificar ninguém); quem ainda não venceu mantém a região")
    void regiaoDoVisitanteSomeComOContato() {
        UUID servico = DadosDeTeste.servico(jdbc, "Corte Clássico", 30, 4500, true);
        UUID barbeiro = DadosDeTeste.barbeiro(jdbc, true);
        // fim em 02/09/2026 09:30 = exatamente 90 dias antes de 01/12 09:30; o outro termina um minuto depois (ainda vale).
        UUID vencido = reserva(barbeiro, servico, DadosDeTeste.em(java.time.LocalDate.of(2026, 9, 2), 9, 0), "CONCLUIDO",
                null, null, "Visitante Fictício");
        UUID vigente = reserva(barbeiro, servico, DadosDeTeste.em(java.time.LocalDate.of(2026, 9, 2), 9, 1), "CONCLUIDO",
                null, null, "Visitante Fictício");
        for (UUID id : List.of(vencido, vigente)) {
            jdbc.update("""
                    UPDATE reserva SET regiao_bairro = 'Sé', regiao_cidade = 'São Paulo', regiao_uf = 'SP',
                                       regiao_municipio_ibge = '3550308', regiao_consentimento_versao = '1.2' WHERE id = ?
                    """, id);
        }

        retencao.executar();

        assertThat(jdbc.queryForObject("SELECT anonimizado_em IS NOT NULL FROM reserva_guest WHERE reserva_id = ?",
                Boolean.class, vencido)).isTrue();
        assertThat(jdbc.queryForMap("""
                SELECT regiao_bairro, regiao_cidade, regiao_uf, regiao_municipio_ibge, regiao_consentimento_versao
                FROM reserva WHERE id = ?""", vencido)).containsEntry("regiao_bairro", null).containsEntry("regiao_cidade", null)
                .containsEntry("regiao_uf", null).containsEntry("regiao_municipio_ibge", null)
                .containsEntry("regiao_consentimento_versao", null);
        assertThat(jdbc.queryForMap("SELECT regiao_bairro, regiao_municipio_ibge FROM reserva WHERE id = ?", vigente))
                .containsEntry("regiao_bairro", "Sé").containsEntry("regiao_municipio_ibge", "3550308");
    }

    @Test
    @DisplayName("A58: dois disparos simultâneos do job com a mesma trava anonimizam cada linha uma só vez, sem erro; com app.jobs.habilitados=false o agendador não existe e o job não roda sozinho")
    void umaVezSoEDesligavel(CapturedOutput saida) throws Exception {
        visitantesVencidos(300);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> disparos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            disparos.add(pool.submit(() -> {
                largada.await();
                retencao.executar();
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> disparo : disparos) {
            disparo.get(); // sem erro em nenhum dos dois
        }
        pool.shutdown();

        assertThat(contar("SELECT count(*) FROM reserva_guest WHERE anonimizado_em IS NOT NULL")).isEqualTo(300);
        // Cada linha foi alterada uma vez: as contagens logadas (a do disparo que perdeu a trava nem existe, ou é 0) somam 300.
        assertThat(somaDasLinhasLogadas(saida.getAll())).isEqualTo(300);
        assertThat(contar("SELECT count(*) FROM shedlock WHERE name = 'retencao-de-dados'")).isEqualTo(1);
        // O @EnableScheduling mora numa classe interna condicionada a app.jobs.habilitados: desligado, ela nem existe.
        assertThat(contexto.getBeanNamesForType(Class.forName("io.github.k3vinrich4rd.altashoras.plataforma.tarefas.TarefasConfig$Agendamento")))
                .as("app.jobs.habilitados=false: o agendamento automático não sobe, o job não roda sozinho").isEmpty();
    }
}
