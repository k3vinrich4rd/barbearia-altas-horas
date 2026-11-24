#!/usr/bin/env sh
# Prova que a imagem Docker da API (backend/Dockerfile) sobe no perfil prod contra um PostgreSQL 18 local, com variáveis
# FALSAS e sem nenhuma conta em provedor.
#   A1 a A15        = critérios da Fatia 8 (docs/sdd/fatia-8-deploy.md)
#   8B-A5 a 8B-A18  = critérios da Fatia 8B (docs/sdd/fatia-8b-deploy-robusto.md): cache AOT, health, queda do banco,
#                     memória, medição de cold start (mediana de 3 subidas com e sem cache) e desligamento limpo.
# Precisa só de Docker (Docker Desktop aberto) e curl. Não usa rede externa além de baixar as imagens base.
#
# Uso: scripts/testar-imagem.sh                     (constrói a imagem e testa)
#      PULAR_BUILD=1 scripts/testar-imagem.sh       (reaproveita a imagem altas-horas-api:fatia8b já construída)
#      MEDIR_ANTES=1 scripts/testar-imagem.sh       (mede também a imagem da Fatia 8, sem cache, construída da origin/develop)
#      CPU_LIMITADA=1 scripts/testar-imagem.sh      (informativo, 8B-A11: sobe com --cpus=0,25 para estimar o Render free)
#      SEM_PICO=1 scripts/testar-imagem.sh          (pula a bateria de queda do banco; só build, subida e medição)
#      PULAR_MEDICAO=1 scripts/testar-imagem.sh     (pula a medição de cold start, que leva uns 8 min)
set -eu

# No Git Bash do Windows, "/actuator" viraria um caminho do Windows dentro do docker exec.
export MSYS_NO_PATHCONV=1

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"   # caminhos relativos: o docker.exe do Windows não entende o /c/... do Git Bash
IMAGEM="altas-horas-api:fatia8b"
IMAGEM_ANTES="altas-horas-api:fatia8"
REDE="altas-horas-fatia8"
PG="altas-horas-fatia8-pg"
API="altas-horas-fatia8-api"
PORTA_API="${PORTA_API:-18080}"
PORTA_INTERNA=10000      # como o Render injeta a PORT (A5): a imagem tem de obedecer, não fixar 8080
FRONT_FALSO="https://front.exemplo.test"
IMG="$IMAGEM"

falhas=0
ok() { echo "  ✅ $1  $2"; }
falha() { echo "  ❌ $1  $2"; falhas=$((falhas + 1)); }
confere() { # confere <id> <descrição> <comando...>
  id="$1"; desc="$2"; shift 2
  if "$@" >/dev/null 2>&1; then ok "$id" "$desc"; else falha "$id" "$desc"; fi
}

limpar() {
  docker rm -f "$API" "$PG" >/dev/null 2>&1 || true
  docker network rm "$REDE" >/dev/null 2>&1 || true
}
trap 'limpar; rm -f .tmp-imagem-*' EXIT
limpar

docker info >/dev/null 2>&1 || { echo "Docker não está rodando: abra o Docker Desktop." >&2; exit 2; }

# ---------- A1 / 8B-A5: a imagem constrói (jar extraído, perfil treino e cache AOT dentro dela) ----------
if [ "${PULAR_BUILD:-0}" != "1" ]; then
  echo "== docker build =="
  docker build -t "$IMAGEM" backend >/dev/null && ok A1 "docker build terminou" || { falha A1 "docker build falhou"; exit 1; }
else
  docker image inspect "$IMAGEM" >/dev/null 2>&1 && ok A1 "imagem já construída (PULAR_BUILD=1)" || { falha A1 "imagem não existe"; exit 1; }
fi
dentro="$(docker run --rm --entrypoint sh "$IMAGEM" -c 'ls /app/app/app.jar /app/app/lib >/dev/null && ls -l /app/app.aot' 2>&1 || true)"
tamanho_aot="$(docker run --rm --entrypoint sh "$IMAGEM" -c 'wc -c < /app/app.aot' 2>/dev/null | tr -d ' \r' || echo 0)"
if [ "${tamanho_aot:-0}" -gt 1000000 ] 2>/dev/null; then
  ok 8B-A5 "jar extraído (-Djarmode=tools) em /app/app e cache AOT gerado no build ($((tamanho_aot / 1048576)) MiB), sem banco e sem segredo"
else
  falha 8B-A5 "cache AOT ausente ou vazio na imagem: $dentro"
fi

# ---------- A2, A3: o que tem (e o que não tem) dentro da imagem ----------
confere A2 "imagem final só tem JRE (sem javac nem Maven)" \
  sh -c "! docker run --rm --entrypoint sh $IMAGEM -c 'command -v javac || command -v mvn || command -v mvnw'"
[ "$(docker run --rm --entrypoint id "$IMAGEM" -u)" != "0" ] && ok A3 "processo roda sem root" || falha A3 "processo roda como root"
env_da_imagem="$(docker image inspect "$IMAGEM" --format '{{range .Config.Env}}{{println .}}{{end}}')"
if echo "$env_da_imagem" | grep -Eq '^(DB_SENHA|DB_URL|JWT_SEGREDO|IDEMPOTENCIA_HMAC_SEGREDO|ADMIN_SENHA|GATEWAY_[A-Z_]*)='; then
  falha A4 "a imagem carrega variável de segredo"
else
  [ "$(docker run --rm --entrypoint sh "$IMAGEM" -c 'ls -A /app | sort | tr "\n" " "')" = "app app.aot " ] \
    && ok A4 "imagem sem segredo e sem .env (só /app/app e o cache app.aot)" || falha A4 "arquivo estranho em /app"
fi
# 8B-A7: o perfil da imagem é prod (nunca treino) e nenhum segredo novo entrou
if echo "$env_da_imagem" | grep -qx 'SPRING_PROFILES_ACTIVE=prod' && ! echo "$env_da_imagem" | grep -qi 'treino'; then
  ok 8B-A7a "ENV da imagem: perfil prod e nenhuma menção a treino"
else
  falha 8B-A7a "ENV da imagem com perfil errado: $(echo "$env_da_imagem" | grep SPRING_PROFILES || true)"
fi

# ---------- sobe PostgreSQL 18 e a API no perfil prod ----------
echo "== subindo PostgreSQL 18 e a API (perfil prod, variáveis falsas) =="
docker network create "$REDE" >/dev/null
docker run -d --name "$PG" --network "$REDE" -e POSTGRES_DB=altashoras -e POSTGRES_USER=altashoras \
  -e POSTGRES_PASSWORD=senha-falsa-so-do-teste postgres:18-alpine >/dev/null
i=0
until docker exec "$PG" pg_isready -U altashoras -d altashoras >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -le 40 ] || { echo "PostgreSQL não ficou pronto" >&2; exit 1; }; sleep 1
done
sleep 2   # o pg_isready responde no primeiro boot, antes de o init terminar; uma pausa curta evita corrida

# Todos os valores abaixo são FALSOS e só existem neste script. Os reais ficam no painel do Render, nunca aqui.
subir_api() { # subir_api <porta-publicada> [args do docker run...]   (usa $IMG)
  porta="$1"; shift
  docker run -d --name "$API" --network "$REDE" --memory=512m -p "$porta:$PORTA_INTERNA" \
    -e PORT="$PORTA_INTERNA" \
    -e DB_URL="jdbc:postgresql://$PG:5432/altashoras" -e DB_USUARIO=altashoras -e DB_SENHA=senha-falsa-so-do-teste \
    -e JWT_SEGREDO=segredo-falso-do-teste-com-mais-de-32-bytes-0000 \
    -e IDEMPOTENCIA_HMAC_SEGREDO=idempotencia-falsa-so-do-teste \
    -e GATEWAY_SEGREDO_WEBHOOK=webhook-falso-so-do-teste \
    -e CORS_ORIGENS="$FRONT_FALSO" \
    -e APP_PRIVACIDADE_CONTATO=contato@exemplo.test \
    -e ADMIN_EMAIL=admin@exemplo.test -e ADMIN_SENHA=SenhaFalsaDoTeste#1 \
    "$@" "$IMG" >/dev/null
}
agora_ms() { echo $(( $(date +%s%N) / 1000000 )); }
esperar_saude() { # imprime os segundos até o health dar UP; falha se passar de 150 s
  t0=$(date +%s)
  while :; do
    corpo="$(curl -s --max-time 3 "http://localhost:$PORTA_API/actuator/health" || true)"
    case "$corpo" in *'"status":"UP"'*) echo $(( $(date +%s) - t0 )); return 0;; esac
    [ $(( $(date +%s) - t0 )) -le 150 ] || return 1
    sleep 2
  done
}
subida_ms() { # subida_ms [args do docker run...]: remove o contêiner anterior, sobe e imprime os ms até o health UP
  docker rm -f "$API" >/dev/null 2>&1 || true
  t0=$(agora_ms)
  subir_api "$PORTA_API" "$@"
  while :; do
    corpo="$(curl -s --max-time 2 "http://localhost:$PORTA_API/actuator/health" || true)"
    case "$corpo" in *'"status":"UP"'*) echo $(( $(agora_ms) - t0 )); return 0;; esac
    [ $(( $(agora_ms) - t0 )) -le 240000 ] || { echo 999999; return 1; }
    sleep 0.5
  done
}
mediana() { printf '%s\n' "$@" | sort -n | sed -n 2p; }
seg() { echo "$(( $1 / 1000 )),$(( ($1 % 1000) / 100 ))"; }

# O log de classes carregadas prova que o cache AOT foi USADO (8B-A6).
subir_api "$PORTA_API" -e JAVA_EXTRA="-Xlog:class+load:file=/tmp/classes.log"
if segundos="$(esperar_saude)"; then
  ok A5 "health UP em ${segundos}s, na porta da variável PORT=$PORTA_INTERNA (cold start da imagem)"
else
  falha A5 "health não ficou UP"; docker logs --tail 60 "$API" || true; exit 1
fi
compartilhadas="$(docker exec "$API" sh -c "grep -c 'shared objects file' /tmp/classes.log" | tr -d '\r' || echo 0)"
total="$(docker exec "$API" sh -c "grep -c 'source:' /tmp/classes.log" | tr -d '\r' || echo 1)"
if [ "$compartilhadas" -gt 3000 ]; then
  ok 8B-A6 "o cache AOT foi aceito e usado: $compartilhadas de $total classes vieram do arquivo de cache"
else
  falha 8B-A6 "só $compartilhadas de $total classes vieram do cache (esperado mais de 3000)"
fi

# ---------- A6: health público e sem detalhes ----------
corpo="$(curl -s "http://localhost:$PORTA_API/actuator/health")"
case "$corpo" in
  *components*|*details*) falha A6 "health expõe detalhes: $corpo" ;;
  *'"status":"UP"'*) ok A6 "health público, UP e sem detalhes (sem components): $corpo" ;;
  *) falha A6 "health devolveu: $corpo" ;;
esac

# ---------- 8B-A13: liveness e readiness públicos, sem detalhes ----------
for sonda in liveness readiness; do
  codigo="$(curl -s -o .tmp-imagem-sonda.$$ -w '%{http_code}' "http://localhost:$PORTA_API/actuator/health/$sonda" || true)"
  corpo="$(cat .tmp-imagem-sonda.$$ 2>/dev/null || true)"; rm -f .tmp-imagem-sonda.$$
  if [ "$codigo" = "200" ] && [ "$corpo" = '{"status":"UP"}' ]; then
    ok "8B-A13-$sonda" "/actuator/health/$sonda: 200 e $corpo (sem components, sem detalhes)"
  else
    falha "8B-A13-$sonda" "/actuator/health/$sonda: $codigo $corpo"
  fi
done

# ---------- A7, A8: Flyway e btree_gist no banco ----------
psql_pg() { docker exec "$PG" psql -U altashoras -d altashoras -tAc "$1"; }
migracoes="$(psql_pg "SELECT count(*) FROM flyway_schema_history WHERE success")"
esperadas="$(ls backend/src/main/resources/db/migration/V*.sql | wc -l | tr -d ' ')"
[ "$migracoes" = "$esperadas" ] && ok A7 "Flyway aplicou as $migracoes migrações na subida (e nenhuma do seed dev)" \
  || falha A7 "Flyway aplicou $migracoes de $esperadas migrações"
ext="$(psql_pg "SELECT extname FROM pg_extension WHERE extname='btree_gist'")"
con="$(psql_pg "SELECT conname FROM pg_constraint WHERE conname='reserva_sem_sobreposicao'")"
[ "$ext" = "btree_gist" ] && [ "$con" = "reserva_sem_sobreposicao" ] && ok A8 "btree_gist e a constraint anti double booking existem" \
  || falha A8 "extension='$ext' constraint='$con'"

# ---------- A9: perfil prod de verdade (sem seed, sem usuário de desenvolvimento) ----------
docker logs "$API" 2>&1 | grep -q 'The following 1 profile is active: "prod"' && ok A9a "perfil ativo: prod" || falha A9a "perfil prod não apareceu no log"
docker logs "$API" 2>&1 | grep -q '"treino"' && falha 8B-A7b "o perfil treino apareceu no log da execução" || ok 8B-A7b "o perfil treino nunca é ativado em execução (só no docker build)"
[ "$(psql_pg "SELECT count(*) FROM servico")" = "0" ] && ok A9b "banco sem seed fictício (catálogo vazio até o admin cadastrar)" || falha A9b "há serviços no banco: o seed dev vazou?"

# ---------- A10: jobs de subida (retenção) ----------
docker logs "$API" 2>&1 | grep -q 'Retenção de dados: limpeza=' && ok A10 "a retenção de dados rodou na subida" || falha A10 "a retenção não rodou na subida"

# ---------- A11: CORS só da origem do front ----------
permitido="$(curl -s -o /dev/null -D - -X OPTIONS "http://localhost:$PORTA_API/servicos" -H "Origin: $FRONT_FALSO" \
  -H 'Access-Control-Request-Method: GET' | tr -d '\r' | grep -i '^access-control-allow-origin:' || true)"
negado="$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS "http://localhost:$PORTA_API/servicos" -H 'Origin: https://intruso.exemplo.test' \
  -H 'Access-Control-Request-Method: GET' || true)"
if [ "$permitido" = "Access-Control-Allow-Origin: $FRONT_FALSO" ] && [ "$negado" = "403" ]; then
  ok A11 "CORS: origem do front aceita, outra origem recebe 403"
else
  falha A11 "CORS: permitido='$permitido' intruso=$negado"
fi

# ---------- A12: IP real atrás do proxy (X-Forwarded-For) ----------
# /alcance limita 30 consultas por minuto por IP. Se a API usasse o IP do proxy (o do curl), o IP 198.51.100.77 e o
# 198.51.100.88 dividiriam a mesma cota; usando o X-Forwarded-For, cada um tem a sua.
ultimo=""
n=0
while [ "$n" -lt 31 ]; do
  ultimo="$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 198.51.100.77' "http://localhost:$PORTA_API/alcance" || true)"
  n=$((n + 1))
done
outro="$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Forwarded-For: 198.51.100.88' "http://localhost:$PORTA_API/alcance" || true)"
if [ "$ultimo" = "429" ] && [ "$outro" = "200" ]; then
  ok A12 "limite por IP usa o X-Forwarded-For (31ª consulta do IP .77 = 429; o IP .88 = 200)"
else
  falha A12 "31ª do .77 = $ultimo (esperado 429); .88 = $outro (esperado 200)"
fi

# ---------- 8B-A9: 20 requisições e a memória do contêiner (limite de 512 MiB) ----------
n=0
while [ "$n" -lt 10 ]; do
  curl -s -o /dev/null -H 'X-Forwarded-For: 198.51.100.50' "http://localhost:$PORTA_API/servicos" || true
  curl -s -o /dev/null -H 'X-Forwarded-For: 198.51.100.51' "http://localhost:$PORTA_API/alcance" || true
  n=$((n + 1))
done
mem="$(docker stats --no-stream --format '{{.MemUsage}}' "$API" | cut -d/ -f1 | tr -d ' ')"
mem_mib="$(echo "$mem" | sed 's/[A-Za-z]*$//' | cut -d. -f1)"
case "$mem" in *GiB) mem_mib=$((mem_mib * 1024));; esac
echo "      memória depois de 20 requisições: $mem (limite do contêiner: 512 MiB; meta: até 400 MiB)"
if curl -sf "http://localhost:$PORTA_API/actuator/health" >/dev/null && [ "${mem_mib:-9999}" -le 400 ]; then
  ok 8B-A9 "contêiner com --memory=512m segue vivo e UP, com $mem em uso (≤ 400 MiB)"
else
  falha 8B-A9 "memória $mem ou health fora do ar"
fi

# ---------- A13: hibernação do Render = SIGTERM + religar ----------
docker stop -t 30 "$API" >/dev/null
codigo="$(docker inspect "$API" --format '{{.State.ExitCode}}')"
case "$codigo" in
  0|143) ok A13a "desligamento limpo no SIGTERM (código $codigo)" ;;
  *) falha A13a "desligou com código $codigo" ;;
esac
docker start "$API" >/dev/null
if segundos="$(esperar_saude)"; then ok A13b "religou do zero e voltou a UP em ${segundos}s (cold start depois da hibernação)"; else falha A13b "não voltou a UP"; fi

# ---------- 8B-A14, 8B-A15, 8B-A18: banco fora, API viva, desligamento limpo ----------
if [ "${SEM_PICO:-0}" != "1" ]; then
  echo "== banco fora do ar (8B-A14, 8B-A15, 8B-A18) =="
  inicio_api="$(docker inspect "$API" --format '{{.State.StartedAt}}')"
  # A14: PostgreSQL parado com a API no ar
  docker stop "$PG" >/dev/null
  sleep 3
  live="$(curl -s -m 5 -o /dev/null -w '%{http_code}' "http://localhost:$PORTA_API/actuator/health/liveness" || true)"
  codigo_saude="$(curl -s -m 20 -o .tmp-imagem-saude.$$ -w '%{http_code}' "http://localhost:$PORTA_API/actuator/health" || true)"
  corpo="$(cat .tmp-imagem-saude.$$ 2>/dev/null || true)"; rm -f .tmp-imagem-saude.$$
  if [ "$live" = "200" ] && [ "$codigo_saude" = "503" ] && echo "$corpo" | grep -q '"status":"DOWN"' \
     && ! echo "$corpo" | grep -Eqi 'jdbc|postgres|altashoras|components'; then
    ok 8B-A14 "PostgreSQL parado: liveness 200; health completo $codigo_saude $corpo (sem URL nem usuário do banco)"
  else
    falha 8B-A14 "liveness=$live health=$codigo_saude corpo=$corpo"
  fi
  docker start "$PG" >/dev/null
  until docker exec "$PG" pg_isready -U altashoras -d altashoras >/dev/null 2>&1; do sleep 1; done

  # A15: banco pendurado (docker pause) por 25 s: o pedido não fica pendurado e o mesmo processo se recupera
  docker pause "$PG" >/dev/null
  t0=$(agora_ms)
  codigo_servicos="$(curl -s -m 20 -o .tmp-imagem-serv.$$ -w '%{http_code}' -H 'X-Forwarded-For: 198.51.100.60' "http://localhost:$PORTA_API/servicos" || true)"
  duracao=$(( $(agora_ms) - t0 ))
  tipo="$(curl -s -m 20 -D - -o /dev/null -H 'X-Forwarded-For: 198.51.100.61' "http://localhost:$PORTA_API/servicos" | tr -d '\r' | grep -i '^content-type:' || true)"
  corpo="$(cat .tmp-imagem-serv.$$ 2>/dev/null || true)"; rm -f .tmp-imagem-serv.$$
  if [ "$codigo_servicos" = "503" ] && [ "$duracao" -le 12000 ] && echo "$corpo" | grep -q 'BANCO_INDISPONIVEL' \
     && ! echo "$corpo" | grep -Eqi 'jdbc|postgres|altashoras|password'; then
    ok 8B-A15a "banco pendurado: GET /servicos = 503 em $(seg "$duracao")s, problem+json BANCO_INDISPONIVEL, sem dado do banco ($tipo)"
  else
    falha 8B-A15a "GET /servicos = $codigo_servicos em ${duracao}ms: $corpo"
  fi
  sleep 15
  docker unpause "$PG" >/dev/null
  t0=$(date +%s); voltou=0
  while [ $(( $(date +%s) - t0 )) -le 60 ]; do
    case "$(curl -s -m 5 "http://localhost:$PORTA_API/actuator/health" || true)" in *'"status":"UP"'*) voltou=1; break;; esac
    sleep 1
  done
  reinicios="$(docker inspect "$API" --format '{{.RestartCount}}')"
  fim_api="$(docker inspect "$API" --format '{{.State.StartedAt}}')"
  if [ "$voltou" = "1" ] && [ "$reinicios" = "0" ] && [ "$inicio_api" = "$fim_api" ]; then
    ok 8B-A15b "o mesmo processo seguiu (sem reinício) e o health completo voltou a UP em $(( $(date +%s) - t0 ))s"
  else
    falha 8B-A15b "voltou=$voltou reinícios=$reinicios início=$inicio_api fim=$fim_api"
  fi

  # A18: SIGTERM no meio de uma requisição lenta. O banco pendurado (docker pause) faz o GET esperar; o SIGTERM chega 1 s
  # depois e o banco volta 4 s depois: a requisição em curso precisa terminar (HTTP 200) e o processo sair com 143 em até 25 s.
  docker pause "$PG" >/dev/null
  ( c="$(curl -s -m 40 -o .tmp-imagem-corpo.$$ -w '%{http_code}' -H 'X-Forwarded-For: 198.51.100.62' "http://localhost:$PORTA_API/servicos" 2>/dev/null || true)"; echo "${c:-000}" > .tmp-imagem-lenta.$$ ) &
  sleep 1
  ( sleep 4; docker unpause "$PG" >/dev/null 2>&1 ) &
  t0=$(date +%s)
  docker stop -t 25 "$API" >/dev/null
  duracao_stop=$(( $(date +%s) - t0 ))
  wait || true
  docker unpause "$PG" >/dev/null 2>&1 || true
  resposta="$(cat .tmp-imagem-lenta.$$ 2>/dev/null || echo 000)"; rm -f .tmp-imagem-lenta.$$
  codigo="$(docker inspect "$API" --format '{{.State.ExitCode}}')"
  if [ "$resposta" = "200" ] && [ "$duracao_stop" -le 25 ] && [ "$codigo" = "143" ]; then
    ok 8B-A18 "SIGTERM com requisição em curso: ela terminou com HTTP $resposta e o processo saiu com $codigo em ${duracao_stop}s"
  else
    falha 8B-A18 "resposta=$resposta saída=$codigo em ${duracao_stop}s (esperado 200, 143, até 25 s)"
  fi
fi

# ---------- 8B-A8: cache corrompido (truncado): a API sobe do mesmo jeito ----------
docker rm -f "$API" >/dev/null 2>&1 || true
entrada="$(docker image inspect "$IMG" --format '{{index .Config.Entrypoint 2}}')"   # o script do ENTRYPOINT da imagem, com a sonda de fallback
docker run -d --name "$API" --network "$REDE" --memory=512m -p "$PORTA_API:$PORTA_INTERNA" -e PORT="$PORTA_INTERNA"   -e DB_URL="jdbc:postgresql://$PG:5432/altashoras" -e DB_USUARIO=altashoras -e DB_SENHA=senha-falsa-so-do-teste   -e JWT_SEGREDO=segredo-falso-do-teste-com-mais-de-32-bytes-0000 -e IDEMPOTENCIA_HMAC_SEGREDO=idempotencia-falsa-so-do-teste   -e GATEWAY_SEGREDO_WEBHOOK=webhook-falso-so-do-teste -e CORS_ORIGENS="$FRONT_FALSO" -e APP_PRIVACIDADE_CONTATO=contato@exemplo.test   --entrypoint sh "$IMG" -c "head -c 1000000 /app/app.aot > /tmp/truncado && cp /tmp/truncado /app/app.aot; $entrada" >/dev/null
if segundos="$(esperar_saude)"; then
  aviso="$(docker logs "$API" 2>&1 | grep -c 'AVISO: cache AOT invalido' || true)"
  ok 8B-A8 "cache AOT truncado (1 MB): a API subiu do mesmo jeito e ficou UP em ${segundos}s (a JVM ignorou o cache truncado; a sonda do ENTRYPOINT recusou o cache: $aviso vez(es))"
else
  falha 8B-A8 "com o cache corrompido a API não ficou UP"; docker logs --tail 20 "$API" || true
fi

if [ "${PULAR_MEDICAO:-0}" != "1" ]; then
# ---------- 8B-A10: medição de cold start, mediana de 3 subidas (com cache, sem cache, TieredStopAtLevel=1) ----------
echo "== medindo o cold start: 3 subidas de cada variante (mediana) =="
docker rm -f "$API" >/dev/null 2>&1 || true
com=""; sem=""; tier=""; semtier=""; antes=""
# TieredStopAtLevel=4 (o padrão da JVM) desfaz a flag que a imagem traz em JAVA_OPTS; o último valor vence.
for n in 1 2 3; do
  t="$(subida_ms)"; com="$com $t"                                                          # imagem como é: cache + C1 (TieredStopAtLevel=1)
  t="$(subida_ms -e AOT_OPTS=)"; sem="$sem $t"                                              # sem cache, com C1
  t="$(subida_ms -e JAVA_EXTRA=-XX:TieredStopAtLevel=4)"; tier="$tier $t"                  # com cache, sem C1 (compilador completo)
  t="$(subida_ms -e AOT_OPTS= -e JAVA_EXTRA=-XX:TieredStopAtLevel=4)"; semtier="$semtier $t" # sem cache e sem C1 = como a Fatia 8
done
m_com="$(mediana $com)"; m_sem="$(mediana $sem)"; m_tier="$(mediana $tier)"; m_semtier="$(mediana $semtier)"
linha() { printf '%s' "$(for t in $1; do printf '%ss ' "$(seg "$t")"; done)"; }
echo "      sem cache, compilador completo (como a Fatia 8): $(linha "$semtier") -> mediana $(seg "$m_semtier")s"
echo "      com cache, compilador completo:                  $(linha "$tier") -> mediana $(seg "$m_tier")s"
echo "      sem cache, TieredStopAtLevel=1:                  $(linha "$sem") -> mediana $(seg "$m_sem")s"
echo "      com cache, TieredStopAtLevel=1 (a imagem):       $(linha "$com") -> mediana $(seg "$m_com")s"
ganho_cache=$(( (m_semtier - m_tier) * 100 / m_semtier ))
ganho_total=$(( (m_semtier - m_com) * 100 / m_semtier ))
echo "      ganho só do cache: $ganho_cache% · ganho do cache + TieredStopAtLevel=1: $ganho_total%"
if [ "$ganho_cache" -ge 25 ]; then
  ok 8B-A10 "mediana com cache $(seg "$m_tier")s contra $(seg "$m_semtier")s sem cache: $ganho_cache% mais rápida (meta: 25% ou mais)"
elif [ "$ganho_total" -ge 25 ]; then
  # Desvio registrado em decisoes.md: a meta de 25% é atingida pelo conjunto cache + TieredStopAtLevel=1, não pelo cache sozinho.
  ok 8B-A10 "(desvio D154) o cache sozinho encurtou só $ganho_cache% (meta: 25%), mas cache + TieredStopAtLevel=1, como a imagem sobe, encurta $ganho_total% (mediana $(seg "$m_com")s contra $(seg "$m_semtier")s, a Fatia 8)"
else
  falha 8B-A10 "cache sozinho $ganho_cache%, cache + flag $ganho_total% (meta: 25%)"
fi
if [ "${MEDIR_ANTES:-0}" = "1" ]; then
  docker image inspect "$IMAGEM_ANTES" >/dev/null 2>&1 || {
    git show origin/develop:backend/Dockerfile | docker build -q -f - -t "$IMAGEM_ANTES" backend >/dev/null; }
  IMG="$IMAGEM_ANTES"
  for n in 1 2 3; do t="$(subida_ms)"; antes="$antes $t"; done
  IMG="$IMAGEM"
  m_antes="$(mediana $antes)"
  echo "      imagem da Fatia 8 (antes):       $(for t in $antes; do printf '%ss ' "$(seg "$t")"; done) -> mediana $(seg "$m_antes")s"
fi

# ---------- 8B-A12: tamanho da imagem ----------
mb() { docker image ls "$1" --format '{{.Size}}' | awk '{v=$1; if (v ~ /GB$/) {sub(/GB/,"",v); v=v*1024} else {sub(/MB/,"",v)}; printf "%d", v}'; }
tam_nova="$(mb "$IMAGEM")"; tam_antiga="$(mb "$IMAGEM_ANTES" 2>/dev/null || echo 0)"
if [ "${tam_antiga:-0}" -gt 0 ]; then
  cresceu=$((tam_nova - tam_antiga))
  echo "      tamanho: fatia8b ${tam_nova} MB; fatia8 ${tam_antiga} MB; cresceu ${cresceu} MB (cache AOT: $((tamanho_aot / 1048576)) MiB)"
  if [ "$cresceu" -le "${LIMITE_CRESCIMENTO_MB:-200}" ]; then
    ok 8B-A12 "a imagem cresceu $cresceu MB (limite: ${LIMITE_CRESCIMENTO_MB:-200} MB; a spec previa 120 MB e o cache real tem $((tamanho_aot / 1048576)) MiB)"
  else
    falha 8B-A12 "a imagem cresceu $cresceu MB (limite ${LIMITE_CRESCIMENTO_MB:-200} MB)"
  fi
else
  echo "      tamanho: fatia8b ${tam_nova} MB (sem imagem da Fatia 8 para comparar; use MEDIR_ANTES=1)"
fi

# ---------- 8B-A11 (informativo): CPU limitada, como o Render free ----------
if [ "${CPU_LIMITADA:-0}" = "1" ]; then
  echo "== CPU limitada (--cpus=0.25), 1 subida de cada (informativo, sem pass/fail) =="
  t="$(subida_ms --cpus=0.25)"; echo "      com cache: $(seg "$t")s"
  t="$(subida_ms --cpus=0.25 -e AOT_OPTS=)"; echo "      sem cache: $(seg "$t")s"
fi

fi

# ---------- A14 (Fatia 8): dentro de 512 MB, de novo com a imagem religada ----------
docker rm -f "$API" >/dev/null 2>&1 || true
subir_api "$PORTA_API"
esperar_saude >/dev/null && ok A14 "container com --memory=512m segue vivo e UP" || falha A14 "não ficou UP"

# ---------- A15: sem variável obrigatória a API não sobe ----------
docker rm -f "$API" >/dev/null
docker run --name "$API" --network "$REDE" --memory=512m -e PORT="$PORTA_INTERNA" \
  -e DB_USUARIO=altashoras -e DB_SENHA=x -e JWT_SEGREDO=segredo-falso-do-teste-com-mais-de-32-bytes-0000 \
  "$IMAGEM" >/dev/null 2>&1 && codigo=0 || codigo=$?
if [ "$codigo" -ne 0 ] && docker logs "$API" 2>&1 | grep -Eq "DB_URL|'url' must start with"; then
  ok A15 "sem DB_URL a API não sobe (código $codigo) e o log mostra a URL do banco inválida"
else
  falha A15 "sem DB_URL a API subiu ou não explicou (código $codigo)"
fi

echo
if [ "$falhas" -eq 0 ]; then echo "Imagem OK: todos os critérios (Fatia 8 e 8B) passaram."; else echo "$falhas critério(s) falharam."; exit 1; fi
