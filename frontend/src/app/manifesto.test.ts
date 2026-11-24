import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";
import manifest from "./manifest";

describe("manifesto do app (Fatia 13)", () => {
  test("A39: GET /manifest.webmanifest tem start_url /painel, display standalone, ícones 192 e 512 e um maskable", () => {
    const m = manifest();
    expect(m.name).toBe("Barbearia Altas Horas");
    expect(m.short_name).toBe("Altas Horas");
    expect(m.start_url).toBe("/painel");
    expect(m.scope).toBe("/");
    expect(m.display).toBe("standalone");
    const icones = m.icons ?? [];
    expect(icones.map((i) => i.sizes)).toEqual(expect.arrayContaining(["192x192", "512x512"]));
    expect(icones.some((i) => i.purpose === "maskable")).toBe(true);
    // todos os ícones saem de docs/assets (copiados no build), nunca de fora
    for (const icone of icones) expect(icone.src).toMatch(/^\/assets\/[\w.-]+\.png$/);
  });

  test("A39: as cores do manifesto são as do design system (fundo preto da marca)", async () => {
    const tokens = readFileSync(resolve(__dirname, "../../design/tokens.json"), "utf-8");
    expect(tokens).toMatch(/#0A0A0A/i);
    const m = manifest();
    expect(m.background_color?.toUpperCase()).toBe("#0A0A0A");
    expect(m.theme_color?.toUpperCase()).toBe("#0A0A0A");
  });

  test("A39: o manifesto serve sem login (é público) e o service worker está no escopo da raiz", async () => {
    const { exigeLogin } = await import("@/bff/cabecalhos");
    expect(exigeLogin("/manifest.webmanifest")).toBe(false);
    expect(exigeLogin("/sw.js")).toBe(false);
  });
});
