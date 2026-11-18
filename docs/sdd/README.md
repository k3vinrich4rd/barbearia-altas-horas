# 📐 Spec-Driven Development (SDD) na Barbearia Altas Horas

| | |
|---|---|
| 🎯 **Para que serve** | mostrar como cada fatia nasce: primeiro a **especificação**, depois o contrato, o teste e só então o código |
| 👥 **Para quem** | recrutador, revisor e qualquer IA que for ajudar no código (Claude Code, assistentes de front) |
| ⏰ **Quando usar** | antes de abrir qualquer PR de código |
| 🚫 **Não cobre** | regras de negócio (estão no [dossiê](../contexto.md)) e decisões (estão em [decisoes.md](../planejamento/decisoes.md)) |

## Por que SDD

Projeto **individual**: o Kevin Richard faz o backend com o Claude Code como par de programação, e o frontend é gerado com IA. Quando a IA escreve boa parte do código, o que garante a qualidade não é "pedir bem": é ter uma **especificação verificável** que a IA segue e que os testes cobram. Sem spec, a IA preenche lacunas com suposições; com spec, ela implementa critérios de aceite que viram testes.

| | Sem SDD (prompt solto) | Com SDD |
|---|---|---|
| Fonte da verdade | o histórico do chat | `docs/sdd/fatia-N-*.md` + contrato OpenAPI |
| Ambiguidade | a IA decide sozinha (e muda de ideia) | decidida na spec, com data e motivo |
| Prova de pronto | "parece funcionar" | cada critério Dado/Quando/Então tem um teste com nome |
| Revisão | ler todo o diff | conferir o diff contra a spec |
| Troca de ferramenta de IA | perde o contexto | o contexto está no repositório |

## O fluxo de cada fatia

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
    R[Regra de negócio<br/>R# no dossiê] --> S[📐 Spec da fatia<br/>Dado / Quando / Então]
    S --> OK{OK do Kevin}
    OK -- ajustes --> S
    OK -- aprovada --> C[📜 Contrato<br/>OpenAPI + lint]
    C --> T[🔴 Testes<br/>um por critério]
    T --> I[🟢 Código<br/>Claude Code + Kevin]
    I --> V[✅ verify + CI<br/>Testcontainers, ArchUnit]
    V --> D[📚 Docs com saída real<br/>guia, README]
    D --> PR[PR para a develop]
```

1. **Spec** (`docs/sdd/fatia-N-*.md`, a partir do [modelo](modelo-de-spec.md)): escopo, regras citadas, contrato, decisões de desenho, **critérios de aceite numerados** e o teste que prova cada um.
2. **Aprovação**: o Kevin lê e dá o OK. Nenhuma linha de código de produção antes disso.
3. **Contrato**: rota, schema e erros no [`openapi.yaml`](../contrato/openapi.yaml), com `redocly lint` sem erro.
4. **Testes primeiro**, com o nome do critério (`A3: ...`) no `@DisplayName`.
5. **Código** até os testes passarem; **docs** só com saída de execução real.
6. **PR** para a `develop` com o [checklist](checklist-pr.md). A `main` só recebe release.

## Specs

| Fatia | Spec | Status |
|---|---|---|
| 1 · Esqueleto + Flyway V1 + Compose | [fatia-1-esqueleto.md](fatia-1-esqueleto.md) | ✅ implementada (26 testes) |
| 2 · Catálogo + disponibilidade | [fatia-2-catalogo-disponibilidade.md](fatia-2-catalogo-disponibilidade.md) | ✅ implementada (69 testes no total) |
| 3 · Conta e acesso | [fatia-3-conta-e-acesso.md](fatia-3-conta-e-acesso.md) | ✅ implementada (115 testes no total) |
| 4 · Agendamento e pagamento | [fatia-4-agendamento-e-pagamento.md](fatia-4-agendamento-e-pagamento.md) | ✅ aprovada (17/11) · 4A pronta (A1 a A21) · 4B em seguida |
| 5 · Painel do barbeiro e do admin | [fatia-5-painel-do-barbeiro.md](fatia-5-painel-do-barbeiro.md) | ✅ concluída (17/11): 5A e 5B, 71/71 critérios |
| 6 · CEP e privacidade (LGPD) | [fatia-6-cep-e-privacidade.md](fatia-6-cep-e-privacidade.md) | ✅ aprovada (17/11) · **6A CEP (A1 a A22): implementada (A1 a A22), aguardando revisão** · 6B privacidade (A23 a A61) · 6C alcance por região (A62 a A75) |
| 7 e 8 | escritas uma de cada vez, antes da fatia começar | ⏳ |

## A IA no processo

| Recurso | Para quê | Onde |
|---|---|---|
| **Claude Code** (par de programação do backend) | escreve spec, testes e código com o Kevin; roda `./mvnw verify`, Docker e lint de verdade antes de afirmar qualquer número | terminal / app |
| **Skills do projeto** | receitas repetíveis: `/especificar-fatia`, `/implementar-fatia`, `/verificar-contrato` | [`.claude/skills/`](../../.claude/skills/) |
| **Agente `revisor-de-fatia`** | revisão independente, só leitura, do código contra a spec | [`.claude/agents/`](../../.claude/agents/revisor-de-fatia.md) |
| **Eval do processo** | `scripts/conferir-spec.sh N --exigir`: todo critério `A#` da spec tem teste com esse número; roda no CI para as fatias implementadas | [`scripts/`](../../scripts/conferir-spec.sh) |
| **Contexto no repositório** | o [`CLAUDE.md`](../../CLAUDE.md) diz onde está cada coisa; `scripts/buscar-contexto.sh R15` traz a regra, a decisão e os testes ligados a ela | raiz do repositório |
| **IA no frontend** | o Next.js é gerado com IA a partir do contrato OpenAPI e das specs de tela; o Kevin revisa e os testes cobram | Fatia 7 |

### Por que não um RAG com banco vetorial

Para o dia a dia, a **recuperação por identificador** continua sendo a regra: o conhecimento cabe em ~40 arquivos com identificadores estáveis (R1 a R32, D1 a D50, códigos de erro, A1 a A29), e `buscar-contexto.sh` encontra a linha exata, de graça.

Depois eu montei o RAG vetorial mesmo assim, para **medir** em vez de opinar ([`docs/ia/rag-vetorial.md`](../ia/rag-vetorial.md)): numa lista de 17 perguntas, o vetor sozinho acertou 10 e errou **todos** os identificadores; a busca híbrida (exata para identificador, vetor para pergunta em linguagem natural) acertou 15. Conclusão: as duas se completam, e o identificador estável segue sendo o que mais ajuda a IA.
