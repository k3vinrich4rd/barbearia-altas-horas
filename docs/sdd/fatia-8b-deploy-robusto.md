# 📐 Spec · Fatia 8B · Deploy robusto e econômico

| | |
|---|---|
| **Status** | 📝 aguarda aprovação do Kevin · nenhuma linha de código, `render.yaml`, Dockerfile ou workflow foi alterada por esta spec |
| **Fatias do plano** | sucessora da [Fatia 8](fatia-8-deploy.md) (deploy grátis, já no ar em beta). Ela fecha o que a 8 deixou aberto: deploy só com CI verde, subida mais rápida, health que não derruba a API à toa, ping que cabe nas 750 h, rollback, alerta e backup de verdade |
| **Branch** | `feat/fatia-8b-deploy-robusto` |
| **Regras** | R30 (nada pessoal em log), H14/R26 (limite por IP), API8 do dossiê (health sem detalhes), D12 (pool pequeno para o Aiven), D38 e D66 (custo zero, banco free) |
| **ADRs** | [0003](../adr/0003-anti-double-booking-no-banco.md) (`btree_gist`: Flyway só para frente), [0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) (BFF e `API_URL`), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (dump tem dado pessoal) |
| **Custo** | **R$ 0**: Render Free, Aiven PostgreSQL Free, Vercel Hobby e GitHub Actions. Nada aqui exige cartão (D-7 pede a decisão sobre o Hobby) |
| **Não cobre** | domínio próprio · monitoramento pago (UptimeRobot, Datadog) · produção paga e mudança de plano (Fase 2) · pagamento real (Asaas) · migrar para outro provedor (plano B Neon continua em [banco-na-nuvem.md](../planejamento/banco-na-nuvem.md)) · mudar regra de negócio ou tela |

## 1. Objetivo

Depois desta fatia, publicar uma versão **não derruba o beta**: só vai ao ar o que passou no CI, a API acorda mais depressa, o Render não a reinicia quando o banco oscila, o ping cabe nas 750 h do Render junto com a API do RKF Bank, uma falha chega por e-mail, existe um passo a passo de rollback ensaiado e o backup semanal **realmente roda** (hoje falha por falta dos segredos).

### Estado medido no ar (23/11/2026) e o que isto muda

| Fato | Valor | Consequência |
|---|---|---|
| Primeira subida no Render | ~180 s até o `Started`, ~3 min até o health | no free a CPU é uma fração de núcleo (**conferir na implementação**: ~0,1): a subida é limitada por CPU, então classe carregada a menos vale muito (item b) |
| Migrações | 7 em 3,4 s | Flyway **não** é o gargalo; não mexer |
| `autoDeploy` | desligado (Manual Deploy) | ninguém publica por engano, mas também ninguém garante CI verde (item a) |
| Horas do Render | 750 h/mês **divididas** com a API do RKF Bank | cada hora de ping custa horas do outro projeto (item d) |
| Ping (repositório do perfil) | a cada 10 min na janela da barbearia, a cada 3 h fora | ~335 h/mês (conta no item d) |
| Front Vercel | usa `API_URL` (só no servidor, `frontend/src/bff/config.ts`) | trocar a URL da API é só variável, sem build novo de código |
| Link de cartão simulado | rota `/pagamento-simulado/{cobrancaId}` existe (A63 da Fatia 7, D149) | entra no roteiro de verificação pós-deploy |
| Backup | `backup-banco.yml` rodou em 22/11 e **falhou em 7 s**: `gh secret list` vazio, faltam `DB_URL_BACKUP` e `BACKUP_SENHA` | hoje **não existe backup** (item f) |

## 2. Arquitetura (o que muda)

```
git push main ──> GitHub Actions (Backend CI + Frontend CI) ──verde──┬─> Render (autoDeployTrigger: checksPass) ──> Aiven PG
                                                                      └─> Vercel (produção só da main)
GitHub Actions (repositório do perfil): ping 06:40–19:50 BRT, ter–sáb ──> /actuator/health (acorda API e toca o banco)
GitHub Actions (este repositório):      manter-banco  2×/dia ──> psql "select 1" direto no Aiven (sem acordar a API)
                                        backup-banco  domingo ──> pg_dump + gpg ──> artefato 30 dias
Render health check: /actuator/health/liveness (não depende do banco)   ·   ping e smoke: /actuator/health (completo)
```

## 3. Contrato

Nenhum endpoint novo e **nenhuma mudança no `openapi.yaml`**. `/actuator/health/liveness` e `/readiness` são rotas de operação (como `/actuator/health`), públicas, sem detalhes, fora do contrato. `SegurancaConfig` já libera `/actuator/health/**`. **Conferir na implementação** que o `redocly lint` segue com 0 erros.

## 4. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Quando o Render publica | `autoDeployTrigger: checksPass` no `render.yaml` (substitui `autoDeploy: false`), `branch: main` | o Render só publica um commit da `main` depois que os checks do GitHub passam. **Conferir na implementação** o nome exato da chave na referência do Blueprint e o que ocorre se o commit não tiver nenhum check (deve ser: publica na hora; por isso o merge na `main` só existe pelo PR de release, que exige CI verde) |
| E2 | Quando o Vercel publica | produção só da `main`; `develop` e PRs viram **Preview** (já é o padrão do Vercel). O gate de CI é o PR de release `develop → main` (só abre/mergeia com CI verde, regra do repositório) | Hobby não tem "Deployment Checks" bloqueante (**conferir**). Alternativa mais rígida, se o Kevin quiser: desligar o deploy por Git em produção (`git.deploymentEnabled` no `vercel.json`) e publicar por workflow com `VERCEL_TOKEN` depois do CI (D-1) |
| E3 | Como encurtar a subida | **Class Data Sharing + cache AOT do JDK 25** (JEP 483 e 514: `-XX:AOTCacheOutput` / `-XX:AOTCache`), gerado **dentro da imagem**, na etapa final, com o mesmo JRE que vai rodar. O jar é extraído com `java -Djarmode=tools -jar app.jar extract` (Spring Boot 4.1; **conferir o nome exato do modo e o layout gerado**) e executado a partir do diretório extraído, porque o cache só vale com o mesmo classpath | pula o carregamento e a verificação de milhares de classes do Spring/Hibernate, justamente o que a CPU do free faz devagar. É um ganho de execução, não de código: se o cache não casar, a JVM ignora e sobe normal (A8) |
| E4 | Rodada de treino no `docker build` | sobe a aplicação com `-Dspring.context.exit=onRefresh` (o contexto fecha sozinho depois de montado) e um perfil `treino` **sem banco**: Flyway desligado, Hikari sem falhar na partida (`initialization-fail-timeout=-1`), `ddl-auto: none`. **Conferir na implementação** se o Hibernate 7 monta o `EntityManagerFactory` sem conexão (pode exigir dialeto explícito e `hibernate.boot.allow_jdbc_metadata_access=false`); se não montar, o treino cobre só o contexto web e o cache fica menor, mas continua válido | o build do Render não tem PostgreSQL; subir banco no `docker build` é inviável. O perfil `treino` **nunca** é ativado em runtime (A7 prova) |
| E5 | `-XX:TieredStopAtLevel=1` | **medir antes de adotar** (A10). Hipótese: com 1 CPU lenta o C2 compete com a aplicação e só atrasa; o preço é pico de desempenho menor, irrelevante para ~dezenas de requisições por hora. Entra só se reduzir o tempo medido e não piorar a memória | decisão por número, não por palpite |
| E6 | Memória em 512 MB | manter `MaxRAMPercentage=60`, `UseSerialGC`, `Xss512k` e `ExitOnOutOfMemoryError`. Acrescentar `-XX:ReservedCodeCacheSize=64m` e `-XX:MaxMetaspaceSize=128m` (já existe) só se a medição (A9) mostrar folga; `-Xshare:auto` explícito. Pool Hikari **3** (era 5) em `prod` | 60% de 512 MB = ~307 MB de heap; a medição da Fatia 8 deu ~280 MiB no contêiner. A API atende um barbeiro: 3 conexões bastam e sobra espaço no limite de 20 do Aiven (Flyway e ShedLock também usam) |
| E7 | Health do Render | `management.endpoint.health.probes.enabled=true` e o `healthCheckPath` do Render vira **`/actuator/health/liveness`** (só "o processo está vivo", não consulta o banco). `/actuator/health` (completo, com banco) continua para o ping, o smoke e o `HEALTHCHECK` do Docker passa a usar o liveness | se o Aiven oscilar, o Render **não** mata nem reinicia uma API saudável (reiniciar não conserta o banco e custa outra subida de 3 min). A readiness (`/actuator/health/readiness`) fica exposta para o ping/smoke saberem "pronta para atender" |
| E8 | Durante as migrações | nada a fazer: o Tomcat só abre a porta **depois** do contexto (Flyway roda antes), então durante as migrações o Render vê a porta fechada, não um health vermelho. As 7 migrações levam 3,4 s. **Conferir** quanto tempo o Render espera a porta abrir antes de dar o deploy por falho (a subida de 180 s passou, então o limite é maior que isso) | evita inventar "readiness durante Flyway" que o Spring já resolve pela ordem de partida |
| E9 | Banco que oscila | `application-prod.yml`: `connection-timeout: 10000`, `validation-timeout: 3000`, `max-lifetime: 600000` (menor que o corte de ociosidade do Aiven, **conferir**), `keepalive-time: 120000`, `minimum-idle: 1`; e, via `data-source-properties`, `connectTimeout=10` e `socketTimeout=30` (segundos) do driver. Assim a URL secreta em `DB_URL` **não precisa mudar** | sem timeout o pedido pendura até o Render derrubar; com timeout falha rápido em `503` e a API se recupera sozinha quando o banco volta |
| E10 | Erro com banco fora | `CannotGetJdbcConnection`/timeout vira `503` em `problem+json` genérico (R30: sem URL, sem usuário), com `Retry-After`. **Conferir** se o `TratadorGlobalDeErros` já cobre; se não, é um ajuste mínimo + teste | o front (A25 da Fatia 8) mostra "acordando a agenda" em vez de erro técnico |
| E11 | Desligamento limpo | `server.shutdown: graceful` com `spring.lifecycle.timeout-per-shutdown-phase: 20s` | um deploy no meio de uma reserva não corta a requisição; o SIGTERM do Render já chega ao `java` (exec, A13 da Fatia 8) |
| E12 | Ping eficiente | (1) o ping do perfil passa a rodar **só** na janela da barbearia, começando às **06:40** BRT (09:40 UTC), uma volta antes da abertura, para a subida de ~3 min não pegar o primeiro cliente; (2) **fora da janela não acorda a API**; (3) o Aiven é mantido ligado por um workflow **neste repositório** que abre uma conexão direta (`psql -c 'select 1'`) 2×/dia; (4) o laço de espera usa `curl -m 15` a cada 20 s por até 8 min em vez de `-m 90` × 8 (que passa dos 15 min do `timeout-minutes`) | acordar a API 8×/dia só para tocar o banco custa ~0,3 h por vez (15 min de ociosidade antes de dormir), ~47 h/mês; a conexão direta custa 0 h de Render. O workflow do ping fica no repositório **público** do perfil (minutos ilimitados); o keep-alive do banco é curto (~20 s por rodada) |
| E13 | Cuidado com o cron do GitHub | agendamentos podem atrasar vários minutos (e são descartados em pico). Intervalo de 10 min contra a hibernação de 15 min deixa ~5 min de folga: **manter 10 min**, não encurtar (cada execução custa uma VM) | encurtar para 5 min dobra execuções sem resolver o atraso do GitHub; o pior caso é uma subida fria, que o ping seguinte já cobre |
| E14 | Rollback | três degraus, do mais barato ao mais caro (seção 6 do guia): (1) **Rollback** do deploy anterior bom no Render, se o plano Free oferecer (**conferir**); (2) **Manual Deploy** de um commit anterior da `main`; (3) `git revert` do merge de release na `develop` → PR → `main` (nunca push direto). **Schema só para frente**: toda migração precisa ser compatível com a versão **anterior** do código (expandir → migrar → contrair em releases separadas), senão o rollback de código quebra | Flyway não desfaz migração (E10 da Fatia 8). Sem a regra "compatível com N-1", o degrau 1 e o 2 podem deixar a API nova sem schema ou a antiga com schema novo |
| E15 | Alerta | só o que já é grátis e já chega por e-mail: (a) GitHub "Actions → Send notifications for failed workflows only"; (b) Render "Deploy failed" e "Service failed" em Notifications; (c) Aiven avisa antes de desligar o banco. Sem serviço novo | o ping falho vira e-mail: é o alarme de "API fora do ar" mais barato que existe |
| E16 | Verificação pós-deploy | `scripts/verificar-no-ar.sh` (só `curl`, sem credencial): health completo da API, home do Vercel `200`, preflight de CORS da origem do front (permitida) e de uma alheia (barrada), `GET /pagamento-simulado/qualquer-id` do front **não** é `404` (A63), e a API responde pelo mesmo `API_URL` que o front usa | é o "acabou o deploy, está tudo no ar?" repetível, usado também depois de um rollback |
| E17 | Backup | corrigir o que falta (segredos, 1ª rodada, ensaio com o arquivo real); **não** mexer no formato nem no agendamento. Cuidado novo: o repositório é **público** e artefato de repositório público é baixável por qualquer conta logada no GitHub, então o dump só pode sair **criptografado** (já é: gpg AES-256, senha ≥ 24 caracteres exigida pelo próprio workflow) | `BACKUP_SENHA` fraca ou vazada = dump de clientes exposto (LGPD, ADR 0009) |
| E18 | O que não fazer | `spring.main.lazy-initialization=true` (esconde erro de configuração até o 1º uso em produção); jar com camadas/Layertools (o ganho é de cache de imagem, não de subida); trocar de GC; subir banco no `docker build` | custo/risco maior que o ganho |

## 5. Critérios de aceite

As provas locais usam `scripts/testar-imagem.sh` (estendido) e `scripts/verificar-no-ar.sh`; as de arquivo usam o `ArquivosDeImplantacaoTest`; o que depende de conta do Kevin é **roteiro manual** com o comando que prova. Numeração própria desta fatia (A1 a A33).

### (a) Deploy só com CI verde

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | `render.yaml` | ler | `autoDeployTrigger: checksPass`, **sem** `autoDeploy: true` e sem `autoDeployTrigger: commit`; `branch: main` | `ArquivosDeImplantacaoTest` |
| A2 | `backend-ci.yml` e `frontend-ci.yml` | ler | ambos rodam em `push` na `main` (o que o `checksPass` espera) e **não** têm filtro `paths` que pule o commit de release | `ArquivosDeImplantacaoTest` |
| A3 | commit da `main` com um check vermelho (simulado num PR de teste ou lido do histórico do Render) | o Render recebe o push | **não** publica; o evento "checks failed" aparece em Events | roteiro manual: Render → Events (registrado no [guia de testes](../guia-testes.md)) |
| A4 | projeto Vercel | conferir em Settings → Git | **Production Branch = `main`**; `develop` e PRs só geram Preview | roteiro manual (print/descrição no guia); `ativar-deploy.md` lista o passo |

### (b) Subida mais rápida

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A5 | `backend/Dockerfile` | `docker build -t altas-horas-api:fatia8b backend` | termina com o jar extraído (`-Djarmode=tools`), o perfil `treino` e o arquivo de cache AOT dentro da imagem; sem banco e sem segredo no build | `scripts/testar-imagem.sh` (A1 da Fatia 8 segue valendo) |
| A6 | a imagem | listar o cache e rodar `java -XX:AOTCache=... -Xshare:on -version` na etapa de verificação (ou subir e ler o log de `-Xlog:aot`/`-Xlog:cds`) | o cache existe, é aceito pela JVM e **é usado** na subida (aparece no log) | `scripts/testar-imagem.sh` |
| A7 | a imagem em execução | ler `env` do processo e o perfil ativo | perfil `prod`, **nunca** `treino`; nenhum segredo novo na imagem; `docker image inspect` sem `ENV` de segredo (A4 da Fatia 8 segue) | `scripts/testar-imagem.sh` |
| A8 | o arquivo de cache corrompido ou de outra versão de JVM (teste negativo: truncar o arquivo) | subir | a API sobe do mesmo jeito e chega a `UP` (a JVM ignora o cache inválido) | `scripts/testar-imagem.sh` |
| A9 | contêiner com `--memory=512m` | 20 requisições a `/servicos` e `/alcance` depois do `UP` | continua vivo e `UP`; a memória do contêiner impressa é **≤ 400 MiB** (a Fatia 8 mediu ~280 MiB) | `scripts/testar-imagem.sh` |
| A10 | a mesma máquina, a imagem **com** cache e a **sem** cache (`SEM_CACHE=1`), três subidas de cada, na mediana | `esperar_saude` | a mediana **com cache é ≥ 25% menor** que a sem cache; o resultado, `TieredStopAtLevel=1` ligado e desligado, vira tabela no guia. Se `TieredStopAtLevel=1` não ajudar, **não entra** na imagem | `scripts/testar-imagem.sh` |
| A11 | o mesmo script com `CPU_LIMITADA=1` (`docker run --cpus=0.25`) | subida fria | imprime o tempo; serve para **estimar** o comportamento no Render (CPU da ordem de 0,1), sem entrar no pass/fail | `scripts/testar-imagem.sh` (informativo) |
| A12 | a imagem nova | `docker image ls` | cresce **no máximo 120 MB** em relação à `fatia8` (o cache AOT é de dezenas de MB) | `scripts/testar-imagem.sh` |

### (c) Health que não falha à toa

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A13 | API no perfil `prod` | `GET /actuator/health/liveness` e `/readiness` sem token | `200`, `{"status":"UP"}`, **sem** `components` nem detalhes | `scripts/testar-imagem.sh` + `HealthProbesIT` |
| A14 | PostgreSQL parado (`docker stop`) com a API já no ar | `GET /actuator/health/liveness` e `GET /actuator/health` | liveness segue `200`; o health completo responde `503` com `DOWN`, **sem** URL nem usuário do banco no corpo | `scripts/testar-imagem.sh` |
| A15 | o mesmo cenário, PostgreSQL parado por 25 s e religado | observar a API | o **mesmo processo** segue (uptime não zera, nenhum reinício) e o health completo volta a `UP` em até 60 s; durante a queda, `GET /servicos` responde `503` `problem+json` em até 12 s (nunca fica pendurado) e sem dado do banco no corpo | `scripts/testar-imagem.sh` |
| A16 | `render.yaml` e `Dockerfile` | ler | `healthCheckPath: /actuator/health/liveness`; `HEALTHCHECK` do Docker idem; o `/actuator/health` completo **não** é o health do Render | `ArquivosDeImplantacaoTest` |
| A17 | `application-prod.yml` carregado com variáveis falsas | ler as propriedades | `maximum-pool-size` 3, `minimum-idle` 1, `connection-timeout` 10000, `validation-timeout` 3000, `max-lifetime` 600000, `keepalive-time` 120000; `connectTimeout` e `socketTimeout` do driver definidos; `server.shutdown: graceful` | `PoolProdIT` |
| A18 | a imagem no ar | `docker stop` (SIGTERM) durante uma requisição lenta simulada | a requisição em curso termina e o processo sai com 143 em até 25 s | `scripts/testar-imagem.sh` (estende A13 da Fatia 8) |

### (d) Ping eficiente

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A19 | `scripts/horas-render.sh` (novo): lê a janela e o intervalo do ping e calcula as horas de API acordada no mês (31 dias, 21,7 dias úteis da barbearia, 15 min de cauda por rodada) | rodar com a configuração proposta | imprime **≤ 300 h/mês** e a conta passo a passo; o número bate com a tabela da seção 8 | `scripts/horas-render.sh --conferir` |
| A20 | workflow do ping (repositório do perfil, **fora deste repositório**: o Kevin aplica o diff que esta fatia entrega) | ler o `cron` | só `ter–sáb`, de `09:40` a `22:50` UTC; **nenhum** cron fora da janela chama a API | roteiro manual: `gh workflow view` no perfil + `scripts/horas-render.sh --conferir` |
| A21 | o mesmo workflow, API hibernada | executar `workflow_dispatch` | acorda a API e termina verde em **até 8 min**; falha vermelha (e-mail) se não vier `UP`; cada tentativa dura no máximo 15 s de espera | roteiro manual: `gh run view` (tempo e conclusão no guia) |
| A22 | `.github/workflows/manter-banco.yml` (novo, neste repositório) | ler | gatilhos só `schedule` e `workflow_dispatch`; `permissions: contents: read`; `if: github.repository == 'k3vinrich4rd/barbearia-altas-horas'`; usa `secrets.DB_URL_BACKUP`; roda `select 1` com `postgres:18-alpine`; **nunca** imprime a URL; `timeout-minutes: 5` | `ArquivosDeImplantacaoTest` |
| A23 | segredo cadastrado e `workflow_dispatch` | executar | termina verde em menos de 2 min e o Aiven mostra a conexão no painel de uso (**conferir**) | roteiro manual: `gh run view` |

### (e) Rollback e alerta

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A24 | `ativar-deploy.md` | ler a seção de rollback | traz os 3 degraus (Rollback no Render, Manual Deploy de commit anterior, `git revert` + PR), a regra "migração compatível com a versão anterior" e o comando `scripts/verificar-no-ar.sh` para confirmar | `ArquivosDeImplantacaoTest` |
| A25 | `docs/sdd/checklist-pr.md` | ler | tem o item "migração nova é compatível com o código anterior (expandir → migrar → contrair)" | `ArquivosDeImplantacaoTest` |
| A26 | `scripts/verificar-no-ar.sh` contra a imagem local (API + front falsos) e contra o ar | rodar | sai 0 só se: health completo `UP`; home do front `200`; CORS da origem do front permitido e de origem alheia `403`; `/pagamento-simulado/x` do front não é `404`; falha com a mensagem de qual checagem caiu | `scripts/verificar-no-ar.sh --local` (contra a imagem) e roteiro manual no ar |
| A27 | o Kevin, uma vez | ensaiar: Manual Deploy do commit anterior da `main`, depois voltar ao atual | `verificar-no-ar.sh` verde nos dois estados; tempo gasto e o que o plano Free oferece de fato (Rollback sim/não) registrados no guia | roteiro manual (registrado no guia de testes) |
| A28 | notificações ligadas (GitHub Actions "failed only", Render "Deploy failed" e "Service failed") | derrubar de propósito: apontar o ping do `workflow_dispatch` para uma URL inválida, uma vez | chega e-mail do GitHub em até 10 min | roteiro manual (print ou descrição no guia) |

### (f) Backup

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A29 | segredos `DB_URL_BACKUP` e `BACKUP_SENHA` cadastrados pelo Kevin | `gh secret list` | os dois nomes aparecem (valores nunca) | roteiro manual |
| A30 | os segredos | `gh workflow run "Backup do banco"` e esperar | termina verde e o artefato `altashoras-AAAA-MM-DD.dump.gpg` existe, com tamanho > 0 | roteiro manual: `gh run list` e `gh run download` |
| A31 | o artefato real baixado | `ARQUIVO=<arquivo> scripts/testar-backup.sh --real` (novo modo): descriptografa com a senha, `pg_restore` num PostgreSQL 18 local vazio | o banco restaurado tem `btree_gist`, a constraint `reserva_sem_sobreposicao` e as 7 migrações no `flyway_schema_history`; a senha errada **não** abre | `scripts/testar-backup.sh --real` (rodado pelo Kevin, ele é o dono da senha) |
| A32 | `backup-banco.yml` | ler | continua como na Fatia 8 (A21 de lá) e a senha curta (< 24) é recusada; **inalterado** nesta fatia | `ArquivosDeImplantacaoTest` |

### (g) Custo

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A33 | `render.yaml`, `ativar-deploy.md` e workflows | ler | todo `plan:` é `free`; nenhum serviço pago (nada de `starter`); o guia traz a tabela de limites e o que acontece ao estourar (seção 8), com a data da conferência | `ArquivosDeImplantacaoTest` |

## 6. Entregas (quando aprovada)

| Arquivo | O que muda |
|---|---|
| `render.yaml` | `autoDeployTrigger: checksPass`, `healthCheckPath` liveness (E1, E7) |
| `backend/Dockerfile` | extração do jar, treino sem banco, cache AOT, `HEALTHCHECK` no liveness, flags medidas (E3 a E6) |
| `backend/src/main/resources/application-treino.yml` (novo) | perfil só de build, nunca ativo em runtime (E4) |
| `application.yml` / `application-prod.yml` | probes, Hikari, timeouts do driver, `shutdown: graceful` (E7, E9, E11) |
| `.github/workflows/manter-banco.yml` (novo) | keep-alive direto do banco (E12) |
| diff do workflow do ping, **para o Kevin aplicar** no repositório do perfil | janela de 06:40, sem chamada fora dela, laço de espera de 8 min (E12) |
| `scripts/testar-imagem.sh`, `scripts/verificar-no-ar.sh`, `scripts/horas-render.sh`, `scripts/testar-backup.sh --real` | provas A5 a A19, A26, A31 |
| `HealthProbesIT`, `PoolProdIT`, ajuste no `ArquivosDeImplantacaoTest` | provas A1, A2, A13, A16, A17, A22, A24, A25, A32, A33 |
| `docs/planejamento/ativar-deploy.md`, `docs/sdd/checklist-pr.md`, `docs/guia-testes.md`, `decisoes.md` (D152 em diante), `docs/sdd/README.md` | rollback, alertas, limites, tabela de medição (com saída real) |

## 7. Riscos e cuidados

- **O cache AOT depende da versão exata da JVM e do classpath.** Imagem base `25-jre-alpine` que mude de patch sem reconstruir o cache é tratada pela JVM como cache inválido (A8: sobe normal, só perde o ganho). O cache é gerado **no mesmo `docker build`** da imagem final, então a imagem sempre carrega um cache que casa com o seu JRE.
- **Treino sem banco pode ficar pobre** (E4). Pior caso: o cache cobre o web e o Spring Core, mas não o Hibernate; o ganho cai. Por isso a meta de A10 é **25%** e não o máximo teórico, e há a medição antes de adotar qualquer flag.
- **`checksPass` pode bloquear um deploy de emergência** se o CI estiver fora do ar. Saída: o Manual Deploy continua existindo no painel.
- **Liveness não enxerga banco fora do ar.** É a intenção; quem avisa é o ping (health completo vermelho → e-mail). O custo: o cliente que chegar com o banco fora recebe `503` amigável em vez de a API reiniciar sem resolver.
- **Hibernação não some.** Fora da janela (domingo, segunda, madrugada) o primeiro acesso ainda espera a subida (~2 a 3 min). Aceito: é o preço de caber nas 750 h (D-2 ensina a trocar).
- **Cron do GitHub atrasa ou pula** em horário de pico; o ping seguinte compensa. Se o repositório do perfil ficar 60 dias sem atividade, o GitHub **desativa** os workflows agendados (o diário e a ofensiva do perfil já geram commits, o que mantém ativo, **conferir**).
- **Artefato do backup em repositório público** é baixável por qualquer conta do GitHub; só a criptografia protege (E17). Trocar a senha = os dumps antigos continuam abrindo com a antiga: anotar por data.
- **Keep-alive do banco usa a credencial do backup.** A mesma `DB_URL_BACKUP` serve às duas coisas; se o Kevin criar um usuário só de leitura no Aiven, o ideal é um segundo segredo (D-6).
- **Vercel Hobby é para uso pessoal e não comercial.** A Barbearia é a agenda de um negócio real: pode ferir os termos (D-7).
- **Limites mudam**: conferir as páginas oficiais no dia da implementação e datar a tabela.

## 8. Custo, limites e conta de horas

### Limites de cada plano (valores de referência: **conferir nas páginas oficiais no dia da implementação**)

| Plano | Limite que importa | O que acontece se estourar |
|---|---|---|
| **Render Free** (web service) | 750 h/mês por workspace, divididas com a API do RKF Bank; 512 MB de RAM; CPU compartilhada pequena; dorme após ~15 min sem tráfego; minutos de build do plano | os serviços free ficam **suspensos até o mês virar** (ninguém é cobrado sem cartão); a Barbearia sai do ar junto com o RKF Bank |
| **Aiven PostgreSQL Free** | 1 GB de disco, 1 CPU, 1 GB de RAM, `max_connections` 20, 1 serviço free por conta | disco cheio = escritas falham (a barbearia gera poucas linhas); ocioso por muito tempo = o serviço é **desligado** (religa no painel, **conferir** por quanto tempo os dados ficam guardados) |
| **Vercel Hobby** | banda, invocações de função e builds mensais do plano; uso pessoal, não comercial | a plataforma pode pausar o projeto até o ciclo virar; o risco de termos é a D-7 |
| **GitHub Actions** | repositório **público**: minutos ilimitados; privado: 2.000 min/mês; 500 MB de armazenamento de artefatos no plano grátis | no público não há estouro de minutos; artefato velho expira em 30 dias; no privado o job para até o mês virar (sem cobrança sem cartão) |

### Conta de horas do Render por mês (Barbearia sozinha; 31 dias = 744 h; 21,7 dias úteis de ter–sáb)

| Cenário | Conta | API acordada | Sobra para o RKF Bank (750 h) |
|---|---|---|---|
| **Hoje** (ping 10 min, 07:00 a 19:50 BRT, e a cada 3 h o dia todo) | janela: 13,2 h (+0,25 h de cauda) × 21,7 d = 291 h; pings de 3 h fora da janela: ~36/semana × ~4,35 sem × ~0,3 h = ~47 h | **~335 h** | **~415 h** |
| **Proposto** (ping 10 min de 06:40 a 19:50 BRT, nada fora; banco por conexão direta) | (13,2 h + 0,25 h) × 21,7 d = **~291 h** | **~291 h** | **~459 h** |
| Sem ping nenhum | só quando cliente usa; imprevisível, mas cada acesso sozinho = ~0,3 h | ~10 a 60 h | ~690 a 740 h | 
| API 24 h/7 d (referência do que **não** fazer) | 744 h | 744 h | **~6 h**: o RKF Bank cai |

O RKF Bank precisa ficar abaixo de **459 h/mês** para as duas APIs caberem (≈ 15 h/dia). **Conferir** o ping real dele. Os números acima são contas, não medições do Render; o painel de uso do Render é a prova (A19 compara).

## 9. Dúvidas para o Kevin (com a proposta de cada uma)

| # | Dúvida | Proposta |
|---|---|---|
| D-1 | Gate de CI do Vercel: só pelo PR de release, ou publicar por workflow com `VERCEL_TOKEN` depois do CI? | **só pelo PR de release** (E2): é grátis e já funciona; o workflow com token exige segredo, mais manutenção e dá poder de deploy ao GitHub. Reavaliar se algum dia a `main` receber algo sem CI |
| D-2 | Quanto de ping vale as horas do RKF Bank? | **janela da barbearia + 20 min antes** (~291 h) e nada fora; se o RKF Bank precisar de mais horas, encurtar a janela de sábado ou começar às 07:00 |
| D-3 | Keep-alive do Aiven: conexão direta 2×/dia, ou manter uma chamada diária à API? | **conexão direta** (0 h de Render). Se em 14 dias o Aiven mostrar que ainda desliga, volta 1 chamada/dia à API (~9 h/mês) |
| D-4 | Health do Render no liveness em vez do completo? | **sim** (E7): API saudável não é reiniciada por culpa do banco; o alarme de banco fora é o ping |
| D-5 | `TieredStopAtLevel=1`? | **só se a medição de A10 mostrar ganho** e a memória não piorar; senão fica de fora |
| D-6 | Segundo segredo de leitura (`DB_URL_PING`) para o keep-alive? | **não agora**: reaproveitar `DB_URL_BACKUP` (o repositório e os segredos já são só do Kevin); criar o usuário de leitura quando houver tempo |
| D-7 | Vercel Hobby e uso não comercial: manter no beta? | **manter no beta** e decidir antes do uso real com clientes: ou migrar o front para o plano pago do Vercel, ou outro host estático/SSR grátis que permita uso comercial. Conferir os termos do dia |
| D-8 | Reduzir o pool do Hikari de 5 para 3? | **sim, em `prod`** (E6); o D12 era dimensionamento conservador e o Aiven tem teto de 20 conexões |
| D-9 | Quem aplica o diff do ping no repositório do perfil? | **o Kevin**, por PR/commit dele (a automação do perfil é outro repositório; esta fatia só entrega o diff e a conta de horas) |
| D-10 | Ensaio de rollback (A27) e de e-mail de falha (A28): quando? | **no dia da implementação**, depois do primeiro deploy com `checksPass`; leva ~15 min e fica registrado no guia |

## 10. Pronto quando

- [ ] OK do Kevin nas D-1 a D-10
- [ ] A1 a A12, A13 a A18, A22, A24 a A26, A32 e A33 verdes (saída real no [guia de testes](../guia-testes.md), com a tabela de tempos de subida de A10)
- [ ] A19: `scripts/horas-render.sh --conferir` ≤ 300 h
- [ ] roteiros do Kevin feitos e registrados: A3, A4, A20, A21, A23, A27, A28, A29 a A31
- [ ] backup com os dois segredos cadastrados e o ensaio de restauração com o arquivo real
- [ ] `redocly lint` do contrato com 0 erros (nada mudou nele)
- [ ] [checklist da PR](checklist-pr.md) completo
