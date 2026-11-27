#!/usr/bin/env sh
# Teste de shell do conferir-spec.sh: prova que o parser enxerga um critério sem teste (sai com erro),
# aceita hífen no nome, os três tipos de aspas, as opções --ate e --exceto e os rótulos de tela (MT/AF/FF).
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
| A16 | x | y | z | `com-extensao.test.ts` (Vitest) |
| A12 | x | y | z | `so-em-comentario` |
| A13 | x | y | z | `titulo-na-linha-de-baixo` |
| A14 | x | y | z | `com-it` |
| A15 | x | y | z | `numero-em-string-solta` |
| V1 | x | y | z | aguarda design |
EOF
printf 'test("A1: ok", () => {});\n' > "$t/frontend/src/bff-sessao.test.ts"
printf "test('A2: ok', () => {});\n" > "$t/frontend/src/aspas-simples.spec.ts"
printf 'test(`A3: ok`, () => {});\n' > "$t/frontend/src/com-crase.test.tsx"
cat > "$t/frontend/src/so-em-comentario.test.ts" <<'EOF'
// test("A12: so no comentario", () => {});
/* it("A12: bloco", () => {}); */
/**
 * test("A12: javadoc")
 */
test("outro nome", () => {});
EOF
printf 'test(
  "A13: titulo quebrado pelo Prettier",
  () => {},
);
' > "$t/frontend/src/titulo-na-linha-de-baixo.test.ts"
printf 'it("A14: com it", () => {});
' > "$t/frontend/src/com-it.test.ts"
printf 'const texto = "A15: so uma string";
' > "$t/frontend/src/numero-em-string-solta.test.ts"
printf 'test("A16: nome com extensao na coluna", () => {});
' > "$t/frontend/src/com-extensao.test.ts"
printf 'test("A10: ignorado", () => {});\n' > "$t/frontend/node_modules/x/so-no-node-modules.test.ts"

# critério substituído por outra fatia: vale se o critério da outra fatia tem teste
printf '| A1 | x | y | z | `bff-sessao` |\n| A2 | x | y | z | `inexistente` |\n' > "$t/docs/sdd/fatia-8-nova.md"
printf '| A1 | x | y | z | `antigo` · substituído pela Fatia 8 (A1) |\n' > "$t/docs/sdd/fatia-6-troca.md"
printf '| A1 | x | y | z | `antigo` · substituído pela Fatia 8 (A2) |\n' > "$t/docs/sdd/fatia-5-troca.md"

# critérios de tela (MT/AF/FF) e títulos com vários rótulos ("FF-3, FF-4 e FF-9: ...")
printf 'test("A2 e A1: dois rotulos", () => {});\ntest("FF-3, FF-4 e FF-9: lista", () => {});\nit("AF-10 (E18): com parenteses", () => {});\ntest("MT-1 e MT-1b: com letra", () => {});\ntest("MT-4c: so a letra", () => {});\n' > "$t/frontend/src/varios-rotulos.test.tsx"
printf '| A1 | x | y | z | `varios-rotulos.test.tsx` |\n| FF-4 | x | y | z | `varios-rotulos.test.tsx` |\n| FF-9 | x | y | z | `varios-rotulos.test.tsx` |\n| AF-10 | x | y | z | `varios-rotulos.test.tsx` |\n| MT-1b | x | y | z | `varios-rotulos.test.tsx` |\n' > "$t/docs/sdd/fatia-10-telas.md"
printf '| FF-12 | x | y | z | `varios-rotulos.test.tsx` |\n' > "$t/docs/sdd/fatia-11-telas.md"
printf '| MT-4 | x | y | z | `varios-rotulos.test.tsx` |\n' > "$t/docs/sdd/fatia-12-telas.md"

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
confere "número só em comentário não conta (A12)" 1 $c --exigir --exceto 1-11,13-15
confere "título na linha de baixo e it( contam (A13 e A14)" 0 $c --exigir --exceto 1-12,15
confere "número em string solta, fora de test( e DisplayName, não conta (A15)" 1 $c --exigir --exceto 1-14
confere "nome com a extensão na coluna Teste (Fatia 14) acha o arquivo (A16)" 0 $c --exigir --exceto 1-15
confere "--exceto pula faixa e número" 0 $c --exigir --exceto 10-16
grep -q "Critérios: 3 · com teste: 3" "$t/saida.txt" || { echo "  FALHOU contagem do --exceto"; falhas=$((falhas + 1)); }
confere "substituído por critério da outra fatia COM teste passa" 0 sh "$t/scripts/conferir-spec.sh" 6 --exigir
confere "substituído por critério da outra fatia SEM teste derruba" 1 sh "$t/scripts/conferir-spec.sh" 5 --exigir
confere "rótulos MT/AF/FF e título com vários rótulos são achados" 0 sh "$t/scripts/conferir-spec.sh" 10 --exigir
grep -q "Critérios: 5 · com teste: 5" "$t/saida.txt" || { echo "  FALHOU contagem dos rótulos de tela"; falhas=$((falhas + 1)); }
confere "rótulo de tela sem teste derruba (FF-12), e --exceto não o pula" 1 sh "$t/scripts/conferir-spec.sh" 11 --exigir --exceto 1-99
confere "MT-4 não é achado dentro de MT-4c" 1 sh "$t/scripts/conferir-spec.sh" 12 --exigir
confere "opção desconhecida é erro de uso" 2 $c --nada
[ "$falhas" -eq 0 ] && echo "testar-conferir-spec: tudo certo" || { echo "testar-conferir-spec: $falhas falha(s)"; exit 1; }
