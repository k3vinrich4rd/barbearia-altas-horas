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

4. **Apply**. O primeiro deploy leva uns 5 a 8 minutos (build da imagem).
5. **Quando o Render publica (Fatia 8B):** o `render.yaml` usa `autoDeployTrigger: checksPass` na branch `main`: o Render só publica um commit da `main` **depois** que os checks do GitHub (Backend CI e Frontend CI, que rodam em todo push na `main`) passam. A `main` só recebe o PR de release `develop → main`, que exige CI verde. O **Manual Deploy** continua no painel (deploy de emergência, se o CI estiver fora do ar). Conferir em **Events** do serviço: um commit com check vermelho aparece como "checks failed" e **não** publica (A3).
6. **Vercel (A4):** em **Settings → Git** do projeto, confirme **Production Branch = `main`**; `develop` e PRs geram só **Preview**. O gate de CI do front é o PR de release (D-1); o plano Hobby não tem "Deployment Checks" bloqueante.

## Passo 3 · Conferir que deu certo

| Conferência | Como | Esperado |
|---|---|---|
| API de pé | abra `https://SEU-SERVICO.onrender.com/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| Liveness (o que o Render usa) | `.../actuator/health/liveness` e `.../actuator/health/readiness` | `{"status":"UP"}`, sem detalhes. O `healthCheckPath` do Render é o **liveness**: se o banco oscilar, o Render **não** reinicia uma API saudável |
| Tudo de uma vez | `scripts/verificar-no-ar.sh` | `No ar: todas as checagens passaram` (health completo, home do front, CORS, `/pagamento-simulado`, front e API iguais) |
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

### O que fica com o Kevin (Secrets e roteiros, Fatia 8B)

Hoje o backup **falha em 7 s** porque os dois segredos ainda não existem (`gh secret list` vazio em 22/11/2026). Sem eles **não há backup**. Passos, na ordem (cada um só com o Kevin logado; **nenhum valor** vai para o chat ou para o repositório):

| # | Passo | Prova |
|---|---|---|
| 1 | cadastrar `DB_URL_BACKUP` e `BACKUP_SENHA` (≥ 24 caracteres; guarde num gerenciador de senhas) em Settings → Secrets and variables → Actions | `gh secret list` mostra os **dois nomes** (A29) |
| 2 | `gh workflow run "Backup do banco"` e esperar | termina verde e o artefato `altashoras-AAAA-MM-DD.dump.gpg` existe, com tamanho > 0 (A30): `gh run list` e `gh run download` |
| 3 | `ARQUIVO=altashoras-AAAA-MM-DD.dump.gpg scripts/testar-backup.sh --real` (pede a senha, sem eco) | restaura num PostgreSQL 18 local vazio: tem `btree_gist`, a constraint `reserva_sem_sobreposicao` e as migrações no `flyway_schema_history`; senha errada não abre (A31) |
| 4 | `gh workflow run "Manter banco ligado"` | verde em menos de 2 min (A23); depois, o Aiven mostra a conexão no painel de uso |
| 5 | aplicar o diff do ping no repositório do **perfil** ([`ping-perfil.diff.md`](ping-perfil.diff.md)) **depois** do passo 4 | `scripts/horas-render.sh --conferir` verde; `workflow_dispatch` com a API dormindo acorda e termina verde em até 8 min (A20, A21) |

O repositório é **público** e artefato de repositório público é baixável por qualquer conta logada no GitHub: o dump só sai **criptografado** (gpg AES-256) e a `BACKUP_SENHA` fraca ou vazada expõe dados de clientes (LGPD, ADR 0009). Trocar a senha não recriptografa os dumps antigos: anote a data de cada senha.

O **keep-alive do banco** (`.github/workflows/manter-banco.yml`) usa o mesmo `DB_URL_BACKUP`: abre uma conexão direta (`select 1`, `postgres:18-alpine`) duas vezes por dia, **sem acordar a API** (0 h do Render). Nunca roda em PR e nunca imprime a URL. Se algum dia houver um usuário de leitura no Aiven, o ideal é um segundo segredo (D-6).

## Como voltar atrás (rollback, Fatia 8B)

Três degraus, do mais barato ao mais caro. **Depois de qualquer um, confirme com `scripts/verificar-no-ar.sh`** (deve terminar com "No ar: todas as checagens passaram").

| Degrau | Quando | Ação |
|---|---|---|
| **1. Rollback no Render** | versão nova com bug, banco intacto | Render → o serviço → **Events** → um dos **dois deploys anteriores** bons → **Rollback**. O plano Free oferece, mas **só para os dois deploys mais recentes** (conferido na documentação do Render em 23/11/2026). Leva o tempo de uma subida (~2 a 3 min) |
| **2. Manual Deploy de commit anterior** | o deploy bom já saiu da lista dos dois últimos | Render → o serviço → **Manual Deploy** → **Deploy a specific commit** → um commit anterior da `main` |
| **3. `git revert` + PR** | o erro está no código e precisa sair da `main` de vez | `git revert -m 1 <merge-da-release>` numa branch a partir da `develop` → PR para a `develop` → PR de release para a `main` (**nunca** push direto na `main`). O `checksPass` só publica depois do CI verde |

**Regra de ouro do schema (só para frente):** o Flyway não desfaz migração. Por isso **toda migração nova precisa ser compatível com a versão anterior do código** (expandir → migrar → contrair, em releases separadas): sem isso, o degrau 1 ou 2 pode deixar a API antiga diante de um schema novo, ou a nova sem schema. Está no [checklist da PR](../sdd/checklist-pr.md).

| Problema | Ação |
|---|---|
| Migração nova quebrou algo | as migrações só andam para frente; corrija com uma migração nova. Só restaure o dump se os dados foram danificados |
| Aiven acabou ou mudou o plano | crie o banco no plano B (Neon), restaure o último dump e troque só `DB_URL`, `DB_USUARIO` e `DB_SENHA` ([nota do banco](banco-na-nuvem.md)) |
| Precisa tirar do ar já | Render → **Settings** → **Suspend Service** |
| O CI está fora do ar e é urgente | o `checksPass` bloqueia; use o **Manual Deploy** (degrau 2) |

### Ensaio de rollback e de e-mail de falha (roteiro do Kevin, A27 e A28, uns 15 min)

1. Depois do primeiro deploy com `checksPass`, rode `scripts/verificar-no-ar.sh` (verde).
2. Render → **Events** → **Rollback** para o deploy anterior (ou **Manual Deploy** de um commit anterior). Anote o tempo e se o botão **Rollback** apareceu.
3. `scripts/verificar-no-ar.sh` de novo (verde) e volte ao deploy atual (**Manual Deploy** do último commit da `main`); verde outra vez.
4. E-mail de falha: no repositório do perfil, rode o ping por `workflow_dispatch` apontando `API` para uma URL inválida, uma vez. Em até 10 min deve chegar o e-mail do GitHub. Registre no [guia de testes](../guia-testes.md).

## Alertas (E15, só o que já é grátis e chega por e-mail)

- **GitHub:** Settings → Notifications → Actions → **Send notifications for failed workflows only**. O ping falho vira e-mail: é o alarme de "API fora do ar" (o ping usa o health **completo**, que toca o banco).
- **Render:** Account/Workspace → **Notifications**: ligar **Deploy failed** e **Service failed**.
- **Aiven:** avisa por e-mail antes de desligar o banco por inatividade.

## Limites e custo zero (conferido em 23/11/2026)

**Custo: R$ 0.** Todo `plan:` do `render.yaml` é `free`; nenhum serviço pago. Nada aqui exige cartão. Os limites mudam: confira as páginas oficiais antes de decidir algo caro.

| Plano | Limite que importa | O que acontece se estourar |
|---|---|---|
| **Render Free** (web service) | **750 h/mês por workspace**, divididas com a API do RKF Bank (conferido); dorme após **15 min** sem tráfego e a volta leva ~1 min a ~3 min (conferido); 512 MB de RAM (a imagem foi medida a 512 MiB); minutos de build do plano | os serviços free do workspace ficam **suspensos até o mês virar** (ninguém é cobrado sem cartão); a Barbearia sai do ar junto com o RKF Bank. Sem minutos de build, os builds novos ficam desligados e a API segue no ar |
| **Aiven PostgreSQL Free** | referência da spec: 1 GB de disco, 1 CPU, 1 GB de RAM, `max_connections` 20, 1 serviço free por conta. **A página de docs consultada não lista esses números**: conferir no console | disco cheio = escritas falham; **sem atividade por muito tempo o serviço é desligado** (aviso por e-mail antes; religa no painel; o prazo exato não está na documentação) |
| **Vercel Hobby** | banda, invocações de função e builds do plano; **uso pessoal, não comercial** (D-7) | a plataforma pode pausar o projeto até o ciclo virar; o risco de termos deve ser decidido antes do uso real com clientes |
| **GitHub Actions** | repositório **público**: minutos ilimitados; privado: 2.000 min/mês; 500 MB de artefatos | artefato velho expira em **30 dias**; no público não há estouro de minutos |

### Conta de horas do Render por mês (`scripts/horas-render.sh`)

| Cenário | API acordada | Sobra para o RKF Bank (750 h) |
|---|---|---|
| Antes (ping de 10 min das 07:00 às 19:50 BRT e a cada 3 h o dia todo) | ~335 h | ~415 h |
| **Proposto** (ping de 10 min das 06:40 às 19:50 BRT, ter a sáb, nada fora; banco por conexão direta) | **~291 h** | **~459 h** |
| API 24 h por dia (o que **não** fazer) | 744 h | ~6 h: o RKF Bank cai |

O RKF Bank precisa ficar abaixo de **459 h/mês**. Estes números são **contas**, não medições do Render: o painel de uso do Render é a prova.

## O que esperar do plano grátis

- **Cache em memória some** quando a API hiberna (CEP, vitrine): é só otimização (ADR 0008).
- **Subida fria:** a imagem leva um cache AOT do JDK 25 gerado no `docker build` (Fatia 8B); os tempos medidos estão no [guia de testes](../guia-testes.md#5c-deploy-robusto-fatia-8b).
- **Fora da janela do ping** (domingo, segunda, madrugada) o primeiro acesso ainda espera a subida: é o preço de caber nas 750 h.
- **Aiven free:** a API usa **3** conexões no pool (limite de 20 do Aiven; Flyway e ShedLock também usam).
- **Retenção de dados:** roda na subida, então a hibernação não atrasa a limpeza de 90 dias.
- **Backups guardam dado pessoal por até 30 dias** (inclusive de quem já excluiu a conta): a política de privacidade precisa dizer isso.
