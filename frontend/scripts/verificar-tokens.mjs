// Verificador de tokens (A56): nenhum CSS de src usa valor visual literal fora de tokens.css.
// Proíbe: cor literal (#, rgb(, hsl(), font-family fora de var(--font-*), box-shadow e medida em px.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function listarCss(dir) {
  const achados = [];
  for (const nome of readdirSync(dir)) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados.push(...listarCss(caminho));
    else if (nome.endsWith(".css") && nome !== "tokens.css") achados.push(caminho);
  }
  return achados;
}

export function verificar(dir = join(raiz, "src")) {
  const problemas = [];
  for (const arquivo of listarCss(dir)) {
    const texto = readFileSync(arquivo, "utf8").replace(/\/\*[\s\S]*?\*\//g, (c) => c.replace(/[^\n]/g, " "));
    texto.split("\n").forEach((linha, i) => {
      const onde = `${relative(raiz, arquivo)}:${i + 1}`;
      if (/#[0-9a-fA-F]{3,8}\b/.test(linha)) problemas.push(`${onde} cor literal (#)`);
      if (/\b(rgb|rgba|hsl|hsla)\(/i.test(linha)) problemas.push(`${onde} cor literal (rgb/hsl)`);
      if (/font-family\s*:/.test(linha) && !/font-family\s*:\s*var\(--(font|tipo)-[a-z-]+\)\s*;?/.test(linha))
        problemas.push(`${onde} font-family fora de var(--font-*)`);
      if (/box-shadow\s*:/.test(linha)) problemas.push(`${onde} box-shadow (o design system não tem sombra)`);
      if (/(^|[^\w.-])-?\d*\.?\d+px\b/.test(linha))
        problemas.push(`${onde} medida em px solta (use var(--space-*), var(--radius-*) ou var(--traco-*))`);
    });
  }
  return problemas;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problemas = verificar();
  if (problemas.length) {
    console.error(problemas.join("\n"));
    process.exit(1);
  }
  console.log("verificador de tokens: nenhum valor visual literal nos CSS");
}
