# 📐 Spec · Fatia 8 · Deploy grátis do backend

| | |
|---|---|
| **Status** | ✅ aprovada (autonomia de 18/11) · preparada: **nada foi ativado** (nenhuma conta, serviço ou banco criado); quem ativa é o Kevin, com o OK dele, pelo [guia de ativação](../planejamento/ativar-deploy.md) |
| **Fatias do plano** | deploy da Fase 1 (D38): API no Render, banco no Aiven PostgreSQL free (D66), backup semanal fora do provedor. O front no Vercel só entra **depois da Fatia 7** |
| **Branch** | `feat/fatia-8-deploy` |
| **Regras** | R30 (nada pessoal em log), H14/R26 (limites por IP, que dependem do IP real), API8 do dossiê (perfil `prod` sem seed, sem padrões, CORS por variável, health sem detalhes) |
| **ADRs** | [0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) (CORS por `CORS_ORIGENS`, BFF), [0003](../adr/0003-anti-double-booking-no-banco.md) (`btree_gist`), [0008](../adr/0008-cep-com-cache-e-degradacao.md) (cache some na hibernação), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (backups e retenção) |
| **Custo** | **zero** (D38, D59): Render free, Aiven free, GitHub Actions, sem cartão e sem gateway real |
| **Não cobre** | o front e o Vercel (critérios A23 a A26, marcados "depois da 7A") · domínio próprio · monitoramento externo e alertas · pagamento real (Asaas, na saída do beta) · produção paga e lojas mobile (Fase 2) · restauração automática (o restore é manual e ensaiado) |

## 1. Objetivo

Depois desta fatia existe um caminho **pronto, testado na máquina do Kevin e sem segredo no repositório** para colocar a API no ar de graça: uma imagem Docker que sobe no perfil `prod`, um Blueprint do Render, o roteiro de ativação passo a passo e um backup semanal do banco, criptografado, guardado no GitHub. A ativação em si (criar contas e colar valores) é um ato do Kevin.

## 2. Arquitetura da Fase 1

```
navegador ──> Vercel (front, depois da 7A) ──> Render (API, Docker, free) ──> Aiven PostgreSQL 18 (free, TLS)
                                                    ^ Flyway + jobs na subida
GitHub Actions (domingo, 03:00 BRT) ── pg_dump -Fc ── gpg AES-256 ──> artefato por 30 dias
```

| Peça | Escolha | Observação |
|---|---|---|
| API | Render **web service free**, `runtime: docker`, `branch: main`, health `/actuator/health` | 512 MB, hiberna após ~15 min sem tráfego |
| Banco | **Aiven PostgreSQL free** (D66); plano B Neon | `btree_gist` obrigatório; `sslmode=require` |
| Front | Vercel (**depois da 7A**) | só muda `CORS_ORIGENS` e `URL_CHECKOUT_SIMULADO` |
| Backup | GitHub Actions, `pg_dump -Fc` + `gpg --symmetric` | segredos `DB_URL_BACKUP` e `BACKUP_SENHA` cadastrados pelo Kevin |

### Variáveis de ambiente (só nomes; nunca valores)

| Variável | Tipo no `render.yaml` | Para quê |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` · `APP_PAGAMENTO_MODO` | valor fixo (`prod` · `simulado`) | perfil e pagamento simulado (D59) |
| `DB_URL` · `DB_USUARIO` · `DB_SENHA` | `sync: false` (Kevin preenche) | conexão JDBC com o Aiven |
| `JWT_SEGREDO` · `IDEMPOTENCIA_HMAC_SEGREDO` · `GATEWAY_SEGREDO_WEBHOOK` | `generateValue: true` (o Render gera) | assinatura do JWT, HMAC da idempotência, token do webhook |
| `ADMIN_EMAIL` · `ADMIN_SENHA` · `ADMIN_NOME` | `sync: false` | primeiro administrador |
| `APP_PRIVACIDADE_CONTATO` | `sync: false` | canal do titular (D-4 da Fatia 6) |
| `CORS_ORIGENS` · `URL_CHECKOUT_SIMULADO` | `sync: false` | origem e tela do front (depois da 7A) |
| `PORT` | injetada pelo Render | a imagem obedece (`-Dserver.port=${PORT}`) |
| `JAVA_OPTS` | padrão na imagem | heap ~60% de 512 MB, GC serial |

## 3. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Como empacotar | `backend/Dockerfile` multi-stage: `eclipse-temurin:25-jdk` + Maven Wrapper no build; `eclipse-temurin:25-jre-alpine` no runtime; usuário não-root; `SPRING_PROFILES_ACTIVE=prod` por padrão na imagem | o JDK e o Maven não vão para produção; o seed dev nunca chega a um servidor |
| E2 | Memória | `JAVA_OPTS` com `MaxRAMPercentage=60`, `UseSerialGC`, `MaxMetaspaceSize=128m`, `ExitOnOutOfMemoryError` | 512 MB do free; se faltar, o processo sai e o Render reinicia |
| E3 | Segredos | nunca no repositório nem na imagem; o Render **gera** os que são aleatórios (`generateValue`) e o Kevin preenche só banco, admin, contato e front | ninguém digita nem vê o JWT; reduz vazamento |
| E4 | Health check | `/actuator/health` (já público, sem detalhes, D-API8) | o Render usa para saber que subiu |
| E5 | Hibernação | aceita no beta: a 1ª requisição depois de ~15 min espera o cold start (medido: 13 a 20 s local); cache em memória recomeça vazio (ADR 0008); a `RetencaoDeDados` e o `ExpiracaoJob` rodam **na subida**, então dormir não atrasa a limpeza | custo zero; sem ping artificial para "manter acordado" (D-1) |
| E6 | CORS | só as origens de `CORS_ORIGENS` (vírgula), nunca `*`; vazio = nenhum navegador | já implementado (E15 da Fatia 3); aqui só é provado na imagem |
| E7 | IP real | `server.forward-headers-strategy: framework` no perfil `prod`: `request.getRemoteAddr()` vira o do `X-Forwarded-For` | limites por IP (login, cadastro, visitante, CEP, vitrine) e a auditoria seriam todos do IP do proxy sem isto. Risco e proposta em D-2 |
| E8 | Flyway | roda na subida (`ddl-auto: validate`); a V1 faz `CREATE EXTENSION IF NOT EXISTS btree_gist` | o Aiven lista a extensão como suportada; se o usuário não puder criar, o guia manda ativar pelo painel |
| E9 | Backup | `.github/workflows/backup-banco.yml`: cron semanal + `workflow_dispatch`, **nunca** em PR; `pg_dump -Fc` com a imagem `postgres:18-alpine` (mesma versão do banco), `gpg --symmetric --cipher-algo AES256`, artefato `.dump.gpg` por 30 dias; só o arquivo criptografado é enviado | o plano grátis pode acabar (D66); dump tem dado pessoal, então criptografa |
| E10 | Rollback | (a) código: rollback do deploy no Render ou novo deploy de um commit anterior da `main`; (b) schema: só para frente (migração corretiva); (c) dados: restaurar o último `.dump.gpg` em banco novo e trocar `DB_*` | Flyway não desfaz migração; o dump cobre o desastre |
| E11 | Quem ativa | só o Kevin, com o OK dele, pelo guia; esta fatia não cria conta nem serviço | regra de segurança do projeto |

## 4. Critérios de aceite

Todos rodam **na máquina**, sem conta em provedor. `scripts/testar-imagem.sh` constrói a imagem, sobe um PostgreSQL 18 e a API no perfil `prod` com variáveis **falsas** (`--memory=512m`, `PORT=10000`) e confere A1 a A15. Os testes Java rodam no `./mvnw verify`.

### 8A · A imagem e o perfil prod

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | `backend/Dockerfile` e `.dockerignore` | `docker build -t altas-horas-api:fatia8 backend` | a imagem é construída sem testes e sem segredo no contexto | `scripts/testar-imagem.sh` |
| A2 | a imagem final | procurar `javac`, `mvn`, `mvnw` | nenhum existe (só JRE) | `scripts/testar-imagem.sh` |
| A3 | a imagem final | `id -u` | diferente de 0 (usuário não-root) | `scripts/testar-imagem.sh` |
| A4 | a imagem final | listar `ENV` e `/app` | nenhuma variável de segredo e só `app.jar` (sem `.env`) | `scripts/testar-imagem.sh` |
| A5 | PostgreSQL 18 local e variáveis falsas, `PORT=10000`, `--memory=512m` | subir a imagem | `/actuator/health` responde `UP` na porta de `PORT` (tempo medido) | `scripts/testar-imagem.sh` |
| A6 | API no ar | `GET /actuator/health` sem token | `200` com `status: UP` e sem `components` nem detalhes | `scripts/testar-imagem.sh` |
| A7 | banco vazio | a API sobe | o Flyway aplicou todas as migrações `V*.sql` (6) e nenhuma do seed dev | `scripts/testar-imagem.sh` |
| A8 | a mesma subida | consultar o banco | `btree_gist` e a constraint `reserva_sem_sobreposicao` existem | `scripts/testar-imagem.sh` |
| A9 | a mesma subida | ler o log e o catálogo | perfil ativo `prod` e **nenhum** serviço de seed no banco | `scripts/testar-imagem.sh` |
| A10 | a mesma subida | ler o log | a retenção de dados rodou na subida (`Retenção de dados: limpeza=`) | `scripts/testar-imagem.sh` |
| A11 | `CORS_ORIGENS` = origem do front falso | `OPTIONS /servicos` com `Origin` do front e com outra origem | a do front recebe `Access-Control-Allow-Origin` igual; a outra recebe `403` | `scripts/testar-imagem.sh` |
| A12 | a imagem no ar | 31 consultas a `GET /alcance` com `X-Forwarded-For: 198.51.100.77`, depois uma com `.88` | a 31ª do `.77` é `429` e a do `.88` é `200` (o limite conta por IP do cliente) | `scripts/testar-imagem.sh` |
| A13 | a imagem no ar | `docker stop` (SIGTERM) e `docker start` | sai com 143 (desligamento limpo) e volta a `UP` do zero (cold start medido) | `scripts/testar-imagem.sh` |
| A14 | container com `--memory=512m` | depois da subida | segue vivo e `UP`; a memória em uso é impressa (~280 MiB no ensaio) | `scripts/testar-imagem.sh` |
| A15 | a imagem sem `DB_URL` | subir | o processo sai com código diferente de 0 e o log mostra a URL do banco inválida | `scripts/testar-imagem.sh` |
| A16 | `@ActiveProfiles("prod")` com o `application-prod.yml` de verdade e variáveis falsas | 30 consultas de `198.51.100.77` atrás do proxy `10.0.0.1`, uma 31ª, e uma de `198.51.100.88` | `forward-headers-strategy` é `framework`; a 31ª é `429`, a do outro cliente é `200`; o perfil não carrega seed nem CORS de localhost | `IpRealNoPerfilProdIT` |

### 8A · Arquivos de implantação

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A17 | `render.yaml` na raiz | ler | um único serviço web `docker`, plano `free`, `healthCheckPath: /actuator/health`, `branch: main`, contexto `./backend`; **nenhum** banco do Render | `ArquivosDeImplantacaoTest` |
| A18 | o mesmo arquivo | ler as variáveis | as de banco, admin, contato e front são `sync: false` sem `value`; JWT, HMAC e token do webhook são `generateValue: true`; nenhuma URL de banco com senha | `ArquivosDeImplantacaoTest` |
| A19 | `application.yml` e `application-prod.yml` | listar as variáveis lidas sem padrão | cada uma está declarada no `render.yaml` (renomear uma variável sem atualizar o Blueprint quebra o teste) | `ArquivosDeImplantacaoTest` |
| A20 | PostgreSQL 18 com o schema das migrações e um dado falso | rodar os comandos do workflow: `pg_dump -Fc`, `gpg` AES-256, e depois descriptografar e `pg_restore` num banco novo | o dump gera, o `.gpg` não mostra o cabeçalho do dump, a senha errada não abre, e o banco restaurado tem o dado, `btree_gist` e a constraint | `scripts/testar-backup.sh` |
| A21 | `.github/workflows/backup-banco.yml` | ler | gatilhos só `schedule` e `workflow_dispatch` (**sem** `pull_request` nem `push`); `permissions: contents: read`; usa `secrets.DB_URL_BACKUP` e `secrets.BACKUP_SENHA`; `pg_dump -Fc`; `gpg --symmetric` AES256; artefato só do `.gpg`, `retention-days: 30` | `ArquivosDeImplantacaoTest` |
| A22 | `docs/planejamento/ativar-deploy.md` | ler | cita todas as variáveis do `render.yaml`, os dois segredos do GitHub, `btree_gist` e o health; sem nenhum valor secreto | `ArquivosDeImplantacaoTest` |

### 8B · O front no Vercel (**depois da 7A**; ainda sem teste)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A23 | front publicado no Vercel (depois da 7A) | colocar a URL em `CORS_ORIGENS` e reiniciar | o navegador do front chama a API sem erro de CORS e uma origem alheia continua barrada | depois da 7A |
| A24 | o front e a API no ar (depois da 7A) | login pelo front | o cookie de refresh funciona entre os domínios (`SameSite` e BFF conforme o ADR 0006) | depois da 7A |
| A25 | a API hibernada (depois da 7A) | abrir o front | a 1ª tela mostra "acordando a agenda" em vez de erro, e a requisição é repetida | depois da 7A |
| A26 | o front no Vercel (depois da 7A) | `URL_CHECKOUT_SIMULADO` aponta para a tela de pagamento simulado | o fluxo de reserva e pagamento simulado fecha de ponta a ponta | depois da 7A |

## 5. Entregas

| Arquivo | O que é |
|---|---|
| `backend/Dockerfile`, `backend/.dockerignore` | imagem multi-stage (E1, E2) |
| `render.yaml` | Blueprint do Render (E3, E4) |
| `.github/workflows/backup-banco.yml` | backup semanal criptografado (E9) |
| `scripts/testar-imagem.sh`, `scripts/testar-backup.sh` | provas locais (A1 a A15, A20) |
| `IpRealNoPerfilProdIT`, `ArquivosDeImplantacaoTest` | provas no `./mvnw verify` (A16 a A19, A21, A22) |
| `docs/planejamento/ativar-deploy.md` | guia de ativação para o Kevin |
| `decisoes.md` (D115 a D120), README, `docs/guia-testes.md` | documentação |

## 6. Riscos e cuidados

- **IP forjado:** com `framework`, a API confia no `X-Forwarded-For` que chegar. Atrás do Render o cabeçalho passa pelo proxy deles, mas um cliente pode mandar um valor próprio na frente da lista e fugir do limite por IP. Mitigação no beta: o limite por conta (login) continua valendo; D-2 propõe conferir no ar e, se for o caso, usar o último IP da cadeia.
- **Cold start e 1º acesso:** 20 a 60 s no Render free. O front (7A) precisa de uma tela de espera (A25).
- **Aiven free pode ser desligado sem uso:** o backup semanal e o plano B existem para isso (D66).
- **Backup tem dado pessoal por 30 dias**, inclusive de contas já excluídas (ADR 0009): a política precisa dizer; a `BACKUP_SENHA` perdida = backup perdido.
- **GitHub desliga workflow agendado após 60 dias sem atividade** no repositório: avisado no guia.
- **Primeiro admin:** `ADMIN_SENHA` fica só no painel do Render; a troca no 1º login é obrigatória.
- **Limites do plano mudam:** conferir as páginas oficiais no dia da ativação.
- **Pagamento simulado em produção do beta** (D59): nenhuma chave de gateway é configurada.

## 7. Dúvidas para o Kevin (com a proposta de cada uma)

| # | Dúvida | Proposta |
|---|---|---|
| D-1 | Manter a API "acordada" com um ping externo? | **não** no beta: o cold start é aceito (custo zero, sem serviço extra); a tela de espera da 7A cobre |
| D-2 | O `X-Forwarded-For` pode ser forjado para escapar dos limites por IP? | **aceitar no beta** e conferir no ar (guia-testes); se o Render anexar o IP real ao fim da lista, trocar para o último IP confiável numa fatia pequena |
| D-3 | `autoDeploy` do Render | **desligado** no beta (Manual Deploy); ligar só depois de o fluxo estável |
| D-4 | Região do Render | `frankfurt` no `render.yaml`, **ajustar** para a mais próxima da região do Aiven que o Kevin escolher |
| D-5 | Backup: conta pessoal do GitHub ou da organização? | no repositório do projeto, com os dois segredos cadastrados pelo Kevin; artefato de 30 dias |
| D-6 | Pedido do Render por cartão | se aparecer, **parar** e falar com o Kevin: o plano free não deve pedir |

## 8. Pronto quando

- [x] `docker build` termina e `scripts/testar-imagem.sh` passa A1 a A15 (saída real no [guia de testes](../guia-testes.md))
- [x] `scripts/testar-backup.sh` passa A20
- [x] `IpRealNoPerfilProdIT` e `ArquivosDeImplantacaoTest` verdes no `./mvnw verify`
- [x] nenhum segredo no repositório; nenhuma conta, serviço ou banco criado
- [ ] **Ativação pelo Kevin** (fora desta PR): guia de ativação, health `UP` no ar, primeiro backup manual rodado
- [ ] 8B (A23 a A26) depois da Fatia 7A
- [ ] [checklist da PR](checklist-pr.md) completo
