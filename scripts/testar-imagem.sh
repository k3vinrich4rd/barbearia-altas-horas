#!/usr/bin/env sh
# Prova que a imagem Docker da API (backend/Dockerfile) sobe no perfil prod contra um PostgreSQL 18 local, com variáveis
# FALSAS e sem nenhuma conta em provedor. Cobre os critérios A1 a A12 da Fatia 8 (docs/sdd/fatia-8-deploy.md).
# Precisa só de Docker (Docker Desktop aberto) e curl. Não usa rede externa além de baixar as imagens base.
#
# Uso: scripts/testar-imagem.sh            (constrói a imagem e testa)
#      PULAR_BUILD=1 scripts/testar-imagem.sh   (reaproveita a imagem altas-horas-api:fatia8 já construída)
set -eu

# No Git Bash do Windows, "/actuator" viraria um caminho do Windows dentro do docker exec.
export MSYS_NO_PATHCONV=1

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"   # caminhos relativos: o docker.exe do Windows não entende o /c/... do Git Bash
IMAGEM="altas-horas-api:fatia8"
REDE="altas-horas-fatia8"
PG="altas-horas-fatia8-pg"
API="altas-horas-fatia8-api"
PORTA_API="${PORTA_API:-18080}"
PORTA_INTERNA=10000      # como o Render injeta a PORT (A5): a imagem tem de obedecer, não fixar 8080
FRONT_FALSO="https://front.exemplo.test"

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
trap limpar EXIT
limpar

docker info >/dev/null 2>&1 || { echo "Docker não está rodando: abra o Docker Desktop." >&2; exit 2; }

# ---------- A1: a imagem constrói ----------
if [ "${PULAR_BUILD:-0}" != "1" ]; then
  echo "== docker build =="
  docker build -t "$IMAGEM" backend >/dev/null && ok A1 "docker build terminou" || { falha A1 "docker build falhou"; exit 1; }
else
  docker image inspect "$IMAGEM" >/dev/null 2>&1 && ok A1 "imagem já construída (PULAR_BUILD=1)" || { falha A1 "imagem não existe"; exit 1; }
fi

# ---------- A2, A3: o que tem (e o que não tem) dentro da imagem ----------
confere A2 "imagem final só tem JRE (sem javac nem Maven)" \
  sh -c "! docker run --rm --entrypoint sh $IMAGEM -c 'command -v javac || command -v mvn || command -v mvnw'"
[ "$(docker run --rm --entrypoint id "$IMAGEM" -u)" != "0" ] && ok A3 "processo roda sem root" || falha A3 "processo roda como root"
env_da_imagem="$(docker image inspect "$IMAGEM" --format '{{range .Config.Env}}{{println .}}{{end}}')"
if echo "$env_da_imagem" | grep -Eq '^(DB_SENHA|DB_URL|JWT_SEGREDO|IDEMPOTENCIA_HMAC_SEGREDO|ADMIN_SENHA|GATEWAY_[A-Z_]*)='; then
  falha A4 "a imagem carrega variável de segredo"
else
  [ "$(docker run --rm --entrypoint sh "$IMAGEM" -c 'ls -A /app')" = "app.jar" ] && ok A4 "imagem sem segredo e sem .env (só o app.jar)" || falha A4 "arquivo estranho em /app"
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
subir_api() { # subir_api <porta-publicada> [env extra...]
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
    "$@" "$IMAGEM" >/dev/null
}
esperar_saude() { # imprime os segundos até o health dar UP; falha se passar de 150 s
  t0=$(date +%s)
  while :; do
    corpo="$(curl -s --max-time 3 "http://localhost:$PORTA_API/actuator/health" || true)"
    case "$corpo" in *'"status":"UP"'*) echo $(( $(date +%s) - t0 )); return 0;; esac
    [ $(( $(date +%s) - t0 )) -le 150 ] || return 1
    sleep 2
  done
}

subir_api "$PORTA_API"
if segundos="$(esperar_saude)"; then
  ok A5 "health UP em ${segundos}s, na porta da variável PORT=$PORTA_INTERNA (cold start da imagem)"
else
  falha A5 "health não ficou UP"; docker logs --tail 60 "$API" || true; exit 1
fi

# ---------- A6: health público e sem detalhes ----------
corpo="$(curl -s "http://localhost:$PORTA_API/actuator/health")"
case "$corpo" in
  *components*|*details*) falha A6 "health expõe detalhes: $corpo" ;;
  *'"status":"UP"'*) ok A6 "health público, UP e sem detalhes (sem components): $corpo" ;;
  *) falha A6 "health devolveu: $corpo" ;;
esac

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

# ---------- A13: hibernação do Render = SIGTERM + religar ----------
docker stop -t 30 "$API" >/dev/null
codigo="$(docker inspect "$API" --format '{{.State.ExitCode}}')"
case "$codigo" in
  0|143) ok A13a "desligamento limpo no SIGTERM (código $codigo)" ;;
  *) falha A13a "desligou com código $codigo" ;;
esac
docker start "$API" >/dev/null
if segundos="$(esperar_saude)"; then ok A13b "religou do zero e voltou a UP em ${segundos}s (cold start depois da hibernação)"; else falha A13b "não voltou a UP"; fi

# ---------- A14: dentro de 512 MB ----------
mem="$(docker stats --no-stream --format '{{.MemUsage}}' "$API" | cut -d/ -f1 | tr -d ' ')"
echo "      memória em uso depois da subida: $mem (limite do container: 512 MiB)"
confere A14 "container com --memory=512m segue vivo e UP" curl -sf "http://localhost:$PORTA_API/actuator/health"

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
if [ "$falhas" -eq 0 ]; then echo "Imagem OK: todos os critérios de A1 a A15 passaram."; else echo "$falhas critério(s) falharam."; exit 1; fi
