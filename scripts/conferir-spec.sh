#!/usr/bin/env sh
# "Eval" determinístico do SDD: confere se cada critério de aceite (A1, A2...) da spec de uma fatia tem um
# teste com o número no nome, num dos arquivos que a própria spec cita na coluna "Teste".
# É a checagem que impede a IA (ou o humano) de dizer "pronto" com critério sem teste.
#
# Onde procura (Fatia 7 em diante, spec do front):
#   backend/src/test/**/<Nome>.java                       (@DisplayName("A7: ..."))
#   frontend/**/<nome>.test.ts, .test.tsx, .spec.ts        (test("A7: ...") ou it("A7: ..."), aspas simples, duplas ou crase)
#   (ignora node_modules, .next e playwright-report)
# O número só vale DENTRO de @DisplayName("A7..., test("A7... ou it("A7... (e @ParameterizedTest(name = "A7..., que é
# o nome do teste parametrizado do Java), e nunca em comentário (// ou /* */): um
# comentário com o número não prova nada. O título pode estar na linha de baixo (Prettier quebra a chamada).
#
# Uso: scripts/conferir-spec.sh 1                        (relata)
#      scripts/conferir-spec.sh 1 --exigir               (sai com erro se faltar algum; usado no CI)
#      scripts/conferir-spec.sh 7 --exigir --ate 43      (só os critérios A1 a A43)
#      scripts/conferir-spec.sh 7 --exigir --exceto 44-53,59   (pula faixas e números; a 7A não cobre o painel)
# A coluna "Teste" pode trazer o nome com a extensão (`pagamento-prorrogacao.test.tsx`, Fatia 14): a extensão é tirada antes de procurar o arquivo.
# Critério trocado por outra fatia: a linha leva "substituído pela Fatia N (A<m>)" e vale se o A<m> da fatia N tem teste.
# Só linhas "| A<n> |" contam; os critérios V# (aguarda design) ficam de fora de propósito.
set -eu

uso() { echo "uso: $0 <numero-da-fatia> [--exigir] [--ate N] [--exceto 44-53,59]" >&2; exit 2; }
[ $# -ge 1 ] || uso
fatia="$1"
shift
exigir=0
ate=""
exceto=""
while [ $# -gt 0 ]; do
  case "$1" in
    --exigir) exigir=1 ;;
    --ate) [ $# -ge 2 ] || uso; ate="$2"; shift ;;
    --exceto) [ $# -ge 2 ] || uso; exceto="$2"; shift ;;
    *) uso ;;
  esac
  shift
done
case "$ate" in ''|*[!0-9]*) [ -z "$ate" ] || uso ;; esac

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"
spec="$(ls docs/sdd/fatia-"$fatia"-*.md 2>/dev/null | head -1)"
[ -n "$spec" ] || { echo "spec da fatia $fatia não encontrada em docs/sdd/" >&2; exit 2; }

# Devolve 0 se o número está na lista "44-53,59".
excluido() {
  n="$1"
  [ -n "$exceto" ] || return 1
  for faixa in $(printf '%s' "$exceto" | tr ',' ' '); do
    case "$faixa" in
      *-*) de="${faixa%-*}"; ateq="${faixa#*-}"; [ "$n" -ge "$de" ] && [ "$n" -le "$ateq" ] && return 0 ;;
      *) [ "$n" -eq "$faixa" ] && return 0 ;;
    esac
  done
  return 1
}

# Procura o arquivo de teste de um nome da coluna "Teste" (Java no back, .test.ts/.test.tsx/.spec.ts no front).
achar_arquivo() {
  nome="$1"
  find backend/src/test -name "$nome.java" 2>/dev/null | head -1
  [ -d frontend ] && find frontend \
    \( -name node_modules -o -name .next -o -name playwright-report -o -name test-results \) -prune -o \
    -type f \( -name "$nome.test.ts" -o -name "$nome.test.tsx" -o -name "$nome.spec.ts" \) -print 2>/dev/null | head -1
  return 0
}

# Devolve 0 se o arquivo declara o critério $2 num @DisplayName, test( ou it(, ignorando comentários.
tem_criterio() {
  arq="$1"
  id="$2"
  sed -E -e 's#^[[:space:]]*//.*$##' -e 's#^[[:space:]]*/?\*.*$##' -e 's#/\*[^*]*\*/##g' -e 's#[[:space:]]//[[:space:]].*$##' "$arq" \
    | tr '\n' ' ' \
    | grep -qE "(@DisplayName\(|@ParameterizedTest\([[:space:]]*name[[:space:]]*=[[:space:]]*|(^|[^A-Za-z0-9_.])(test|it)\()[[:space:]]*[\"'\`]$id[: ]"
}

total=0
faltando=0
tmp="$(mktemp)"
cache="$(mktemp -d)"
trap 'rm -rf "$tmp" "$cache"' EXIT
echo "Spec: $spec"
# Linhas de critério: | A7 | dado | quando | então | `nome-do-teste` |
grep -E '^\| A[0-9]+ \|' "$spec" > "$tmp" || true
while IFS= read -r linha; do
  id="$(printf '%s' "$linha" | cut -d'|' -f2 | tr -d ' ')"
  numero="${id#A}"
  if [ -n "$ate" ] && [ "$numero" -gt "$ate" ]; then continue; fi
  if excluido "$numero"; then continue; fi
  # Critério trocado por outra fatia: a marca "substituído pela Fatia N (A<m>)" na linha vale se o A<m> da fatia N tem teste.
  subst="$(printf '%s' "$linha" | sed -nE 's/.*substituído pela Fatia ([0-9]+) \((A[0-9]+)\).*/\1 \2/p')"
  if [ -n "$subst" ]; then
    total=$((total + 1))
    set -- $subst
    # a fatia que substitui é conferida uma vez só por execução (resultado guardado numa pasta temporária)
    [ -f "$cache/f$1" ] || sh "$raiz/scripts/conferir-spec.sh" "$1" > "$cache/f$1" 2>/dev/null || true
    if grep -q "✅ $2 " "$cache/f$1"; then
      echo "  ↪ $id  substituído pela Fatia $1 ($2, com teste)"
    else
      echo "  ❌ $id  marcado como substituído pela Fatia $1 ($2), mas o $2 dela não tem teste"
      faltando=$((faltando + 1))
    fi
    continue
  fi
  classes="$(printf '%s' "$linha" | awk -F'|' '{print $(NF-1)}' | grep -oE '`[A-Za-z0-9_.-]+`' | tr -d '`' | sed -E 's/\.(test\.tsx|test\.ts|spec\.ts)$//' || true)"
  total=$((total + 1))
  achou=""
  for classe in $classes; do
    for arquivo in $(achar_arquivo "$classe"); do
      if tem_criterio "$arquivo" "$id"; then achou="$classe"; break; fi
    done
    [ -z "$achou" ] || break
  done
  if [ -n "$achou" ]; then
    echo "  ✅ $id  $achou"
  else
    echo "  ❌ $id  sem teste com \"$id:\" em: $(echo $classes | tr '\n' ' ')"
    faltando=$((faltando + 1))
  fi
done < "$tmp"

echo "Critérios: $total · com teste: $((total - faltando)) · sem teste: $faltando"
if [ "$exigir" -eq 1 ] && [ "$faltando" -gt 0 ]; then exit 1; fi
exit 0
