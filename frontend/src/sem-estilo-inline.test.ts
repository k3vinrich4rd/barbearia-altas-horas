import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, test } from "vitest";

// Fatia 16, A25: a CSP só aceita estilo vindo de arquivo (style-src 'self'; style-src-attr só com o hash aprovado).
// Nenhum componente pode usar `style={...}`: mostrar, esconder e animar é por classe ou atributo.
const RAIZ = join(__dirname);

function arquivos(pasta: string): string[] {
  return readdirSync(pasta).flatMap((nome) => {
    const caminho = join(pasta, nome);
    if (statSync(caminho).isDirectory()) return arquivos(caminho);
    return /\.tsx?$/.test(nome) && !/\.(test|spec)\.tsx?$/.test(nome) ? [caminho] : [];
  });
}

describe("CSP: nenhum estilo inline no código do front (Fatia 16)", () => {
  test("A25: nenhum componente usa style={...} nem o atributo style em texto", () => {
    const comEstilo = arquivos(RAIZ)
      .filter((arq) => /\bstyle=\{|\bstyle="/.test(readFileSync(arq, "utf8")))
      .map((arq) => relative(RAIZ, arq));
    expect(comEstilo).toEqual([]);
  });
});
