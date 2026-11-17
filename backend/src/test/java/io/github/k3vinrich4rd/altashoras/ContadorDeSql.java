package io.github.k3vinrich4rd.altashoras;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Conta as instruções SQL que a aplicação prepara (JPA e JDBC juntos), para provar que o número de consultas não cresce
 * com o número de linhas (sem N+1). O Hibernate Statistics só enxerga o JPA; o painel usa JdbcClient. Embrulha o
 * DataSource num proxy que conta {@code prepareStatement}, {@code createStatement} e {@code prepareCall}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContadorDeSql {

    private static final AtomicLong TOTAL = new AtomicLong();

    public static long total() {
        return TOTAL.get();
    }

    @Bean
    static BeanPostProcessor contadorDeConsultas() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nome) {
                if (bean instanceof DataSource fonte && !Proxy.isProxyClass(bean.getClass())) {
                    return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                            (proxy, metodo, args) -> {
                                Object resultado = chamar(fonte, metodo, args);
                                return resultado instanceof Connection conexao && metodo.getName().equals("getConnection")
                                        ? contar(conexao) : resultado;
                            });
                }
                return bean;
            }
        };
    }

    private static Connection contar(Connection conexao) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, args) -> {
                    String nome = metodo.getName();
                    if (nome.equals("prepareStatement") || nome.equals("createStatement") || nome.equals("prepareCall")) {
                        TOTAL.incrementAndGet();
                    }
                    return chamar(conexao, metodo, args);
                });
    }

    private static Object chamar(Object alvo, Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException erro) {
            throw erro.getCause();
        }
    }
}
