---
name: especificar-fatia
description: Escreve a especificação SDD de uma fatia da Barbearia Altas Horas em docs/sdd/fatia-N-*.md (objetivo, contrato, decisões de desenho, critérios de aceite Dado/Quando/Então com o teste de cada um) e para para o OK do Kevin, sem escrever código de produção. Use quando pedirem "/especificar-fatia N", "escreve a spec da fatia 3" ou "começa a especificação da próxima fatia".
---

# /especificar-fatia N

Você escreve a **spec** da fatia N. Nada de código de produção nesta skill: o resultado é um Markdown que o Kevin aprova. Fale em português do Brasil.

## 1. Juntar o contexto (não supor)
1. Leia `CLAUDE.md`, `docs/sdd/README.md`, `docs/sdd/modelo-de-spec.md` e a spec da fatia anterior (estilo e nível de detalhe).
2. Descubra o escopo: README (seção "Roadmap em fatias") e, se existir na máquina, o plano de fatias interno (S## e F## que a fatia junta).
3. Para cada regra citada, rode `scripts/buscar-contexto.sh R<n>` e leia a linha completa no dossiê; faça o mesmo para decisões (`D<n>`) e códigos de erro.
4. Leia as rotas no `docs/contrato/openapi.yaml` e no `contrato-api.md`; leia a migração atual em `backend/src/main/resources/db/migration/`.
5. Datas usadas nos critérios: confira o dia da semana com código (`python -c "import datetime; ..."`), nunca de cabeça.

## 2. Escrever `docs/sdd/fatia-N-<assunto>.md` (seguindo o modelo)
- **Status:** "aguarda aprovação do Kevin" com a data.
- **Não cobre:** o que fica para outras fatias, com o número.
- **Contrato:** rotas, sucesso e erros. Se precisar mudar o contrato, liste os ajustes como "aplicados depois da aprovação" com a versão nova.
- **Decisões de desenho** (E1, E2...): cada pergunta difícil com decisão e porquê. Procure as armadilhas de verdade (fuso, limites inclusivos, serviço que passa do expediente, concorrência, N+1, formato de data, dado pessoal em log).
- **Critérios de aceite** (A1, A2...): Dado/Quando/Então concretos, com números calculados (quantos horários, qual o primeiro), e o nome do teste que prova cada um.
- **Entregas** por módulo, **riscos** e **pronto quando**.

## 3. Conferir
- Recontar à mão os números dos critérios (ex.: quantos slots cabem).
- Links relativos existem.
- Nada de dado pessoal real, segredo ou conteúdo da área interna da equipe.
- Atualizar a tabela "Specs" em `docs/sdd/README.md`.

## 4. Parar
Mostre ao Kevin: resumo em 5 linhas, as decisões que mais mudam o código e as dúvidas reais (se houver). **Espere o OK** antes de qualquer contrato ou código.
