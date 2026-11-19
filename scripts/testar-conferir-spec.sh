#!/usr/bin/env sh
# Teste de shell do conferir-spec.sh: prova que o parser enxerga um critério sem teste (sai com erro),
# aceita hífen no nome, os três tipos de aspas e as opções --ate e --exceto.
# Roda numa cópia temporária do repositório mínimo; não toca em nada do projeto.
set -eu
raiz="$(cd "$(dirname "$0")/.." && pwd)"
t="$(mktemp -d)"
trap 'rm -rf "$t"' EXIT
mkdir -p "$t/scripts" "$t/docs/sdd" "$t/frontend/src" "$t/frontend/node_modules/x" "$t/backend/src/test"
cp "$raiz/scripts/conferir-spec.sh" "$t/scripts/"
cat > "$t/docs/sdd/fatia-9-exemplo.md" <<'EOF'
| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | x | y | z | `bff-sessao` |
| A2 | x | y | z | `aspas-simples` |
| A3 | x | y | z | `com-crase` |
| A10 | x | y | z | `so-no-node-modules` |
| A11 | x | y | z | `inexistente` |
| V1 | x | y | z | aguarda design |
EOF
printf 'test("A1: ok", () => {});\n' > "$t/frontend/src/bff-sessao.test.ts"
printf "test('A2: ok', () => {});\n" > "$t/frontend/src/aspas-simples.spec.ts"
printf 'test(`A3: ok`, () => {});\n' > "$t/frontend/src/com-crase.test.tsx"
printf 'test("A10: ignorado", () => {});\n' > "$t/frontend/node_modules/x/so-no-node-modules.test.ts"

falhas=0
confere() { # descricao, codigo-esperado, comando...
  desc="$1"; esperado="$2"; shift 2
  set +e; "$@" > "$t/saida.txt" 2>&1; real=$?; set -e
  if [ "$real" -eq "$esperado" ]; then echo "  ok   $desc"; else echo "  FALHOU $desc (saiu $real, esperado $esperado)"; cat "$t/saida.txt"; falhas=$((falhas + 1)); fi
}
c="sh $t/scripts/conferir-spec.sh 9"
confere "critério sem teste derruba com --exigir" 1 $c --exigir
confere "sem --exigir só relata" 0 $c
confere "--ate 3 vê só A1 a A3 (aspas simples, duplas e crase, nome com hífen)" 0 $c --exigir --ate 3
confere "A10 só existe em node_modules e conta como sem teste" 1 $c --exigir --ate 10
confere "--exceto pula faixa e número" 0 $c --exigir --exceto 10-11
grep -q "Critérios: 3 · com teste: 3" "$t/saida.txt" || { echo "  FALHOU contagem do --exceto"; falhas=$((falhas + 1)); }
confere "opção desconhecida é erro de uso" 2 $c --nada
[ "$falhas" -eq 0 ] && echo "testar-conferir-spec: tudo certo" || { echo "testar-conferir-spec: $falhas falha(s)"; exit 1; }
