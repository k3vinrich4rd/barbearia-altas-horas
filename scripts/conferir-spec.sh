#!/usr/bin/env sh
# "Eval" determinístico do SDD: confere se cada critério de aceite (A1, A2...) da spec de uma fatia tem um
# teste com o número no @DisplayName, numa das classes que a própria spec cita na coluna "Teste".
# É a checagem que impede a IA (ou o humano) de dizer "pronto" com critério sem teste.
#
# Uso: scripts/conferir-spec.sh 1            (relata)
#      scripts/conferir-spec.sh 1 --exigir   (sai com erro se faltar algum; usado no CI para fatias implementadas)
set -eu

[ $# -ge 1 ] || { echo "uso: $0 <numero-da-fatia> [--exigir]" >&2; exit 2; }
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"
spec="$(ls docs/sdd/fatia-"$1"-*.md 2>/dev/null | head -1)"
[ -n "$spec" ] || { echo "spec da fatia $1 não encontrada em docs/sdd/" >&2; exit 2; }

total=0
faltando=0
echo "Spec: $spec"
# Linhas de critério: | A7 | dado | quando | então | `ClasseDeTeste` |
grep -E '^\| A[0-9]+ \|' "$spec" > /tmp/criterios.$$ || true
while IFS= read -r linha; do
  id="$(printf '%s' "$linha" | cut -d'|' -f2 | tr -d ' ')"
  classes="$(printf '%s' "$linha" | awk -F'|' '{print $(NF-1)}' | grep -oE '`[A-Za-z0-9_]+`' | tr -d '`' || true)"
  total=$((total + 1))
  achou=""
  for classe in $classes; do
    arquivo="$(find backend/src/test -name "$classe.java" 2>/dev/null | head -1)"
    [ -n "$arquivo" ] || continue
    if grep -qE "\"$id[: ]" "$arquivo"; then achou="$classe"; break; fi
  done
  if [ -n "$achou" ]; then
    echo "  ✅ $id  $achou"
  else
    echo "  ❌ $id  sem teste com \"$id:\" em: $(echo $classes | tr '\n' ' ')"
    faltando=$((faltando + 1))
  fi
done < /tmp/criterios.$$
rm -f /tmp/criterios.$$

echo "Critérios: $total · com teste: $((total - faltando)) · sem teste: $faltando"
if [ "${2:-}" = "--exigir" ] && [ "$faltando" -gt 0 ]; then exit 1; fi
