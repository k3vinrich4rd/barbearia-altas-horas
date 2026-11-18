/**
 * Módulo Admin. Painel da barbearia: agenda, fechamento, pagamentos pendentes e auditoria (Fatia 5A); cadastros na 5B.
 * Os controllers, os casos de uso e a auditoria moram aqui; as regras e as tabelas continuam com os donos (agenda,
 * pagamento, conta), que se expõem por API pública (ADR 0002). Só enxerga a tabela da auditoria. Também limpa o IP da
 * auditoria por prazo, pela porta {@code LimpezaPorRetencao} da privacidade (Fatia 6B).
 */
@ApplicationModule(displayName = "Admin", allowedDependencies = {"agenda", "pagamento", "conta", "privacidade", "comum"})
package io.github.k3vinrich4rd.altashoras.admin;

import org.springframework.modulith.ApplicationModule;
