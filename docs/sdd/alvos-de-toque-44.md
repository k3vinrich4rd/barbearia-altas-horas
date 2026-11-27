# Alvos de toque de 44 px nos links soltos

**Objetivo:** todo link ou botão solto (fora de um parágrafo) com pelo menos 44 px de altura de toque, como pede o PDF.
**Usuários:** todos no celular, sobretudo quem tem tremor, baixa visão ou dedo grande.
**Fora do escopo:** links dentro de parágrafo corrido (a WCAG 2.5.8 os dispensa), cores, textos e ordem.

## Referências (link + o que copiar)
- PDF *Site · Barbearia Altas Horas*, p.22: "Alvos de pelo menos 44×44px."
- WCAG 2.2, 2.5.8 Tamanho do alvo (mínimo, 24 px): https://www.w3.org/TR/WCAG22/ · o mínimo legal; o desenho pede mais.
- Apple HIG: https://developer.apple.com/design/human-interface-guidelines/ · 44 pt como alvo confortável (só referência).

## Situação no ar (26/11/2026, Playwright 360×800)
| Rota | Elemento | Tamanho | Arquivo |
|---|---|---|---|
| `/` | "Ver todos os serviços e preços" | 218×17 | `ListaDeServicos.tsx:52` |
| `/entrar` | "Criar conta" | 78×17 | `Entrar.tsx:54` (e `:81` no texto "Ainda não tem conta?") |
| `/cadastro` | "Entrar" | 44×17 | `Cadastro.tsx:107` (e `:195` em "Já tem conta?") |
| `/cadastro` | "Saiba mais na política de privacidade" e "Política de privacidade" | 320×33 e 282×41 | `Cadastro.tsx` (conferir se estão dentro de frase; se estiverem, ficam de fora) |
| `/privacidade` | 8 links do sumário "Nesta página" | ~17 de altura | `Privacidade.tsx:45-47` |
| `/nao-existe` | "Voltar para o início", "Agendar horário", "Consultar reserva" | 24 de altura | `app/not-found.tsx` |

## Fluxo
Não muda.

## Telas
- Uma classe utilitária nos estilos (ex.: `.link-solto`) com `display: inline-flex; align-items: center; min-height: 44px` usando só tokens (o `verificar:tokens` tem que passar). Aplicar nos elementos da tabela.
- Sumário da privacidade: cada `li` com `min-height` de 44 px, sem aumentar a fonte.
- Os links dentro de frase ("Ainda não tem conta? Criar conta") ganham a altura pelo padding vertical, sem quebrar a linha.
- **Desktop:** a mesma regra (não atrapalha) ou só abaixo de 48rem, a critério do implementador; registrar no PR.

## Estados
Foco: o anel de 3 px afastado 3 px (PDF p.22) contorna a área nova inteira.

## Conteúdo e microcopy
Nenhum texto muda.

## Acessibilidade
2.5.8 (mínimo), 2.4.7 e 2.4.13 de referência (foco visível), 1.4.10 (reflow em 320 px), 1.4.12 (espaçamento de texto).

## Dados e LGPD
Nenhum.

## Critérios de aceite (Dado/Quando/Então)
| # | Dado | Quando | Então |
|---|---|---|---|
| AT-1 | 360×800 | mede os elementos da tabela | todos com altura ≥ 44 px (Playwright, `getBoundingClientRect`) |
| AT-2 | 360×800, rotas `/`, `/agendar`, `/agendar/dados`, `/servicos`, `/reserva`, `/entrar`, `/cadastro`, `/privacidade`, `/nao-existe` | lista `a, button, summary` visíveis fora de `p` | nenhum com altura < 44 px (os inputs de rádio de 13 px ficam de fora: o alvo é o `label` inteiro) |
| AT-3 | foco por teclado | foca cada link da tabela | o anel de foco aparece inteiro e não sobrepõe o vizinho |
| AT-4 | 320 px e zoom de 200% | abre as rotas | sem rolagem horizontal |
| AT-5 | as rotas de AT-2, 360×800 e 1280×800 | roda o axe | 0 violações; `npm run verificar:tokens` passa |

Testes: Playwright (novo caso em `e2e/acessibilidade.spec.ts`: AT-1, AT-2, AT-4, AT-5), revisão manual de AT-3.

## Métricas de sucesso
Elementos soltos abaixo de 44 px no celular: de 16 (medidos hoje nas rotas públicas, alguns talvez dentro de frase) para 0 entre os que não estão em frase.

## Riscos e perguntas abertas
- Mexer em `globais.css` junto com a spec do resumo fixo: fazer em sequência para não conflitar.
- Arquivos prováveis: `frontend/src/estilos/globais.css`, `frontend/src/componentes/ListaDeServicos.tsx` (52), `frontend/src/telas/Entrar.tsx` (54, 81), `frontend/src/telas/Cadastro.tsx` (107, 195), `frontend/src/telas/Privacidade.tsx` (45-47), `frontend/src/app/not-found.tsx`, `frontend/e2e/acessibilidade.spec.ts`.

## Decisões de implementação

- A classe utilitária é `.link-solto`, com `display: inline-flex`, `align-items: center` e `min-height: var(--alvo-toque)`. O token `--alvo-toque` já existia em `globais.css` (`calc(var(--space-12) - var(--space-1))` = 44 px).
- A mesma regra vale no desktop (não atrapalha e evita um media query extra).
- O sumário da política (`ol.sumario-politica li`) e os links do rodapé (`.rodape-links li`) também têm `min-height: var(--alvo-toque)`, para o AT-2 (links fora de parágrafo) passar nessas rotas.
- Links dentro de frase ("Ainda não tem conta? Criar conta", "Já tem conta? Entrar", "Saiba mais na política de privacidade") usam a mesma classe: a altura vem do padding vertical, sem quebrar a linha.
- AT-3 (anel de foco inteiro, sem sobrepor o vizinho) fica para revisão manual: o anel já é de 3 px afastado 3 px (`:focus-visible` em `globais.css`) e contorna a área nova.
