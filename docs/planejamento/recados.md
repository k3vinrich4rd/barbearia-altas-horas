# 📬 Recados entre os agentes

| | |
|---|---|
| 🎯 **Para que serve** | avisos que um agente deixa para outro, para nada se perder entre PRs |
| 👥 **Quem usa** | coordenador, Alicerce (arquitetura), Black (README e orquestração), Decolagem (deploy) e o Kevin |
| ⏰ **Quando usar** | ao abrir ou revisar um PR; apagar o recado quando resolvido |
| 🧰 **Como** | um item por recado, com data, para quem e o que fazer |
| 🚫 **Não cobre** | decisões (vão para [decisoes.md](decisoes.md)) e regras de negócio (HIPÓTESES no [dossiê](../contexto.md#4-hipóteses-em-aberto)) |

## Abertos

### 13/11/2026 · para o Black · nomes dos módulos no README
O README cita os módulos `identidade`, `endereco` e `compartilhado`. A arquitetura do Alicerce usa `conta`, `cep`, `comum` e `plataforma` (8 módulos: `comum`, `plataforma`, `conta`, `cep`, `agenda`, `pagamento`, `privacidade`, `admin`; ver [arquitetura](../arquitetura/README.md)). Não mexi no README porque você está editando. Sugestão: alinhar os nomes e apontar para `docs/arquitetura/README.md`.

### 13/11/2026 · para o Alicerce · três pontos para revisar
1. **CPF não persistido** (ADR 0009) diverge da proposta original; a crítica do Gemini (guardar cifrado) está registrada no próprio ADR.
2. Tabelas **`configuracao_agenda`** e **`usuario_papel`** foram acrescentadas ao modelo (regras como dado e papéis múltiplos); conferir se ficam no módulo certo.
3. **Expiração preguiçosa** mantida com lock ordenado (`ORDER BY id FOR UPDATE`) e nova tentativa em `40P01`, contra a sugestão do Gemini de tirá-la.

### 13/11/2026 · para a Decolagem · deploy
- Hikari com no máximo 5 conexões (Aiven free).
- Medir as flags da JVM com `--memory=512m` antes de fixar números.
- Ping externo para não hibernar: **não adotado**; se for reconsiderado, conferir o consumo das 750 h/mês.
- Vercel Hobby só serve enquanto o projeto for portfólio (RISC-03).

### 13/11/2026 · para o Kevin · respostas pendentes
As HIPÓTESES H1 a H20 do dossiê esperam a conversa com seu pai. As que mais mudam o desenho: **H7** (política do sinal), **H8** (pagamento depois de expirar), **H9** (barbeiro vê só a própria agenda), **H10** (pacote raiz) e **H17** (uso real ou só portfólio).
