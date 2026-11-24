# ✅ Checklist de toda PR (SDD)

Ordem obrigatória: **spec → contrato → teste → código → docs**. Nenhuma linha de produção antes de a spec da fatia estar aprovada pelo Kevin.

## Antes do código
- [ ] Spec da fatia em `docs/sdd/fatia-N-*.md` (modelo: `docs/sdd/modelo-de-spec.md`): regras (R#), rotas do contrato, critérios de aceite em Dado/Quando/Então
- [ ] Contrato (`docs/contrato/openapi.yaml` + `contrato-api.md`) atualizado e `npx @redocly/cli@2.78.0 lint` com 0 erros
- [ ] Decisão nova registrada em `docs/planejamento/decisoes.md` (e ADR se mudar arquitetura)

## Código
- [ ] Branch a partir da `origin/develop` (`feat/...`, `docs/...`); PR para a `develop`, **nunca** para a `main`
- [ ] Testes de integração com PostgreSQL real (Testcontainers) verdes
- [ ] Erros em RFC 9457 com `code` e `traceId`, com teste
- [ ] Rota nova com teste de 401, 403 ou 404
- [ ] Nenhum dado pessoal (CPF, telefone, CEP) em log
- [ ] ArchUnit e Spring Modulith `verify()` verdes
- [ ] Valores de regra vêm do banco/config, nunca fixos (D15)
- [ ] Migração nova é compatível com o código anterior (expandir → migrar → contrair, em releases separadas): o rollback do Render depende disso ([rollback](../planejamento/ativar-deploy.md#como-voltar-atrás-rollback-fatia-8b))

## Fechamento
- [ ] CI verde (build, testes, CodeQL, gitleaks)
- [ ] Passo do guia (`docs/guia-testes.md`; roteiro interno completo da equipe) com comando e resposta **reais**
- [ ] README só com números que saíram de execução real
- [ ] Corpo da PR: O que foi feito · Como testar · Checklist · resultado real dos testes
- [ ] Commits semânticos em português; parar e esperar o OK do Kevin
