/**
 * Módulo Privacidade. Consentimento, política, exportação, exclusão e retenção de dados (LGPD, Fatia 6B).
 * Só depende do {@code comum}: quem tem dado pessoal (conta, agenda, admin, pagamento) implementa as portas
 * {@link io.github.k3vinrich4rd.altashoras.privacidade.FonteDeDadosDoTitular} e
 * {@link io.github.k3vinrich4rd.altashoras.privacidade.LimpezaPorRetencao} e escuta
 * {@link io.github.k3vinrich4rd.altashoras.privacidade.ExclusaoSolicitada}; este módulo só toca as próprias
 * tabelas ({@code consentimento} e {@code solicitacao_titular}) e nunca importa o módulo de quem o usa (ADR 0002).
 */
@ApplicationModule(displayName = "Privacidade", allowedDependencies = "comum")
package io.github.k3vinrich4rd.altashoras.privacidade;

import org.springframework.modulith.ApplicationModule;
