// @vitest-environment node
import { mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { describe, expect, test } from "vitest";
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
      writeFileSync(join(pasta, "x", "ruim.module.css"), ".a { color: #fff; font-family: Arial; box-shadow: 0 0 1px red; padding: 12px; border-radius: 4px; background: rgb(0,0,0); }");
      const problemas: string[] = verificar(pasta);
      for (const trecho of ["cor literal (#)", "cor literal (rgb/hsl)", "font-family", "box-shadow", "medida em px"]) expect(problemas.join("\n")).toContain(trecho);
    } finally {
      rmSync(pasta, { recursive: true, force: true });
    }

    const html = RootLayout({ children: null }) as unknown as { type: string; props: Record<string, unknown> };
    expect(html.type).toBe("html");
    expect(html.props["data-theme"]).toBe("escuro");
  });
});
