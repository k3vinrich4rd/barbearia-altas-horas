#!/usr/bin/env sh
# Ensaia, na sua máquina, os MESMOS comandos do .github/workflows/backup-banco.yml (pg_dump -Fc em Docker + gpg
# simétrico AES-256) e depois a restauração (descriptografar + pg_restore) num banco novo. Critério A20 da Fatia 8.
#
# Modo --real (Fatia 8B, A31): restaura o artefato REAL baixado do GitHub (quem roda é o Kevin, dono da senha):
#   ARQUIVO=altashoras-AAAA-MM-DD.dump.gpg scripts/testar-backup.sh --real     (a senha é pedida sem eco; ou BACKUP_SENHA no ambiente)
# Descriptografa, restaura num PostgreSQL 18 local vazio e confere btree_gist, a constraint anti double booking e as
# migrações no flyway_schema_history; confere também que uma senha errada NÃO abre. Nada sai da sua máquina.
#
# Modo padrão: usa um PostgreSQL 18 descartável com o schema das migrações e dados FALSOS. Precisa de Docker e gpg (vem com o Git).
set -eu
export MSYS_NO_PATHCONV=1

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"
if [ "${1:-}" = "--real" ]; then
  REDE="altas-horas-backup-real"; PG="altas-horas-backup-real-pg"
  arquivo="${ARQUIVO:-}"
  [ -n "$arquivo" ] && [ -s "$arquivo" ] || { echo "informe o artefato: ARQUIVO=altashoras-AAAA-MM-DD.dump.gpg $0 --real" >&2; exit 2; }
  if [ -z "${BACKUP_SENHA:-}" ]; then
    printf 'BACKUP_SENHA (sem eco): ' >&2; stty -echo 2>/dev/null || true; read -r BACKUP_SENHA; stty echo 2>/dev/null || true; echo >&2
  fi
  [ "${#BACKUP_SENHA}" -ge 24 ] || { echo "senha curta demais (o workflow exige 24 ou mais)" >&2; exit 2; }
  pasta="$(mktemp -d)"
  limpar_real() { docker rm -f "$PG" >/dev/null 2>&1 || true; docker network rm "$REDE" >/dev/null 2>&1 || true; rm -rf "$pasta"; }
  trap limpar_real EXIT
  docker info >/dev/null 2>&1 || { echo "Docker não está rodando: abra o Docker Desktop." >&2; exit 2; }
  falhas=0
  ok() { echo "  ✅ $1  $2"; }
  falha() { echo "  ❌ $1  $2"; falhas=$((falhas + 1)); }
  if printf '%s' "senha-errada-do-backup-xxxxxxxx" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0        -d -o "$pasta/errado.dump" "$arquivo" >/dev/null 2>&1; then falha A31a "abriu com a senha errada"; else ok A31a "senha errada não abre o arquivo"; fi
  if printf '%s' "$BACKUP_SENHA" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 -d -o "$pasta/restaurar.dump" "$arquivo" >/dev/null 2>&1      && [ -s "$pasta/restaurar.dump" ]; then ok A31b "descriptografou com a senha ($(wc -c < "$pasta/restaurar.dump" | tr -d ' ') bytes)"; else falha A31b "não descriptografou (senha errada ou arquivo corrompido)"; exit 1; fi
  docker network create "$REDE" >/dev/null
  docker run -d --name "$PG" --network "$REDE" -e POSTGRES_DB=restaurado -e POSTGRES_USER=altashoras     -e POSTGRES_PASSWORD=senha-falsa-so-do-teste postgres:18-alpine >/dev/null
  i=0; until docker exec "$PG" pg_isready -U altashoras -d restaurado >/dev/null 2>&1; do i=$((i + 1)); [ "$i" -le 40 ] || exit 1; sleep 1; done
  sleep 2
  docker exec -i "$PG" pg_restore -U altashoras -d restaurado --no-owner --no-privileges < "$pasta/restaurar.dump" >/dev/null 2>"$pasta/erros.txt" || true
  q() { docker exec "$PG" psql -U altashoras -d restaurado -tAc "$1"; }
  ext="$(q "SELECT extname FROM pg_extension WHERE extname='btree_gist'")"
  con="$(q "SELECT conname FROM pg_constraint WHERE conname='reserva_sem_sobreposicao'")"
  migr="$(q "SELECT count(*) FROM flyway_schema_history WHERE success")"
  esperadas="$(ls backend/src/main/resources/db/migration/V*.sql | wc -l | tr -d ' ')"
  [ "$ext" = "btree_gist" ] && [ "$con" = "reserva_sem_sobreposicao" ] && ok A31c "restaurou: btree_gist e a constraint anti double booking existem" || falha A31c "extension='$ext' constraint='$con' ($(head -c 300 "$pasta/erros.txt"))"
  if [ "$migr" -ge "$esperadas" ]; then ok A31d "flyway_schema_history com $migr migrações com sucesso (o repositório tem $esperadas)"; else falha A31d "flyway_schema_history com $migr migrações (o repositório tem $esperadas)"; fi
  echo
  if [ "$falhas" -eq 0 ]; then echo "Backup real restaurado OK (A31)."; exit 0; else echo "$falhas critério(s) falharam."; exit 1; fi
fi
REDE="altas-horas-backup"
PG="altas-horas-backup-pg"
SENHA_DO_BANCO="senha-falsa-so-do-teste"
BACKUP_SENHA="senha-falsa-do-backup-com-mais-de-24-caracteres"   # no GitHub é o segredo BACKUP_SENHA
pasta="$(mktemp -d)"
falhas=0
ok() { echo "  ✅ $1  $2"; }
falha() { echo "  ❌ $1  $2"; falhas=$((falhas + 1)); }

limpar() { docker rm -f "$PG" >/dev/null 2>&1 || true; docker network rm "$REDE" >/dev/null 2>&1 || true; rm -rf "$pasta"; }
trap limpar EXIT
limpar; pasta="$(mktemp -d)"
docker info >/dev/null 2>&1 || { echo "Docker não está rodando: abra o Docker Desktop." >&2; exit 2; }

docker network create "$REDE" >/dev/null
docker run -d --name "$PG" --network "$REDE" -e POSTGRES_DB=altashoras -e POSTGRES_USER=altashoras \
  -e POSTGRES_PASSWORD="$SENHA_DO_BANCO" postgres:18-alpine >/dev/null
i=0
until docker exec "$PG" pg_isready -U altashoras -d altashoras >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -le 40 ] || { echo "PostgreSQL não ficou pronto" >&2; exit 1; }; sleep 1
done
sleep 2

# Schema real (as migrações em ordem) e um dado falso.
for sql in $(ls backend/src/main/resources/db/migration/V*.sql | sort -V); do
  docker exec -i "$PG" psql -q -v ON_ERROR_STOP=1 -U altashoras -d altashoras < "$sql" >/dev/null
done
docker exec "$PG" psql -q -U altashoras -d altashoras -c \
  "INSERT INTO servico (nome, duracao_min, preco_centavos) VALUES ('Corte Teste', 30, 5000)" >/dev/null

# ---- os mesmos comandos do workflow ----
export DB_URL_BACKUP="postgres://altashoras:$SENHA_DO_BANCO@$PG:5432/altashoras"
docker run --rm --network "$REDE" -e DB_URL_BACKUP postgres:18-alpine \
  sh -c 'pg_dump "$DB_URL_BACKUP" -Fc --no-owner --no-privileges' > "$pasta/altashoras.dump"
[ -s "$pasta/altashoras.dump" ] && ok A20a "pg_dump -Fc gerou $(wc -c < "$pasta/altashoras.dump" | tr -d ' ') bytes" || falha A20a "dump vazio"

printf '%s' "$BACKUP_SENHA" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
  --symmetric --cipher-algo AES256 -o "$pasta/altashoras.dump.gpg" "$pasta/altashoras.dump"
if grep -aq 'PGDMP' "$pasta/altashoras.dump.gpg"; then falha A20b "o arquivo criptografado ainda tem o cabeçalho do dump"; else ok A20b "arquivo criptografado sem o cabeçalho PGDMP legível"; fi
rm -f "$pasta/altashoras.dump"

# ---- restauração (o que o Kevin faria depois de um desastre) ----
if printf '%s' "senha-errada-do-backup-xxxxxxxx" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
     -d -o "$pasta/errado.dump" "$pasta/altashoras.dump.gpg" >/dev/null 2>&1; then
  falha A20c "descriptografou com a senha errada"
else
  ok A20c "senha errada não descriptografa"
fi
printf '%s' "$BACKUP_SENHA" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
  -d -o "$pasta/restaurar.dump" "$pasta/altashoras.dump.gpg" >/dev/null 2>&1
docker exec "$PG" psql -q -U altashoras -d postgres -c "CREATE DATABASE restaurado" >/dev/null
docker exec -i "$PG" pg_restore -U altashoras -d restaurado --no-owner --no-privileges < "$pasta/restaurar.dump"
con="$(docker exec "$PG" psql -U altashoras -d restaurado -tAc "SELECT conname FROM pg_constraint WHERE conname='reserva_sem_sobreposicao'")"
ext="$(docker exec "$PG" psql -U altashoras -d restaurado -tAc "SELECT extname FROM pg_extension WHERE extname='btree_gist'")"
linha="$(docker exec "$PG" psql -U altashoras -d restaurado -tAc "SELECT nome FROM servico")"
[ "$con" = "reserva_sem_sobreposicao" ] && [ "$ext" = "btree_gist" ] && echo "$linha" | grep -qx "Corte Teste" \
  && ok A20d "restaurado num banco novo: dado, btree_gist e a constraint anti double booking voltaram" \
  || falha A20d "restauração incompleta (constraint='$con' ext='$ext' dado='$linha')"

echo
if [ "$falhas" -eq 0 ]; then echo "Backup e restauração OK (A20)."; else echo "$falhas critério(s) falharam."; exit 1; fi
