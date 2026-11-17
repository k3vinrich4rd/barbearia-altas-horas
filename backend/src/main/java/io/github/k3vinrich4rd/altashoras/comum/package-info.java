/**
 * Módulo Comum. Tipos compartilhados por todos os módulos: códigos de erro e, nas próximas fatias, os value objects
 * (Dinheiro, Cpf, Cep) que mascaram dado pessoal no toString(). Aberto: qualquer módulo pode usar.
 */
@ApplicationModule(displayName = "Comum", type = ApplicationModule.Type.OPEN)
package io.github.k3vinrich4rd.altashoras.comum;

import org.springframework.modulith.ApplicationModule;
