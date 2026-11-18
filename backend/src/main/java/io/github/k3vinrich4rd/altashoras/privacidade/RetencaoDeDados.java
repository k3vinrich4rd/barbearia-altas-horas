package io.github.k3vinrich4rd.altashoras.privacidade;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O job de retenção (R29, D77, E18): todo dia às 3h no fuso da barbearia, com ShedLock (uma instância por vez) e ligado
 * ou desligado por {@code app.jobs.habilitados}. Ele não lê tabela alheia: chama cada {@link LimpezaPorRetencao}
 * publicada pelo módulo dono do dado. Uma limpeza que falha não impede as outras. O log leva só o nome técnico da
 * limpeza e a quantidade (nunca nome, e-mail, WhatsApp ou IP); é por ele que se nota se o job parou de rodar.
 */
@Component
public class RetencaoDeDados {

    private static final Logger LOG = LoggerFactory.getLogger(RetencaoDeDados.class);

    private final List<LimpezaPorRetencao> limpezas;
    private final Clock clock;

    RetencaoDeDados(List<LimpezaPorRetencao> limpezas, Clock clock) {
        this.limpezas = limpezas.stream().sorted(Comparator.comparing(LimpezaPorRetencao::nome)).toList();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.retencao.cron:0 0 3 * * *}", zone = "${app.fuso:America/Sao_Paulo}")
    @SchedulerLock(name = "retencao-de-dados", lockAtMostFor = "PT30M")
    public void executar() {
        executarEm(clock.instant());
    }

    /** O mesmo trabalho, sem agendador nem trava: devolve quantas linhas cada limpeza mudou (os testes usam). */
    public Map<String, Integer> executarEm(Instant agora) {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (LimpezaPorRetencao limpeza : limpezas) {
            try {
                int linhas = limpeza.executar(agora);
                resultado.put(limpeza.nome(), linhas);
                LOG.info("Retenção de dados: limpeza={} linhas={}", limpeza.nome(), linhas);
            } catch (RuntimeException falha) {
                // Só a classe do erro: a mensagem de uma exceção de banco pode trazer o valor de uma coluna.
                LOG.error("Retenção de dados: limpeza={} falhou ({})", limpeza.nome(), falha.getClass().getSimpleName());
            }
        }
        return resultado;
    }
}
