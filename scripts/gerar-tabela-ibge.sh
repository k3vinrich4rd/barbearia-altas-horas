#!/usr/bin/env bash
# Gera backend/src/main/resources/ibge/municipios.csv a partir do serviço público de localidades do IBGE (sem chave, sem
# custo). Rode só quando o IBGE mudar a divisão (Fatia 11, E16, D-8): a aplicação NUNCA chama o IBGE em tempo de execução.
# Depois de rodar, confira o diff na revisão e atualize os números esperados em TabelaIbgeTest se a contagem mudou.
# Uso: bash scripts/gerar-tabela-ibge.sh   (precisa de curl e node)
set -euo pipefail
cd "$(dirname "$0")/.."

DESTINO="backend/src/main/resources/ibge/municipios.csv"
URL="https://servicodados.ibge.gov.br/api/v1/localidades/municipios"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

curl -fsS --max-time 120 "$URL" -o "$TMP"

IBGE_JSON="$TMP" IBGE_DESTINO="$DESTINO" IBGE_URL="$URL" node -e '
const fs = require("fs");
const lista = JSON.parse(fs.readFileSync(process.env.IBGE_JSON, "utf8"));
const linhas = [];
const regioes = new Set();
const ufs = new Set();
for (const m of lista) {
  const ri = m["regiao-imediata"];
  if (!ri) { throw new Error("município sem região imediata: " + m.id); }
  const uf = ri["regiao-intermediaria"].UF.sigla;
  regioes.add(ri.id);
  ufs.add(uf);
  linhas.push([m.id, m.nome, uf, ri.id, ri.nome]);
}
linhas.sort((a, b) => a[0] - b[0]);
const hoje = new Date().toISOString().slice(0, 10);
const cab = `# fonte=${process.env.IBGE_URL} data=${hoje} municipios=${linhas.length} regioesImediatas=${regioes.size} ufs=${ufs.size}`;
const corpo = ["codigo;municipio;uf;regiaoImediataCodigo;regiaoImediataNome", ...linhas.map(l => l.join(";"))];
fs.writeFileSync(process.env.IBGE_DESTINO, [cab, ...corpo].join("\n") + "\n", "utf8");
console.log(cab);
'
