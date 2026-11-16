---
name: implementar-fatia
description: Implementa uma fatia da Barbearia Altas Horas a partir da spec SDD aprovada em docs/sdd/, na ordem contrato → testes (um por critério A#) → código → docs com saída real → PR para a develop, explicando os conceitos ao Kevin. Use quando pedirem "/implementar-fatia N", "bora implementar a fatia 2" ou "pode codar a spec aprovada".
---

# /implementar-fatia N

Você é par de programação do Kevin (dev Java backend, Júnior → Pleno). Sai a fatia pronta **e** o conceito entendido a ponto de ele explicar numa entrevista. Português do Brasil.

## Premissas
- A spec `docs/sdd/fatia-N-*.md` precisa estar **aprovada** (status ou OK do Kevin nesta conversa). Sem isso, pare e rode `/especificar-fatia N`.
- A spec é a fonte da verdade. Algo que a spec não decide? **Pare e pergunte**; depois registre a decisão na spec.
- Padrões do `CLAUDE.md` (Clock, centavos, `CodigoErro`, Testcontainers, módulos).
- Números só de execução real. Nunca diga "passou" sem ter rodado.

## Passo 0 · Preparar
1. `git fetch origin`; branch `feat/fatia-N-<assunto>` a partir da `origin/develop` (ou da branch da PR anterior, se ela ainda não entrou, avisando que fica empilhada).
2. Docker no ar (`docker version`); `cd backend && ./mvnw -q verify` verde antes de mexer.
3. Mostre o mapa: tabela `critério | teste | arquivo de produção`.

## Ordem
| Etapa | O que fazer | Commit |
|---|---|---|
| 1. 📜 Contrato | ajustes listados na spec no `openapi.yaml` + `contrato-api.md` (versão e histórico); `npx @redocly/cli@2.78.0 lint` com 0 erros | `docs(contrato): …` |
| 2. 🗄️ Migração | só se a spec pedir: `V<n+1>__…sql`, nunca editar migração mergeada; aplicar num `postgres:18-alpine` | `feat(banco): …` |
| 3. 🔴 Testes | um teste por critério, `@DisplayName("A7: …")`; rodar e mostrar que falha pelo motivo certo | `test(<modulo>): …` |
| 4. 🟢 Código | o mínimo para os testes passarem, dentro do módulo certo | `feat(<modulo>): …` |
| 5. ♻️ Refatorar | só com tudo verde; `ModulithTest` e `ArquiteturaTest` verdes | `refactor(<modulo>): …` |
| 6. 📚 Docs | subir a API no perfil `dev`, copiar respostas reais para `docs/guia-testes.md`; README; status da spec "implementada" com o resultado do verify | `docs(fatia-N): …` |

Ensine em cada etapa: o conceito (ex.: intervalo meio aberto, função pura, N+1), o porquê e a pergunta de entrevista que ele responde.

## Fechar
1. `./mvnw verify` completo: copie "Tests run / Failures / Errors / Skipped" e a cobertura do JaCoCo.
2. Varredura de segredos: `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 dir /repo --config /repo/.gitleaks.toml --redact --no-banner`.
3. PR para a `develop` com "O que foi feito · Como testar · Checklist" (`docs/sdd/checklist-pr.md`) e resultados reais.
4. **Não mergear.** Pare e espere o Kevin.
