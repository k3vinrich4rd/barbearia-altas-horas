// Gera src/api/rotas-permitidas.ts a partir do openapi.yaml: todas as rotas do contrato, menos o webhook (E4).
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const aqui = dirname(fileURLToPath(import.meta.url));
const contrato = resolve(aqui, "../../docs/contrato/openapi.yaml");
const destino = resolve(aqui, "../src/api/rotas-permitidas.ts");
const METODOS = ["get", "post", "put", "patch", "delete"];

export function listarRotas(yaml) {
  const spec = parse(yaml);
  const todas = [];
  const publicas = [];
  const comGuest = [];
  for (const [caminho, item] of Object.entries(spec.paths)) {
    if (caminho === "/pagamentos/webhook") continue;
    for (const m of METODOS) {
      const op = item[m];
      if (!op) continue;
      const chave = `${m.toUpperCase()} ${caminho}`;
      todas.push(chave);
      if (Array.isArray(op.security) && op.security.length === 0) publicas.push(chave);
      if (Array.isArray(op.security) && op.security.some((s) => "guestToken" in s)) comGuest.push(chave);
    }
  }
  return { todas: todas.sort(), publicas: publicas.sort(), comGuest: comGuest.sort() };
}

function lista(nome, itens, comentario) {
  const linhas = itens.map((r) => `  "${r}",`);
  return ["// " + comentario, "export const " + nome + ": readonly string[] = [", ...linhas, "];", ""].join("\n");
}

export function gerarArquivo(yaml) {
  const { todas, publicas, comGuest } = listarRotas(yaml);
  return [
    "// GERADO por scripts/gerar-rotas.mjs a partir de docs/contrato/openapi.yaml. NÃO EDITAR.",
    lista("ROTAS_PERMITIDAS", todas, "Rotas que o BFF aceita (método + caminho do contrato). O webhook do gateway nunca entra (E4)."),
    lista("ROTAS_PUBLICAS", publicas, "Rotas sem segurança no contrato (security: []): o BFF não manda token."),
    lista("ROTAS_COM_TOKEN_GUEST", comGuest, "Rotas que aceitam X-Guest-Token (visitante)."),
  ].join("\n");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  writeFileSync(destino, gerarArquivo(readFileSync(contrato, "utf8")));
  console.log("gerado src/api/rotas-permitidas.ts");
}
