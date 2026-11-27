import { describe, expect, test } from "vitest";
import config from "../../next.config";

describe("cabeçalho do áudio Opus", () => {
  test("o .opus é configurado com Content-Type audio/ogg", async () => {
    const regras = (await config.headers?.()) ?? [];
    const opus = regras.find((r) => /\.opus$/.test(r.source) || r.source.includes("historia.opus"));
    const tipo = opus?.headers.find((h) => h.key.toLowerCase() === "content-type")?.value ?? "";
    expect(tipo).toMatch(/^audio\/ogg\b/i);
  });
});
