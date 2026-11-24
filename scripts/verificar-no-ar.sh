#!/usr/bin/env sh
# "Acabou o deploy: está tudo no ar?" (Fatia 8B, E16, A26). Só curl, sem credencial e sem escrita: serve depois de um
# deploy, de um rollback e no dia a dia. Sai 0 só se TODAS as checagens passarem; senão diz qual caiu.
#
# Uso: scripts/verificar-no-ar.sh                      (API e front de produção do beta)
#      API_URL=https://... FRONT_URL=https://... scripts/verificar-no-ar.sh
#      scripts/verificar-no-ar.sh --local              (sobe a imagem da API + um PostgreSQL + um front falso e prova o
#                                                       próprio script: passa no cenário certo e FALHA com o front quebrado;
#                                                       precisa de Docker e da imagem altas-horas-api:fatia8b)
# Checagens:
#   1. health completo da API = UP (espera até ESPERA_S segundos: a API pode estar acordando do cold start)
#   2. home do front = 200
#   3. CORS: a origem do front é permitida e uma origem alheia é barrada (403)
#   4. /pagamento-simulado/<id> do front não é 404 (A63 da Fatia 7)
#   5. o front fala com a MESMA API: GET <front>/api/servicos devolve o mesmo corpo que GET <api>/servicos
set -eu
export MSYS_NO_PATHCONV=1

API_URL="${API_URL:-https://altas-horas-api.onrender.com}"
FRONT_URL="${FRONT_URL:-https://barbearia-altas-horas.vercel.app}"
ESPERA_S="${ESPERA_S:-240}"
ORIGEM_ALHEIA="https://intruso.exemplo.test"

verificar() {
  api="${1%/}"; front="${2%/}"; origem_do_front="$front"
  falhas=0
  ok() { echo "  ✅ $1"; }
  falha() { echo "  ❌ $1"; falhas=$((falhas + 1)); }

  # 1. health completo, esperando a API acordar
  inicio=$(date +%s); corpo=""
  while :; do
    corpo="$(curl -s -m 15 "$api/actuator/health" || true)"
    case "$corpo" in *'"status":"UP"'*) break;; esac
    [ $(( $(date +%s) - inicio )) -lt "$ESPERA_S" ] || break
    sleep 5
  done
  case "$corpo" in
    *'"status":"UP"'*) ok "1. health completo da API: UP ($corpo) em $(( $(date +%s) - inicio ))s" ;;
    *) falha "1. health completo da API não ficou UP em ${ESPERA_S}s (resposta: '${corpo}')" ;;
  esac

  # 2. home do front
  codigo="$(curl -s -m 30 -o /dev/null -w '%{http_code}' "$front/" || true)"
  [ "$codigo" = "200" ] && ok "2. home do front: 200" || falha "2. home do front respondeu $codigo (esperado 200)"

  # 3. CORS
  permitido="$(curl -s -m 30 -o /dev/null -D - -X OPTIONS "$api/servicos" -H "Origin: $origem_do_front" \
    -H 'Access-Control-Request-Method: GET' | tr -d '\r' | grep -i '^access-control-allow-origin:' || true)"
  negado="$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X OPTIONS "$api/servicos" -H "Origin: $ORIGEM_ALHEIA" \
    -H 'Access-Control-Request-Method: GET' || true)"
  if [ "$permitido" = "Access-Control-Allow-Origin: $origem_do_front" ] && [ "$negado" = "403" ]; then
    ok "3. CORS: origem do front permitida; origem alheia = 403"
  else
    falha "3. CORS: permitido='$permitido' (esperado a origem do front), alheia=$negado (esperado 403). Confira CORS_ORIGENS no Render"
  fi

  # 4. rota do pagamento simulado no front
  codigo="$(curl -s -m 30 -o /dev/null -w '%{http_code}' "$front/pagamento-simulado/qualquer-id" || true)"
  if [ "$codigo" = "404" ] || [ "$codigo" = "000" ]; then
    falha "4. /pagamento-simulado/qualquer-id respondeu $codigo (a rota do front some do deploy?)"
  else
    ok "4. /pagamento-simulado/qualquer-id existe no front (HTTP $codigo, não é 404)"
  fi

  # 5. o front usa a mesma API
  da_api="$(curl -s -m 30 "$api/servicos" || true)"
  do_front="$(curl -s -m 30 "$front/api/servicos" || true)"
  if [ -n "$da_api" ] && [ "$da_api" = "$do_front" ]; then
    ok "5. o front fala com esta API (GET /api/servicos do front = GET /servicos da API)"
  else
    falha "5. o front devolveu algo diferente da API em /servicos. Confira API_URL no Vercel (front: '$(printf '%.60s' "$do_front")')"
  fi

  echo
  if [ "$falhas" -eq 0 ]; then echo "No ar: todas as checagens passaram ($api e $front)."; return 0; fi
  echo "$falhas checagem(ns) falharam."; return 1
}

if [ "${1:-}" != "--local" ]; then
  verificar "$API_URL" "$FRONT_URL"
  exit $?
fi

# ---------------- --local: prova o próprio script contra a imagem e um front falso ----------------
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"
IMAGEM="${IMAGEM:-altas-horas-api:fatia8b}"
REDE="altas-horas-verificar"; PG="altas-horas-verificar-pg"; API="altas-horas-verificar-api"; FRONT="altas-horas-verificar-front"
PORTA_API=18090; PORTA_FRONT=18091
FRONT_FALSO="http://localhost:$PORTA_FRONT"
limpar() { docker rm -f "$API" "$PG" "$FRONT" >/dev/null 2>&1 || true; docker network rm "$REDE" >/dev/null 2>&1 || true; }
trap limpar EXIT
limpar
docker info >/dev/null 2>&1 || { echo "Docker não está rodando: abra o Docker Desktop." >&2; exit 2; }
docker image inspect "$IMAGEM" >/dev/null 2>&1 || { echo "falta a imagem $IMAGEM (docker build -t $IMAGEM backend)" >&2; exit 2; }

docker network create "$REDE" >/dev/null
docker run -d --name "$PG" --network "$REDE" -e POSTGRES_DB=altashoras -e POSTGRES_USER=altashoras \
  -e POSTGRES_PASSWORD=senha-falsa-so-do-teste postgres:18-alpine >/dev/null
i=0; until docker exec "$PG" pg_isready -U altashoras -d altashoras >/dev/null 2>&1; do i=$((i + 1)); [ "$i" -le 40 ] || exit 1; sleep 1; done
sleep 2
docker run -d --name "$API" --network "$REDE" --memory=512m -p "$PORTA_API:10000" -e PORT=10000 \
  -e DB_URL="jdbc:postgresql://$PG:5432/altashoras" -e DB_USUARIO=altashoras -e DB_SENHA=senha-falsa-so-do-teste \
  -e JWT_SEGREDO=segredo-falso-do-teste-com-mais-de-32-bytes-0000 -e IDEMPOTENCIA_HMAC_SEGREDO=idempotencia-falsa-so-do-teste \
  -e GATEWAY_SEGREDO_WEBHOOK=webhook-falso-so-do-teste -e CORS_ORIGENS="$FRONT_FALSO" \
  -e APP_PRIVACIDADE_CONTATO=contato@exemplo.test "$IMAGEM" >/dev/null

# Front falso: busybox httpd com a home, a rota de pagamento simulado e a cópia do /servicos da API.
docker run -d --name "$FRONT" -p "$PORTA_FRONT:80" busybox sh -c 'mkdir -p /www/pagamento-simulado /www/api; httpd -f -p 80 -h /www' >/dev/null
sleep 2
nofront() { docker exec "$FRONT" sh -c "$1"; }
nofront 'echo home > /www/index.html; echo pagamento > /www/pagamento-simulado/qualquer-id'

echo "== cenário certo: tudo no ar =="
t=0; until curl -sf -m 3 "http://localhost:$PORTA_API/actuator/health" >/dev/null 2>&1; do t=$((t + 1)); [ "$t" -le 120 ] || exit 1; sleep 2; done
corpo_servicos="$(curl -s "http://localhost:$PORTA_API/servicos")"
nofront "printf '%s' '$corpo_servicos' > /www/api/servicos"
ESPERA_S=10 verificar "http://localhost:$PORTA_API" "$FRONT_FALSO" && certo=0 || certo=1

echo
echo "== cenário quebrado: a rota /pagamento-simulado some do front e o /api/servicos diverge =="
nofront 'rm /www/pagamento-simulado/qualquer-id; echo "[]x" > /www/api/servicos'
if ESPERA_S=10 verificar "http://localhost:$PORTA_API" "$FRONT_FALSO" > /tmp/verificar-quebrado.$$ 2>&1; then quebrado=0; else quebrado=1; fi
cat /tmp/verificar-quebrado.$$
quatro="$(grep -c '❌ 4\.' /tmp/verificar-quebrado.$$ || true)"; cinco="$(grep -c '❌ 5\.' /tmp/verificar-quebrado.$$ || true)"
rm -f /tmp/verificar-quebrado.$$

echo
if [ "$certo" -eq 0 ] && [ "$quebrado" -eq 1 ] && [ "$quatro" -eq 1 ] && [ "$cinco" -eq 1 ]; then
  echo "✅ A26: passou no cenário certo e falhou, dizendo qual checagem caiu (4 e 5), no cenário quebrado."
else
  echo "❌ A26: certo=$certo (esperado 0), quebrado=$quebrado (esperado 1), caiu a 4: $quatro, caiu a 5: $cinco"
  exit 1
fi
