package io.github.k3vinrich4rd.altashoras.plataforma.tarefas;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs agendados com ShedLock (E10): a trava fica na tabela shedlock (V1), com a hora do banco (usingDbTime), para
 * duas instâncias da API não rodarem o mesmo job ao mesmo tempo, mesmo com relógios diferentes.
 * O agendamento automático pode ser desligado (app.jobs.habilitados=false): os testes chamam o job à mão, para
 * ele não mexer nos dados de outro teste no meio.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class TarefasConfig {

    @Bean
    LockProvider travaDeJobs(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnBooleanProperty(name = "app.jobs.habilitados", matchIfMissing = true)
    static class Agendamento {
    }
}
