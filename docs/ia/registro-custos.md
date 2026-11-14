# 💸 Registro de custo das IAs

Uma linha por chamada de IA (skill de orquestração, seção "Registro de custo por tarefa"). Horários de Brasília.

| data hora | IA | modelo | finalidade | custo | útil? | subiu de nível? |
|---|---|---|---|---|---|---|
| 2026-11-13 21:05 | Gemini (`agy` 1.5.8, PC do Kevin) | `gemini-3.8-flash-medium` | pesquisa atual com fontes (Spring Boot, gateways, planos grátis) | 59,5k entrada / 9,3k saída / 5,7k raciocínio (cota fixa) | parcial: 3 imprecisões corrigidas pela fonte oficial | não |
| 2026-11-13 21:20 | Gemini (`agy` 1.5.8, PC do Kevin) | `gemini-3.8-flash-medium` | ataque à arquitetura proposta | 12,5k entrada / 3,9k saída / 2,5k raciocínio (cota fixa) | sim: 6 de 8 ataques geraram mudança ou registro | não |

## IAs que faltaram nesta etapa

| IA | Por quê | Quem cobriu |
|---|---|---|
| Claude Code | login OAuth vencido no PC; volta quando o Kevin rodar `claude` uma vez | o coordenador escreveu o domínio, o contrato e os ADRs; a revisão crítica fica com o Alicerce (arquitetura) e com o Claude quando voltar |
| GitHub Copilot | não usado: etapa só de documentação, sem código | — |
