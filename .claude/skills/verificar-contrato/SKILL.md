---
name: verificar-contrato
description: Compara os controllers, DTOs e o CodigoErro do backend da Barbearia Altas Horas com o contrato docs/contrato/openapi.yaml e o contrato-api.md, e lista divergências de rota, parâmetro, campo, tipo, obrigatoriedade, status e código de erro. Só lê e relata. Use quando pedirem "/verificar-contrato", ao mudar rota ou DTO, ou antes de PR que mexe na API.
---

# /verificar-contrato

Você confere se o código cumpre o contrato. **Não edita nada**: relata. Português do Brasil.

## Passos
1. Lint: `npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml` (erros > 0 já é achado).
2. Liste as rotas do contrato (`paths` do YAML) e as do código (`@GetMapping`, `@PostMapping`... em `backend/src/main/java`).
3. Para cada rota implementada, compare:
   - método, caminho e parâmetros (`required`, tipo, formato `uuid`/`date`);
   - corpo de resposta: campos do schema × campos do `record` (nome exato, nenhum campo a mais, como `ativo` ou `version`, nenhum a menos);
   - status de sucesso e erros listados × exceções que o código lança;
   - segurança (`security: []` = pública).
4. `CodigoErro` × enum `CodigoErro` do YAML × tabela da seção 5 do `contrato-api.md`: os três com os mesmos nomes.
5. Rotas do contrato ainda não implementadas: só liste, com a fatia prevista (README, "Roadmap em fatias").

## Relatório
Tabela `gravidade | rota | divergência | arquivo:linha | o que fazer`, com gravidade 🔴 (quebra o front), 🟠 (contrato desatualizado) ou 🟡 (cosmético). Termine com "contrato e código batem" só se não houver nenhum 🔴 ou 🟠.
