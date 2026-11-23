// @vitest-environment node
// Fatia 12, A27: o CSS das seções novas usa só tokens de design/tokens.json; nenhuma cor nova.
import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { describe, expect, test } from "vitest";
// @ts-expect-error scripts .mjs sem tipos
import { gerarCss } from "../../scripts/gerar-tokens.mjs";
// @ts-expect-error scripts .mjs sem tipos
import { verificar } from "../../scripts/verificar-tokens.mjs";

const raiz = resolve(__dirname, "../..");
const lf = (t: string) => t.replace(/\r\n/g, "\n");
const css = (arquivo: string) =>
  lf(readFileSync(join(raiz, "src/estilos", arquivo), "utf8")).replace(/\/\*[\s\S]*?\*\//g, "");

/** Seletores das seções novas da Fatia 12 (galeria, faixa do herói, barbeiro, alcance vazio, barra fixa, agendar). */
const SECOES_NOVAS = [
  ".galeria-",
  ".faixa-passos",
  ".grade-barbeiros",
  ".cartao-barbeiro",
  ".foto-barbeiro",
  ".estado-vazio",
  ".barra-agendar",
  ".dias-",
  ".dia-",
  ".chip-",
  ".horarios-chips",
  ".resumo-fixo",
  ".barbeiros-cartoes",
];

function blocosDasSecoesNovas(): { seletor: string; corpo: string }[] {
  const achados: { seletor: string; corpo: string }[] = [];
  for (const arquivo of ["globais.css", "navegacao.css"]) {
    for (const m of css(arquivo).matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      const seletor = m[1].trim();
      if (SECOES_NOVAS.some((s) => seletor.split(",").some((parte) => parte.trim().startsWith(s))))
        achados.push({ seletor, corpo: m[2] });
    }
  }
  return achados;
}

describe("tokens visuais da Fatia 12", () => {
  test("A27: o CSS das seções novas só usa tokens (nenhuma cor, medida ou fonte solta) e tokens.css continua gerado do JSON", () => {
    expect(verificar(), "valor visual literal nos CSS").toEqual([]);

    // nenhuma cor nova: tokens.css é exatamente o que o tokens.json gera (sem edição à mão)
    const gerado = gerarCss(JSON.parse(readFileSync(join(raiz, "design/tokens.json"), "utf8")));
    expect(lf(readFileSync(join(raiz, "src/estilos/tokens.css"), "utf8"))).toBe(gerado);

    // toda variável usada existe: nos tokens ou numa propriedade --nome declarada em algum CSS
    const todos = [...readdirSync(join(raiz, "src/estilos"))].filter((f) => f.endsWith(".css"));
    const definidas = new Set<string>();
    for (const arquivo of todos) for (const m of css(arquivo).matchAll(/(--[a-z0-9-]+)\s*:/g)) definidas.add(m[1]);
    const semDefinicao: string[] = [];
    for (const arquivo of todos)
      for (const m of css(arquivo).matchAll(/var\((--[a-z0-9-]+)/g))
        // --fonte-playfair é injetada em tempo de build pelo next/font (layout.tsx), não mora em CSS
        if (!definidas.has(m[1]) && m[1] !== "--fonte-playfair") semDefinicao.push(`${arquivo}: ${m[1]}`);
    expect(semDefinicao).toEqual([]);

    // as seções novas existem no CSS e as cores delas vêm só de var(--token)
    const blocos = blocosDasSecoesNovas();
    expect(blocos.length).toBeGreaterThan(8);
    const CORES =
      /(^|[;\s])(color|background|background-color|border(-[a-z]+)*|outline(-[a-z]+)*|fill|stroke)\s*:\s*([^;]*)/g;
    const PERMITIDAS = new Set([
      "transparent",
      "currentcolor",
      "inherit",
      "none",
      "solid",
      "dashed",
      "dotted",
      "initial",
    ]);
    for (const { seletor, corpo } of blocos) {
      expect(corpo, seletor).not.toMatch(/#[0-9a-fA-F]{3,8}\b|\b(rgb|rgba|hsl|hsla)\(/);
      for (const m of corpo.matchAll(CORES)) {
        const valor = m[m.length - 1].replace(/var\([^)]*\)/g, " ").replace(/calc\([^)]*\)/g, " ");
        const palavras = (valor.toLowerCase().match(/[a-z]+/g) ?? []).filter((p) => !PERMITIDAS.has(p));
        expect(palavras, `${seletor} { ${m[0].trim()} }`).toEqual([]);
      }
    }
  });
});
