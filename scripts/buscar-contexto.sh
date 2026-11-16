#!/usr/bin/env sh
# Recupera o contexto de um identificador do projeto (regra R15, decisão D40, critério A7, código de erro...)
# em todos os documentos e no código, com arquivo e linha. É o "RAG leve" do projeto: busca exata por
# identificador estável, mais precisa que busca vetorial para ~40 documentos (docs/sdd/README.md).
#
# Uso: scripts/buscar-contexto.sh R15
#      scripts/buscar-contexto.sh HORARIO_INDISPONIVEL
set -eu

if [ $# -ne 1 ]; then
  echo "uso: $0 <identificador>   (ex.: R15, D40, A7, HORARIO_INDISPONIVEL)" >&2
  exit 2
fi

id="$1"
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

# Identificador curto (R1, D4, A7) só casa como palavra inteira: R1 não traz R15.
padrao="(^|[^A-Za-z0-9_])${id}([^A-Za-z0-9_]|$)"

echo "== ${id} =="
for area in "docs/contexto.md:regra ou hipótese" "docs/planejamento/decisoes.md:decisão" "docs/sdd:spec" \
            "docs/contrato:contrato" "docs/adr:ADR" "docs/dominio:domínio" "docs/arquitetura:arquitetura" \
            "backend/src:código e testes"; do
  caminho="${area%%:*}"
  rotulo="${area#*:}"
  [ -e "$caminho" ] || continue
  achados="$(grep -rnE "$padrao" "$caminho" 2>/dev/null | cut -c1-220 || true)"
  if [ -n "$achados" ]; then
    echo
    echo "--- ${rotulo} (${caminho})"
    echo "$achados"
  fi
done
