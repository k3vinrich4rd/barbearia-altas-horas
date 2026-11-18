# 🚀 Ativar o deploy grátis da API (Fatia 8)

| | |
|---|---|
| 🎯 **Para que serve** | colocar a API no ar (Render) com o banco no Aiven PostgreSQL free e o backup semanal no GitHub, **sem pagar nada** |
| ⏰ **Quando usar** | só depois do OK do Kevin; nada deste guia foi feito ainda (nenhuma conta, serviço ou banco foi criado) |
| 🔗 **Spec e decisões** | [spec da Fatia 8](../sdd/fatia-8-deploy.md) · D66 e D115 a D120 em [decisoes.md](decisoes.md) · [banco-na-nuvem.md](banco-na-nuvem.md) |
| 🔒 **Divisão de tarefas** | quem faz o quê, entre a equipe, está na documentação privada da equipe. Este guia é só o passo técnico e vale para qualquer pessoa |

> **Regra de ouro:** nenhuma senha, URL de banco ou chave vai para o repositório, para o chat ou para um print. Cada valor existe só no painel do serviço que o usa.

## Antes de começar (5 min)

- Pagamento fica **simulado** (D59): não é preciso conta em gateway.
- Tenha em mãos: um e-mail para o contato de privacidade e um e-mail e uma senha forte para o **primeiro administrador**.
- Confira os limites no dia (mudam): [Render](https://render.com/pricing), [Aiven free](https://aiven.io/docs/platform/concepts/free-plan).

## Passo 1 · Banco no Aiven (PostgreSQL free)

1. Entre em **console.aiven.io** e crie a conta (sem cartão para o plano free).
2. **Create service** → **PostgreSQL** → plano **Free** → região (anote qual: o Render deve ficar na região mais próxima; o `render.yaml` usa `frankfurt`) → **Create**.
3. Quando o serviço ficar **Running**, abra **Overview** e anote, só para colar no Render: *Host*, *Port*, *Database name* (`defaultdb`), *User* (`avnadmin`) e *Password*.
4. A extensão **`btree_gist`** (a constraint anti double booking depende dela): o Aiven a lista como suportada e a migração V1 faz `CREATE EXTENSION IF NOT EXISTS btree_gist`. Para conferir antes: **Extensions** na tela do serviço, procure `btree_gist`. Se o `avnadmin` não puder criar, ative por ali (ou por `psql`, com `CREATE EXTENSION btree_gist;`). Se não existir, use o plano B (Neon) da [nota do banco](banco-na-nuvem.md).
5. Monte as duas URLs (as senhas ficam só nos painéis):
   - **JDBC, para o Render** (`DB_URL`): `jdbc:postgresql://<HOST>:<PORT>/defaultdb?sslmode=require`
   - **libpq, para o backup** (`DB_URL_BACKUP`): `postgres://<USUARIO>:<SENHA>@<HOST>:<PORT>/defaultdb?sslmode=require`

## Passo 2 · API no Render

1. Entre em **dashboard.render.com** com a conta do GitHub (plano **Free**, sem cartão para web service free).
2. **New** → **Blueprint** → escolha o repositório `barbearia-altas-horas` e a branch **`main`** (o Blueprint é o arquivo [`render.yaml`](../../render.yaml)).
3. O Render lista as variáveis. Elas são de dois tipos:

| Variável | Quem preenche | O que colocar |
|---|---|---|
| `SPRING_PROFILES_ACTIVE`, `APP_PAGAMENTO_MODO` | já vêm fixas | `prod` e `simulado` (não mexer) |
| `JWT_SEGREDO`, `IDEMPOTENCIA_HMAC_SEGREDO`, `GATEWAY_SEGREDO_WEBHOOK` | **o Render gera** (`generateValue`) | nada: ninguém digita; ficam só no painel |
| `DB_URL` | Kevin | a URL JDBC do passo 1.5 |
| `DB_USUARIO` · `DB_SENHA` | Kevin | usuário e senha do Aiven |
| `ADMIN_EMAIL` · `ADMIN_SENHA` | Kevin | e-mail e senha forte do primeiro admin (troca obrigatória no 1º login) |
| `ADMIN_NOME` | Kevin | nome que aparece (opcional; o padrão é "Administrador") |
| `APP_PRIVACIDADE_CONTATO` | Kevin | e-mail de contato do titular da política |
| `CORS_ORIGENS` | Kevin, **depois da Fatia 7** | a URL do front no Vercel, sem barra no fim. Enquanto não houver front, deixe vazio (nenhum navegador acessa a API) |
| `URL_CHECKOUT_SIMULADO` | Kevin, **depois da Fatia 7** | a URL da tela de pagamento simulado do front |

4. **Apply**. O primeiro deploy leva uns 5 a 8 minutos (build da imagem). O `autoDeploy` está desligado: novas versões saem em **Manual Deploy**.

## Passo 3 · Conferir que deu certo

| Conferência | Como | Esperado |
|---|---|---|
| API de pé | abra `https://SEU-SERVICO.onrender.com/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| Migrações | log do deploy (aba **Logs**) | `Successfully applied 6 migrations` (ou o total atual) |
| Perfil | log | `The following 1 profile is active: "prod"` |
| Retenção na subida | log | linhas `Retenção de dados: limpeza=...` |
| Rota pública | `.../servicos` | `200` (lista vazia até o admin cadastrar o catálogo) |
| Admin | login pela API com `ADMIN_EMAIL` | pede a troca de senha (`403 TROCA_DE_SENHA_OBRIGATORIA` nas demais rotas) |
| IP real | os limites por IP contam pelo IP do cliente (testado antes de subir, A12 e A16) | nada a fazer |

## Passo 4 · Backup semanal (GitHub Actions)

1. No GitHub: repositório → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Crie `DB_URL_BACKUP` (a URL libpq do passo 1.5) e `BACKUP_SENHA` (uma frase longa gerada por você, **24 caracteres ou mais**; guarde-a num gerenciador de senhas: **sem ela o backup não abre**).
3. **Actions** → **Backup do banco** → **Run workflow** (teste manual). O artefato `backup-banco-NNN` aparece no fim da execução, guardado por **30 dias**. Depois disso roda sozinho todo domingo (03:00 em Brasília).
4. **Restaurar** (ensaiado em `scripts/testar-backup.sh`):

```bash
gpg -d -o altashoras.dump altashoras-AAAA-MM-DD.dump.gpg          # pede a BACKUP_SENHA
pg_restore -d "URL_DO_BANCO_NOVO" --no-owner --no-privileges altashoras.dump
```

Dica: o GitHub desliga workflows agendados depois de 60 dias sem atividade no repositório; uma execução manual religa.

## Como voltar atrás (rollback)

| Problema | Ação |
|---|---|
| Versão nova com bug | Render → o serviço → **Events** → o deploy anterior bom → **Rollback**. Se o plano não oferecer, faça **Manual Deploy** de um commit anterior da `main` |
| Migração nova quebrou algo | as migrações só andam para frente; corrija com uma migração nova. Só restaure o dump se os dados foram danificados |
| Aiven acabou ou mudou o plano | crie o banco no plano B (Neon), restaure o último dump e troque só `DB_URL`, `DB_USUARIO` e `DB_SENHA` ([nota do banco](banco-na-nuvem.md)) |
| Precisa tirar do ar já | Render → **Settings** → **Suspend Service** |

## O que esperar do plano grátis

- **Custo: R$ 0.** Sem cartão no Render e no Aiven free; o GitHub Actions é grátis para repositório público (e tem cota mensal em privado).
- **Render free:** 512 MB de RAM; o serviço **hiberna após ~15 minutos sem tráfego** e a primeira requisição depois disso espera ~20 a 60 s (cold start). Medido no ensaio local: 13 a 20 s. Há uma cota mensal de horas gratuitas (confira no dia).
- **Cache em memória some** quando a API hiberna (CEP, vitrine): é só otimização (ADR 0008).
- **Aiven free:** 1 GB, `max_connections` 20 (a API usa 5); pode ser desligado se ficar sem uso por muito tempo, com aviso.
- **Retenção de dados:** roda na subida, então a hibernação não atrasa a limpeza de 90 dias.
- **Backups guardam dado pessoal por até 30 dias** (inclusive de quem já excluiu a conta): a política de privacidade precisa dizer isso.
