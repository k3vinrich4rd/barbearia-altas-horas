# 🏗 Como este projeto foi construído, de ponta a ponta

| | |
|---|---|
| 🎯 **Para que serve** | contar, sem maquiagem, como o projeto saiu do zero: o método (SDD), as ferramentas de IA, as skills, o que cada fatia entregou e como conferir |
| 👥 **Para quem** | o Kevin (estudar e explicar o próprio projeto), recrutadores e quem quiser repetir o método |
| 🧭 **Leia junto** | [SDD](sdd/README.md) · [guia de testes](guia-testes.md) · [decisões](planejamento/decisoes.md) |

## 1. O ponto de partida

Projeto **individual** de Kevin Richard, desenvolvedor backend Java, para a barbearia do pai (Osasco, desde 1997). Sem parceiro de frontend. A escolha foi deixar isso explícito:

| Parte | Quem faz |
|---|---|
| Regras de negócio | Kevin, conversando com o barbeiro |
| Especificação, backend, deploy | Kevin, com o **Claude Code** como par de programação |
| Frontend (Next.js) | **gerado com IA** a partir do contrato OpenAPI, revisado pelo Kevin |

## 2. O método: Spec-Driven Development

Ordem fixa de toda entrega: **regra → spec → OK do Kevin → contrato → testes → código → docs com saída real → PR**. O detalhe e o porquê estão em [`docs/sdd/`](sdd/README.md). Três regras que valem para a IA e para o humano:

1. **Nada de código sem spec aprovada.** A spec tem decisões de desenho (E1, E2...) e critérios de aceite numerados (A1, A2...).
2. **Cada critério vira um teste** com o número no nome (`"A7: ..."`).
3. **Número só de execução real.** "26 testes" está escrito porque o `./mvnw verify` rodou e disse 26.

## 3. Linha do tempo

| Quando | Etapa | O que saiu | Onde ver |
|---|---|---|---|
| 13/11/2026 | **Fundação** | dossiê com regras R1–R32 e hipóteses, modelo de domínio, máquina de estados, DDL proposto, contrato OpenAPI v1.0, ADRs 0001–0010 | [contexto](contexto.md), [ADRs](adr/README.md) |
| 13/11/2026 | **Revisão de arquitetura** | HMAC na idempotência, expiração com trava ordenada, cobrança gravada antes do gateway, reprocessamento de webhook | [decisões D17–D30](planejamento/decisoes.md) |
| 16/11/2026 | **Regras reais** | pagamento 100% antecipado (Pix ou cartão), sem cancelamento pelo cliente, aceite obrigatório, catálogo e horário reais; contrato v1.1 | decisões D31–D39, [PR #3](https://github.com/k3vinrich4rd/barbearia-altas-horas/pull/3) |
| 16/11/2026 | **Fatia 1** | esqueleto Spring Boot 4, 8 módulos, erros RFC 9457, `Clock`, Flyway V1 com `EXCLUDE USING gist`, seed, Compose, CI; **26 testes** | [spec](sdd/fatia-1-esqueleto.md), [PR #4](https://github.com/k3vinrich4rd/barbearia-altas-horas/pull/4) |
| 16/11/2026 | **Fatia 2** | spec com 15 decisões e 29 critérios, aprovada e implementada: catálogo, política e disponibilidade; hexagonal seletivo (ADR 0011); **69 testes** | [spec](sdd/fatia-2-catalogo-disponibilidade.md), [PR #6](https://github.com/k3vinrich4rd/barbearia-altas-horas/pull/6) |
| 17/11/2026 | **RAG e release** | busca híbrida na documentação (Spring AI + pgvector), Copilot e Actions documentados; tudo mergeado na `main` | [RAG](ia/rag-vetorial.md), [PR #9](https://github.com/k3vinrich4rd/barbearia-altas-horas/pull/9) |
| 17/11/2026 | **Fatia 3** | spec com 25 decisões e 44 critérios, aprovada e implementada: cadastro, login, refresh rotativo, troca de senha, admin inicial, limite de tentativas, negar por padrão e CSRF onde há cookie; **115 testes** | [spec](sdd/fatia-3-conta-e-acesso.md) |
| a seguir | Fatias 4 a 8 | uma spec por vez | [roadmap](../README.md#-roadmap-em-fatias) |
| 24/11/2026 | **Fatia 11** | alcance por nível: região só com consentimento e sem guardar o CEP (11A), pessoas distintas, k e subida de nível (11B), vitrine em faixas (11C), painel por estado, região e município e as telas (11D); teste de propriedade com 200 sementes | [spec](sdd/fatia-11-alcance-por-nivel.md), [D162 a D170](planejamento/decisoes.md) |
| 23/11/2026 | **Fatia 10** | convite e login do barbeiro: política única `AcessoAgenda` com testes de isolamento A×B (10A) e convite de uso único, aceite com o próprio e-mail, desligamento que mantém o histórico e telas em layout neutro (10B); só o dono mexe na agenda de outro | [spec](sdd/fatia-10-convite-do-barbeiro.md), [D153 a D155](planejamento/decisoes.md) |

## 4. O que existe no código hoje (Fatia 1), arquivo por arquivo

| Arquivo | O que faz | Conceito para estudar |
|---|---|---|
| `backend/pom.xml` | Java 25, Spring Boot 4.1.2, Modulith, Testcontainers, JaCoCo, enforcer | BOM, escopo `test`, plugin enforcer |
| `AltasHorasApplication.java` + `*/package-info.java` | a aplicação e os 8 módulos (`@ApplicationModule`) | monólito modular ([ADR 0001](adr/0001-monolito-modular-com-spring-modulith.md)) |
| `comum/erro/CodigoErro.java` | todos os códigos de erro do contrato, com status e título | enum como contrato estável |
| `plataforma/web/TratadorGlobalDeErros.java` | transforma qualquer erro em `application/problem+json` | `@RestControllerAdvice`, RFC 9457 ([ADR 0005](adr/0005-erros-rfc-9457.md)) |
| `plataforma/web/RastreioFilter.java` | `traceId` por requisição (log, cabeçalho e corpo do erro) | filtro, MDC, validação de entrada |
| `plataforma/tempo/RelogioConfig.java` | o único lugar que lê o relógio | `Clock` injetado ([ADR 0004](adr/0004-tempo-e-dinheiro.md)) |
| `db/migration/V1__agenda.sql` | o schema inteiro, com as constraints que protegem as regras | `EXCLUDE USING gist`, `tstzrange '[)'`, `CHECK`, índice parcial ([ADR 0003](adr/0003-anti-double-booking-no-banco.md)) |
| `db/dev/R__seed_dev.sql` | barbeiro, catálogo e jornada reais, só no perfil `dev` | migração repetível, `ON CONFLICT` |
| `application*.yml` | perfis `dev` e `prod`, Hikari com 5 conexões | configuração por perfil, falhar cedo em `prod` |
| `compose.yaml` | PostgreSQL 18 local na 5433 | healthcheck, volume |
| `.github/workflows/` | build + testes, CodeQL, gitleaks | CI que prova que os testes de banco rodaram |
| testes (`*IT`, `*Test`) | um por critério da spec | Testcontainers, concorrência com `CountDownLatch`, ArchUnit |

**Como conferir que você entendeu:** rode o [guia de testes](guia-testes.md) partes 0 a 3 e explique em voz alta o "o que prova" de cada passo.

## 5. As ferramentas de IA do projeto

### 5.1 O índice: `CLAUDE.md`
[`CLAUDE.md`](../CLAUDE.md) é lido pela IA no começo de cada sessão. Diz a regra de ouro (SDD), **onde está cada informação** e os padrões do código. Foi escrito depois da Fatia 1, a partir do que de fato se repetiu (Clock, centavos, `CodigoErro`, Testcontainers).

### 5.2 Recuperação de contexto: `scripts/buscar-contexto.sh`
```bash
sh scripts/buscar-contexto.sh R15      # a regra, a decisão, a spec, o contrato e o código que citam R15
```
É a "memória" do projeto. Como tudo tem identificador estável (R#, D#, E#, A#, código de erro), a busca exata é mais precisa e mais barata que um RAG com banco vetorial ([por quê](sdd/README.md#por-que-não-um-rag-com-banco-vetorial)).

Irmão dele, o **eval do processo**: `sh scripts/conferir-spec.sh 1 --exigir` confere que cada critério `A#` da spec tem um teste com esse número (Fatia 1: 10 de 10). É o que impede dizer "pronto" com critério sem teste, seja a IA ou eu.

E o **RAG vetorial** ([`docs/ia/rag-vetorial.md`](ia/rag-vetorial.md)): Spring AI 2.0.2 + pgvector + embeddings locais em [`ferramentas/rag/`](../ferramentas/rag). Montado para medir: só vetor acertou 10 de 17 perguntas (nenhum identificador); a busca híbrida, 15 de 17.

### 5.3 Skills (`.claude/skills/`)
Uma skill é uma receita em Markdown que o Claude Code carrega quando o comando é chamado. Cada uma nasceu de um trabalho feito **uma vez à mão** e depois escrito como passo a passo, para a próxima fatia sair igual.

| Skill | O que faz | De onde veio | Como usar |
|---|---|---|---|
| [`/especificar-fatia N`](../.claude/skills/especificar-fatia/SKILL.md) | junta regras, contrato e migração, escreve a spec em `docs/sdd/` e **para** para o OK | da escrita das specs das Fatias 1 e 2 (inclusive o cuidado de conferir dia da semana por código, depois de quase errar uma data) | `/especificar-fatia 3` |
| [`/implementar-fatia N`](../.claude/skills/implementar-fatia/SKILL.md) | contrato → testes → código → docs reais → PR, explicando os conceitos | da implementação da Fatia 1 | `/implementar-fatia 2` (com a spec aprovada) |
| [`/verificar-contrato`](../.claude/skills/verificar-contrato/SKILL.md) | compara controllers, DTOs e `CodigoErro` com o OpenAPI; só relata | necessidade a partir da Fatia 2, quando surgem as primeiras rotas | `/verificar-contrato` antes da PR |

### 5.4 Agente (`.claude/agents/`)
| Agente | Papel | Por que existe |
|---|---|---|
| [`revisor-de-fatia`](../.claude/agents/revisor-de-fatia.md) | segundo par de olhos: confere o diff contra a spec (todo A# tem teste que prova de verdade), contrato, padrões, segurança e LGPD. Só lê | quem escreveu o código tende a não ver o próprio erro; um agente com contexto limpo e só leitura revisa sem esse viés |

### 5.5 GitHub Copilot e as Actions
O Copilot lê as mesmas regras pelo [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) e trabalha no ambiente do [`copilot-setup-steps.yml`](../.github/workflows/copilot-setup-steps.yml). O que cada workflow faz e por quê: [`docs/automacao.md`](automacao.md).

### 5.6 Quem fez o quê
| Ferramenta | Uso |
|---|---|
| **Claude Code** | specs, backend, testes, CI e documentação, sempre rodando os comandos de verdade (Maven, Docker, lint, gitleaks) |
| **GitHub Copilot** | segunda revisão das PRs e tarefas pequenas e bem especificadas (uma issue com o critério da spec), sempre barrado pelo mesmo CI |
| **Outras IAs** (pesquisa e crítica) | na fundação, pesquisa com fonte e data e ataque à arquitetura; o que foi aceito virou decisão com fonte em [decisoes.md](planejamento/decisoes.md), inclusive os pontos em que a IA errou e a fonte oficial prevaleceu |
| **IA no frontend** | Fatia 7: telas geradas a partir do contrato; a ferramenta e o processo serão documentados aqui quando acontecer |
| **Kevin** | decide as regras, aprova cada spec, revisa cada PR, faz o merge e o release |

## 6. Erros reais do caminho (e o que viraram)

| O que aconteceu | Como foi pego | O que virou |
|---|---|---|
| A API conectou num PostgreSQL instalado no Windows em vez do container | erro de senha ao subir o perfil `dev` | porta 5433 no compose (D40) |
| O gitleaks acusou dois exemplos fictícios do contrato | varredura local antes do push | `.gitleaks.toml` liberando só essas duas strings |
| Uma regra do ArchUnit não compilou | `./mvnw verify` | regra reescrita com `noFields()` |
| O Hibernate leu a jornada das 07:00 como 04:00 | teste A27 (`expected 07:00 but was 04:00`) | `java_time_use_direct_jdbc` (D46) |
| 12 inserções simultâneas terminaram em deadlock (`40P01`) em vez de `23P01` | `ConcorrenciaReservaIT` falhou com a máquina sob carga | o teste passou a aceitar os dois; o invariante (uma vencedora) não mudou (D47) |
| `SELECT DISTINCT` + `ORDER BY lower(...)` recusado pelo PostgreSQL | 15 testes com `500` na primeira execução | `JOIN FETCH` sem `DISTINCT` (D48) |
| O validador de JWT do Spring usa o relógio do sistema: o teste de token vencido dependeria do dia em que roda | desenho da Fatia 3 | validador com o `Clock` injetado (D55) |
| O CodeQL acusou o CSRF desligado na PR da Fatia 3 | check de segurança vermelho | em vez de dispensar o alerta, CSRF ligado e exigido onde há cookie, com teste (D58) |
| O CORS não respondia: o bean se chamava `cors` e o Spring Security procura `corsConfigurationSource` | teste A35 | bean renomeado |
| A spec pedia comparar a versão da credencial no refresh, mas o refresh é opaco e não carrega versão | ao implementar | troca de senha revoga os refresh; spec e decisão D56 atualizadas |
| Uma data de teste quase foi tratada como segunda-feira (era terça) | conferência por código | passo obrigatório na skill `/especificar-fatia` |

## 7. Como repetir em outro projeto

1. Escreva as regras numeradas e as decisões com data e fonte.
2. Copie [`docs/sdd/modelo-de-spec.md`](sdd/modelo-de-spec.md), o [`CLAUDE.md`](../CLAUDE.md) e as skills; troque o domínio.
3. Uma fatia por vez: spec, OK, testes, código, saída real.
