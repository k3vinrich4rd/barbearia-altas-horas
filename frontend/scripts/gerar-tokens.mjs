// Gera src/estilos/tokens.css a partir de design/tokens.json (design system do Kevin).
// Nenhuma decisão visual mora aqui: só a tradução de nomes. Rodar: npm run gerar:tokens
// Com --conferir, não grava: sai com erro se o arquivo versionado estiver diferente do que seria gerado.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const aqui = dirname(fileURLToPath(import.meta.url));
const origem = resolve(aqui, "../design/tokens.json");
const destino = resolve(aqui, "../src/estilos/tokens.css");

export function gerarCss(tokens) {
  const referencia = (valor) => valor.replace(/\{([a-z0-9-]+)\}/g, (_, nome) => `var(--${nome})`);
  const base = [];
  const escuro = [];
  const claro = [];
  for (const t of tokens.color.tokens) {
    if (typeof t.value === "string") {
      base.push(`  --${t.name}: ${t.value};`);
    } else {
      escuro.push(`  --${t.name}: ${referencia(t.value.escuro)};`);
      claro.push(`  --${t.name}: ${referencia(t.value.claro)};`);
    }
  }
  for (const grupo of ["spacing", "radius", "xadrez", "traco"]) {
    for (const t of tokens[grupo].tokens) base.push(`  --${t.name}: ${t.value};`);
  }
  for (const [nome, valor] of Object.entries(tokens.type.families)) base.push(`  --font-${nome}: ${valor};`);
  const estilos = [];
  for (const g of tokens.type.groups) for (const e of g.styles) estilos.push({ ...e, familia: e.family ?? g.family });
  for (const e of estilos) {
    base.push(`  --tipo-${e.name}-familia: var(--font-${e.familia});`);
    base.push(`  --tipo-${e.name}-tamanho: ${e.fontSize};`);
    base.push(`  --tipo-${e.name}-altura: ${e.lineHeight};`);
    base.push(`  --tipo-${e.name}-peso: ${e.fontWeight};`);
    base.push(`  --tipo-${e.name}-espacamento: ${e.letterSpacing};`);
  }
  const classes = estilos.map(
    (e) =>
      `.tipo-${e.name} {\n  font-family: var(--tipo-${e.name}-familia);\n  font-size: var(--tipo-${e.name}-tamanho);\n  line-height: var(--tipo-${e.name}-altura);\n  font-weight: var(--tipo-${e.name}-peso);\n  letter-spacing: var(--tipo-${e.name}-espacamento);\n}`,
  );
  return [
    `/* GERADO por scripts/gerar-tokens.mjs a partir de design/tokens.json (sync ${tokens.meta.synced}, ${tokens.meta.ref}). NÃO EDITAR. */`,
    `:root {\n${base.join("\n")}\n}`,
    `:root,\n[data-theme="escuro"] {\n  color-scheme: dark;\n${escuro.join("\n")}\n}`,
    `[data-theme="claro"] {\n  color-scheme: light;\n${claro.join("\n")}\n}`,
    classes.join("\n"),
    "",
  ].join("\n\n");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const css = gerarCss(JSON.parse(readFileSync(origem, "utf8")));
  if (process.argv.includes("--conferir")) {
    let atual = "";
    try {
      atual = readFileSync(destino, "utf8");
    } catch {
      atual = "";
    }
    if (atual.replace(/\r\n/g, "\n") !== css) {
      console.error("tokens.css está diferente do gerado: rode npm run gerar:tokens");
      process.exit(1);
    }
    console.log("tokens.css confere com design/tokens.json");
  } else {
    writeFileSync(destino, css);
    console.log("gerado src/estilos/tokens.css");
  }
}
