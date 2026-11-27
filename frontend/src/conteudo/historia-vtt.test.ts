import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";
import { HISTORIA } from "./historia";

function textoDoVtt(vtt: string): string {
  return vtt
    .replace(/^WEBVTT\s*/, "")
    .split(/\n\s*\n/)
    .flatMap((bloco) => {
      const linhas = bloco.split(/\r?\n/);
      const i = linhas.findIndex((l) => l.includes("-->"));
      return i >= 0 ? [linhas.slice(i + 1).join(" ")] : [];
    })
    .filter(Boolean)
    .join(" ")
    .trim();
}

describe("legenda da Trajetória", () => {
  test("VZ-4: o texto das legendas, juntado, é igual ao HISTORIA da tela", () => {
    const vtt = readFileSync(resolve(__dirname, "../../../docs/assets/audio/historia.vtt"), "utf8");
    expect(textoDoVtt(vtt)).toBe(HISTORIA.join(" "));
  });
});
