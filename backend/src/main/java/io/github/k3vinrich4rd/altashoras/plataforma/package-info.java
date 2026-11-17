/**
 * Módulo Plataforma. Infraestrutura transversal: erros RFC 9457, rastreio (traceId), relógio (Clock) e, depois, segurança e jobs.
 */
@ApplicationModule(displayName = "Plataforma", allowedDependencies = "comum")
package io.github.k3vinrich4rd.altashoras.plataforma;

import org.springframework.modulith.ApplicationModule;
