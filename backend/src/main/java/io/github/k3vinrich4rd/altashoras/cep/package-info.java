/**
 * Módulo CEP. Consulta de CEP com ViaCEP, fallback BrasilAPI, cache e degradação (Fatia 6A, ADR 0008).
 * API pública: só {@code CepApi}, {@code Endereco} e {@code CepIndisponivel}. Controller, clientes dos provedores e cache
 * ficam em {@code cep.consulta}, que o Modulith não deixa ninguém de fora usar.
 */
@ApplicationModule(displayName = "CEP", allowedDependencies = {"comum", "plataforma"})
package io.github.k3vinrich4rd.altashoras.cep;

import org.springframework.modulith.ApplicationModule;
