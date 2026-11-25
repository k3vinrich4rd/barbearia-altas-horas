// @vitest-environment node
// Fatia 14D, A62: depois das melhorias de fluidez, a home e /agendar seguem com o MESMO orçamento do Lighthouse móvel da
// Fatia 12 (A24). A medição roda no CI (Lighthouse CI contra o build); aqui o teste impede afrouxar o orçamento.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";

const config = JSON.parse(readFileSync(resolve(__dirname, "../lighthouserc.json"), "utf8")) as {
  ci: {
    collect: { url: string[]; settings: { formFactor: string } };
    assert: { assertions: Record<string, [string, Record<string, number>]> };
  };
};

describe("Lighthouse depois da 14D", () => {
  test("A62: home e /agendar no celular com Desempenho ≥ 90, Acessibilidade ≥ 95, LCP ≤ 2,7 s, CLS ≤ 0,1 e TBT ≤ 200 ms (os orçamentos da Fatia 12)", () => {
    const { collect, assert } = config.ci;
    expect(collect.url.map((u) => new URL(u).pathname)).toEqual(expect.arrayContaining(["/", "/agendar"]));
    expect(collect.settings.formFactor).toBe("mobile");
    const a = assert.assertions;
    expect(a["categories:performance"][1].minScore).toBeGreaterThanOrEqual(0.9);
    expect(a["categories:accessibility"][1].minScore).toBeGreaterThanOrEqual(0.95);
    expect(a["largest-contentful-paint"][1].maxNumericValue).toBeLessThanOrEqual(2700);
    expect(a["cumulative-layout-shift"][1].maxNumericValue).toBeLessThanOrEqual(0.1);
    expect(a["total-blocking-time"][1].maxNumericValue).toBeLessThanOrEqual(200);
    for (const chave of Object.keys(a)) expect(a[chave][0], chave).toBe("error");
  });
});
