// @vitest-environment node
import { mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { describe, expect, test, vi } from "vitest";

// next/font/google só funciona dentro do compilador do Next: aqui entra um falso com a mesma forma
vi.mock("next/font/google", () => ({
  Playfair_Display: () => ({ variable: "fonte-falsa", className: "fonte-falsa" }),
}));
// @ts-expect-error scripts .mjs sem tipos
import { gerarCss } from "../../scripts/gerar-tokens.mjs";
// @ts-expect-error scripts .mjs sem tipos
import { verificar } from "../../scripts/verificar-tokens.mjs";
import RootLayout from "@/app/layout";

const raiz = resolve(__dirname, "../..");
const normalizar = (t: string) => t.replace(/\r\n/g, "\n");

describe("tokens visuais", () => {
  test("A56: nenhum valor visual literal nos CSS, tokens.css é gerado do JSON, tem os dois temas e o <html> nasce escuro", () => {
    expect(verificar(), "CSS com valor literal").toEqual([]);

    const gerado = gerarCss(JSON.parse(readFileSync(resolve(raiz, "design/tokens.json"), "utf8")));
    expect(normalizar(readFileSync(resolve(raiz, "src/estilos/tokens.css"), "utf8"))).toBe(gerado);
    expect(gerado).toContain('[data-theme="escuro"]');
    expect(gerado).toContain('[data-theme="claro"]');
    expect(gerado).toMatch(/--acao-fundo: var\(--branco\)/);
    expect(gerado).toMatch(/--font-display:/);

    // o verificador de verdade reprova valor solto (prova de que ele enxerga)
    const pasta = mkdtempSync(join(tmpdir(), "css-"));
    try {
      mkdirSync(join(pasta, "x"));
      writeFileSync(
        join(pasta, "x", "ruim.module.css"),
        ".a { color: #fff; font-family: Arial; box-shadow: 0 0 1px red; padding: 12px; border-radius: 4px; background: rgb(0,0,0); }\n" +
          ".b { color: white; border: 2px solid black; padding: 1rem; margin: 0.5em; background-color: var(--fundo); }\n" +
          ".d { fill: red; }\n" +
          ".c {\n  --medida-ok: 40rem;\n  max-width: var(--medida-ok);\n  color: var(--texto);\n  border: var(--traco-1) solid var(--borda);\n}\n",
      );
      writeFileSync(join(pasta, "x", "Tela.tsx"), 'export const T = () => <p style={{ color: "red" }}>x</p>;\n');
      const problemas: string[] = verificar(pasta);
      for (const trecho of [
        "cor literal (#)",
        "cor literal (rgb/hsl)",
        "font-family",
        "box-shadow",
        "medida em px",
        "cor nomeada (white)",
        "cor nomeada (black)",
        "cor nomeada (red)",
        "medida em rem/em solta",
        "style= no TSX",
      ])
        expect(problemas.join("\n")).toContain(trecho);
      // o que é token ou variável --medida-* passa: o bloco .c (linhas 4 a 9 do CSS) não tem problema nenhum
      expect(problemas.filter((p) => /ruim\.module\.css:[4-9]/.test(p))).toEqual([]);
    } finally {
      rmSync(pasta, { recursive: true, force: true });
    }

    const html = RootLayout({ children: null }) as unknown as { type: string; props: Record<string, unknown> };
    expect(html.type).toBe("html");
    expect(html.props["data-theme"]).toBe("escuro");
  });
});
