# Barbearia Altas Horas · guia para a IA

Projeto **individual** do Kevin Richard: backend em Java/Spring feito pelo Kevin com o Claude Code como par de programação; frontend Next.js gerado com IA a partir do contrato. Fale em **português do Brasil**, inclusive nos avisos de andamento.

## Regra de ouro: SDD
Nenhuma linha de código de produção sem **spec aprovada** em `docs/sdd/fatia-N-*.md`. Ordem: spec → OK do Kevin → contrato → testes → código → docs com saída real → PR para a `develop`. Fluxo completo: `docs/sdd/README.md`.

## Onde está cada coisa (consulte antes de supor)
| Preciso de... | Arquivo |
|---|---|
| regra de negócio `R1`–`R32`, hipóteses `H#` | `docs/contexto.md` (seções 3 e 4) |
| decisão `D1`–`D44` (data, fonte, status) | `docs/planejamento/decisoes.md` |
| rota, schema, código de erro | `docs/contrato/openapi.yaml` (máquina) e `docs/contrato/contrato-api.md` (humano) |
| máquina de estados da reserva | `docs/dominio/modelo-de-dominio.md` seção 2 |
| módulos e fluxos críticos | `docs/arquitetura/README.md`; ADRs em `docs/adr/` |
| schema do banco | `backend/src/main/resources/db/migration/` (a verdade) |
| critérios de aceite da fatia atual | `docs/sdd/fatia-N-*.md` |
| roteiro de teste para visitantes | `docs/guia-testes.md` |

Pergunta em linguagem natural sobre a documentação: RAG em `ferramentas/rag` (`docs/ia/rag-vetorial.md`), se o banco `rag-db` estiver no ar.

Busca por identificador: `scripts/buscar-contexto.sh R15` (ou `D40`, `HORARIO_INDISPONIVEL`, `A7`) mostra todas as linhas que citam o identificador, com arquivo e linha.

## Padrões do código
- Pacote `io.github.k3vinrich4rd.altashoras`; 8 módulos do Spring Modulith (`comum` é aberto; os demais só pela API pública). `ModulithTest` e `ArquiteturaTest` precisam ficar verdes.
- Tempo só pelo `Clock` injetado (nunca `Instant.now()`); dinheiro em `long` de centavos.
- Erros: lançar exceção com `CodigoErro`; o `TratadorGlobalDeErros` devolve RFC 9457 com `code` e `traceId`. Código novo de erro = linha nova na tabela do contrato.
- Regras configuráveis vêm do banco (`configuracao_agenda`), nunca fixas no código.
- Migração mergeada nunca é editada: mudança vira `V<n+1>`.
- Testes com PostgreSQL real (`PostgresTestcontainers`); o critério da spec vai no `@DisplayName` (`"A7: ..."`).
- Nenhum CPF, telefone, CEP ou e-mail em log.

## Comandos
```bash
docker compose up -d                                            # PostgreSQL 18 na 5433
cd backend && ./mvnw verify                                     # testes + cobertura
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
npx @redocly/cli@2.78.0 lint docs/contrato/openapi.yaml         # contrato
```

## Git
- Branch a partir da `develop` (ou empilhada na PR anterior quando ela ainda não entrou); commits semânticos em português; PR para a `develop` com "O que foi feito · Como testar · Checklist" e o resultado **real** dos testes.
- **Nunca** mergear na `main`: release `develop → main` só pelo Kevin.
- `.equipe/` é interna e ignorada pelo Git: nada dela vai para commit, PR ou print.

## Skills e agente do projeto
`/especificar-fatia N` · `/implementar-fatia N` · `/verificar-contrato` (`.claude/skills/`) · agente `revisor-de-fatia` (`.claude/agents/`), para rodar antes de abrir a PR.

Ao fim de cada fatia, atualize `docs/guia-testes.md` (passos ⏳ viram ✅ com a resposta real), `docs/como-foi-construido.md` (linha do tempo) e, se existir na máquina, `.equipe/testes/BACKEND_E2E.md`.
