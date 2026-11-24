package io.github.k3vinrich4rd.altashoras.plataforma.implantacao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Fatia 8B (E4): DataSource de mentira, usado SÓ no {@code docker build}, no perfil {@code treino}, para o Spring montar o
 * contexto (e a JVM gravar o cache AOT) sem PostgreSQL. Responde "PostgreSQL" aos metadados e nada mais; qualquer SQL
 * devolve vazio. O perfil {@code treino} nunca é ativado em runtime (a imagem final roda no {@code prod}; o
 * {@code scripts/testar-imagem.sh} prova) e esta classe nem é criada fora dele.
 */
@Configuration(proxyBeanMethods = false)
@Profile("treino")
class TreinoSemBancoConfig {

    @Bean
    @Primary
    DataSource dataSourceDeTreino() {
        return (DataSource) falso(DataSource.class);
    }

    private static Object falso(Class<?> tipo) {
        InvocationHandler tratador = (proxy, metodo, args) -> {
            String nome = metodo.getName();
            Class<?> retorno = metodo.getReturnType();
            if (nome.equals("getDatabaseProductName")) {
                return "PostgreSQL";
            }
            if (nome.equals("getDatabaseProductVersion")) {
                return "18.0";
            }
            if (nome.equals("toString")) {
                return "DataSourceDeTreino";
            }
            if (nome.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (nome.equals("equals")) {
                return proxy == args[0];
            }
            if (nome.equals("unwrap")) {
                throw new java.sql.SQLException("sem banco (perfil treino)");
            }
            if (retorno == boolean.class) {
                return Boolean.FALSE;
            }
            if (retorno == int.class) {
                return 0;
            }
            if (retorno == long.class) {
                return 0L;
            }
            if (retorno == Connection.class || retorno == DatabaseMetaData.class || retorno == Statement.class
                    || retorno == ResultSet.class || retorno == java.sql.PreparedStatement.class) {
                return falso(retorno);
            }
            return null;
        };
        return Proxy.newProxyInstance(TreinoSemBancoConfig.class.getClassLoader(), new Class<?>[] {tipo}, tratador);
    }
}
