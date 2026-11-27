# Ritmo e movimento

**Objetivo:** o site ganha um ritmo único de movimento, só com `transform` e `opacity`, desligável por "Sem animação" e por `prefers-reduced-motion`.
**Usuários:** qualquer visitante; em especial quem prefere menos movimento.
**Fora do escopo:** animar o herói da home; a linha "Trocar" do serviço escolhido (outra fatia); entrada das seções ao rolar (ritmo da home).

## Decisões de implementação

**Decisão:** entram quatro tokens em `design/tokens.json`, gerados em `tokens.css`: `--mov-rapido` (150 ms), `--mov-medio` (200 ms), `--mov-lento` (250 ms) e `--mov-curva` (`cubic-bezier(0.2, 0.7, 0.2, 1)`). Nenhuma duração literal permanece no CSS da aplicação; o verificador de tokens reprova `ms` e `s` soltos.

**Por quê:** as durações de 150 a 200 ms já existiam escritas à mão; a direção visual pede também 250 ms no título da confirmação e uma curva só. Centralizar no `tokens.json` evita número solto e deixa o verificador (A56) cobrir o ritmo.

**Decisão:** no toque, o botão usa `scale(0.98)` em 150 ms; o chip de dia ou horário escolhido pulsa 1 → 1,02 → 1 em 150 ms; o aviso e os horários do dia entram em opacidade (os horários sem deslocar o layout) em 200 ms; o título da confirmação entra com `translateY` de `--space-2` (8 px) em 250 ms; no desktop, o cartão sobe `--space-1 / 2` (2 px) no hover, em 200 ms. Tudo zera com `data-movimento="reduzido"` ou `prefers-reduced-motion: reduce` (regras que já existiam no fim de `globais.css`).

**Por quê:** é o quadro da seção 6 da direção visual, só com tokens e propriedades que não alteram o layout (nada de `width`, `height`, `top` ou sombra).

**Decisão:** as decisões desta melhoria ficam nesta spec, não em `docs/planejamento/decisoes.md`.

Testes: `verificar:tokens`; Playwright `e2e/movimento-reduzido.spec.ts` (A58 e HG-2).
