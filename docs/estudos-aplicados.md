# 🎓 O que eu estudo × onde aplico neste projeto

| | |
|---|---|
| 🎯 **Para que serve** | ligar as minhas metas de estudo (trilhas de Java/Spring e de IA) a evidências neste repositório: o que já foi praticado aqui e em qual fatia entra o resto |
| 🧭 **Origem** | as trilhas do meu caderno de estudos (as mesmas que aparecem no [README do meu perfil](https://github.com/k3vinrich4rd)) |
| ✅ / 🔜 | ✅ aplicado, com arquivo ou teste para conferir · 🔜 planejado, com a fatia |

## Trilha 1 · Java e Spring: o que ainda estava em andamento

| Tema de estudo | Aplicação neste projeto | Onde conferir | |
|---|---|---|:-:|
| **Arquiteturas de software** (monólito modular, hexagonal, BFF) | 8 módulos com fronteiras testadas; portas e adaptadores onde paga o custo (`CalculadoraDeHorarios` pura + porta `FonteDeBloqueios`) | [ADR 0001](adr/0001-monolito-modular-com-spring-modulith.md), [ADR 0011](adr/0011-hexagonal-seletivo-no-monolito-modular.md), [`ArquiteturaTest`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/ArquiteturaTest.java) | ✅ Fatias 1 e 2 (BFF: 🔜 7) |
| **JPQL e JOIN FETCH** | catálogo por projeção JPQL direto em DTO; barbeiros com as jornadas num `LEFT JOIN FETCH` só (e a lição do `DISTINCT` que o PostgreSQL recusou) | [`ServicoRepository`](../backend/src/main/java/io/github/k3vinrich4rd/altashoras/agenda/catalogo/ServicoRepository.java), [`BarbeiroRepository`](../backend/src/main/java/io/github/k3vinrich4rd/altashoras/agenda/catalogo/BarbeiroRepository.java) | ✅ Fatia 2 |
| **N+1 consultas** | teste que conta as consultas do Hibernate e prova que o número **não cresce** com mais barbeiros | [`DisponibilidadeIT`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/agenda/disponibilidade/DisponibilidadeIT.java) (A29) | ✅ Fatia 2 |
| **Paginação e `countQuery`** | listas do painel (`/admin/pagamentos`, `/admin/auditoria`, `/me/agendamentos`) paginadas no banco | [contrato](contrato/contrato-api.md) | 🔜 Fatias 4 e 5 |
| **OAuth2 e OIDC** | a API como Resource Server (JWT); login social por OIDC atrás de flag | [ADR 0006](adr/0006-sessao-jwt-refresh-cookie-bff.md) | 🔜 Fatia 3 (social: pós-beta) |
| **Síncrono e assíncrono** | gateway chamado de forma síncrona **fora** da transação; confirmação assíncrona por webhook; CEP com timeout curto | [arquitetura, seção 6](arquitetura/README.md) | 🔜 Fatias 4 e 6 |
| **Mensageria e Outbox** | `event_publication` do Spring Modulith: o evento é gravado na mesma transação da mudança | [ADR 0002](adr/0002-comunicacao-entre-modulos.md), decisão D41 | 🔜 Fatia 4 |
| **Pub-Sub e DLQ** | evento de webhook que falha é reprocessado com limite de tentativas e, esgotado, vai para `REVISAO_MANUAL` (o papel de uma DLQ, sem broker) | [ADR 0007](adr/0007-gateway-de-pagamento-sandbox.md), decisão D28 | 🔜 Fatia 4 |

### Arquitetura: o que eu estudei por último

| Estilo | Onde aparece neste projeto | Onde conferir | |
|---|---|---|:-:|
| **Arquitetura em camadas** | catálogo e política: controller → repositório Spring Data com projeção em DTO, sem cerimônia | [`CatalogoController`](../backend/src/main/java/io/github/k3vinrich4rd/altashoras/agenda/catalogo/CatalogoController.java) | ✅ Fatia 2 |
| **Arquitetura hexagonal** | disponibilidade: núcleo puro (`CalculadoraDeHorarios`), porta de saída (`FonteDeBloqueios`), adaptador SQL (`BloqueiosJdbc`) e adaptador de entrada HTTP | [ADR 0011](adr/0011-hexagonal-seletivo-no-monolito-modular.md), [`disponibilidade/`](../backend/src/main/java/io/github/k3vinrich4rd/altashoras/agenda/disponibilidade) | ✅ Fatia 2 |
| **Clean Architecture** (a regra de dependência: o centro não conhece a borda) | regra do ArchUnit que proíbe o núcleo de importar Spring, JPA, Hibernate ou JDBC | [`ArquiteturaTest`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/ArquiteturaTest.java) | ✅ Fatia 2 |
| **Arquitetura orientada a eventos** | `PagamentoConfirmado` → agenda confirma a reserva; `ReservaExpirada` → pagamento cancela a cobrança; publicação com outbox do Spring Modulith | [ADR 0002](adr/0002-comunicacao-entre-modulos.md) | 🔜 Fatia 4 |
| **BFF (Backend for Frontend)** | rotas do Next.js guardam os tokens em cookie `HttpOnly` e falam com a API; o navegador nunca vê o JWT | [ADR 0006](adr/0006-sessao-jwt-refresh-cookie-bff.md) | 🔜 Fatia 7 |

> Por que não um estilo só: cada um responde a um problema. Usar hexagonal no catálogo seria custo sem retorno; usar só camadas na disponibilidade deixaria a regra presa ao banco. O critério está no ADR 0011.

Temas que eu já tinha praticado e que aqui subiram de nível:

| Tema | O passo a mais neste projeto | Onde conferir |
|---|---|---|
| SQL e constraints | `EXCLUDE USING gist` com `tstzrange`, índice parcial, `CHECK` protegendo regra de pagamento | [`V1__agenda.sql`](../backend/src/main/resources/db/migration/V1__agenda.sql) |
| Concorrência | 12 conexões reais disputando o mesmo horário | [`ConcorrenciaReservaIT`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/banco/ConcorrenciaReservaIT.java) |
| Testcontainers | PostgreSQL 18 como bean com `@ServiceConnection`, reaproveitado entre as classes | [`PostgresTestcontainers`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/PostgresTestcontainers.java) |
| Tratamento de erros REST | RFC 9457 com `code` e `traceId`, inclusive para os erros do próprio Spring MVC | [`TratadorGlobalDeErros`](../backend/src/main/java/io/github/k3vinrich4rd/altashoras/plataforma/web/TratadorGlobalDeErros.java) |
| Dinheiro | centavos em `long` e regra de arquitetura que proíbe `double` | [`ArquiteturaTest`](../backend/src/test/java/io/github/k3vinrich4rd/altashoras/ArquiteturaTest.java) |

## Trilha 2 · IA

| Tema de estudo | Aplicação neste projeto | Onde conferir | |
|---|---|---|:-:|
| **Spec-Driven Development** | toda fatia começa por uma spec com critérios numerados | [`docs/sdd/`](sdd/README.md) | ✅ |
| **Prompt e engenharia de contexto** | `CLAUDE.md` como índice: a IA lê onde está cada regra em vez de receber tudo no prompt | [`CLAUDE.md`](../CLAUDE.md) | ✅ |
| **Skills** | três procedimentos carregados sob demanda | [`.claude/skills/`](../.claude/skills/) | ✅ |
| **Agentes de IA** | agente revisor só de leitura, com contexto separado de quem escreveu o código | [`revisor-de-fatia`](../.claude/agents/revisor-de-fatia.md) | ✅ |
| **RAG** | busca híbrida na documentação: exata para identificador (`R15`), vetorial para pergunta em linguagem natural; 379 trechos de 36 arquivos | [`docs/ia/rag-vetorial.md`](ia/rag-vetorial.md), [`Buscador`](../ferramentas/rag/src/main/java/io/github/k3vinrich4rd/altashoras/rag/Buscador.java) | ✅ |
| **Testes de aplicações com IA (evals)** | duas avaliações de verdade: (1) todo critério `A#` da spec tem teste, cobrado no CI; (2) acerto@3 da recuperação do RAG em 17 perguntas, comparando só vetor (9) com híbrida (14) | [`scripts/conferir-spec.sh`](../scripts/conferir-spec.sh), [`Avaliador`](../ferramentas/rag/src/main/java/io/github/k3vinrich4rd/altashoras/rag/Avaliador.java) | ✅ |
| **Guardrails** | travas que valem para a IA e para mim: skill que **para** sem spec aprovada, CI que barra critério sem teste, ArchUnit, gitleaks e instruções do Copilot com o que nunca pode ser feito (rota de cancelamento, número de cartão na API) | [`docs/automacao.md`](automacao.md), [`copilot-instructions.md`](../.github/copilot-instructions.md) | ✅ (de processo) |
| **Structured output** | o agente revisor termina com um JSON de formato fixo (testes, critérios, achados, `pode_abrir_pr`), legível por script | [`revisor-de-fatia`](../.claude/agents/revisor-de-fatia.md) | ✅ |
| **Embeddings e banco vetorial** | modelo multilíngue local (ONNX, 384 dimensões) + PostgreSQL com pgvector, índice HNSW e distância de cosseno; *chunking* por título em função pura testada | [`application.yml`](../ferramentas/rag/src/main/resources/application.yml), [`DivisorDeMarkdown`](../ferramentas/rag/src/main/java/io/github/k3vinrich4rd/altashoras/rag/DivisorDeMarkdown.java) | ✅ |
| **Spring AI** | `VectorStore`, `SearchRequest` e o starter de embeddings locais, na versão 2.0.2 (linha do Spring Boot 4) | [`ferramentas/rag/pom.xml`](../ferramentas/rag/pom.xml) | ✅ (recuperação; `ChatClient` e `@Tool`: 🔜 pós-beta) |
| **Tool Calling, MCP, Design Doc para IA** | pós-beta: um assistente de agendamento que consulta `GET /disponibilidade` como ferramenta (`@Tool`), só leitura, com design doc, guardrail de saída, resposta em `record` (structured output) e evals antes do código | [roadmap](../README.md#-roadmap-em-fatias) | 🔜 pós-beta |

> **Regra que eu sigo:** só marco ✅ o que tem arquivo ou teste para abrir. O que é plano fica como 🔜 com a fatia.
