// @vitest-environment node
// Fatia 12, A24: o orçamento do Lighthouse móvel está no lighthouserc.json e é o da spec. A medição em si roda no CI
// (Lighthouse CI contra o build de produção) e a saída real fica registrada na PR; aqui o teste impede que alguém
// afrouxe o orçamento sem passar pela spec.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";

const config = JSON.parse(readFileSync(resolve(__dirname, "../lighthouserc.json"), "utf8")) as {
  ci: {
    collect: { url: string[]; settings: { formFactor: string; throttlingMethod: string }; numberOfRuns: number };
    assert: { assertions: Record<string, [string, Record<string, number>]> };
  };
};

describe("orçamento do Lighthouse (Fatia 12)", () => {
  test("A24: home e /agendar no celular (CPU 4x e Slow 4G simulados), Desempenho ≥ 90, Acessibilidade ≥ 95, LCP ≤ 2,5 s, CLS ≤ 0,1 e TBT ≤ 200 ms", () => {
    const { collect, assert } = config.ci;
    expect(collect.url.map((u) => new URL(u).pathname)).toEqual(["/", "/agendar"]);
    expect(collect.settings.formFactor).toBe("mobile");
    expect(collect.settings.throttlingMethod).toBe("simulate"); // o padrão do Lighthouse móvel: CPU 4x e Slow 4G
    expect(collect.numberOfRuns).toBeGreaterThanOrEqual(3);

    const a = assert.assertions;
    expect(a["categories:performance"]).toEqual(["error", { minScore: 0.9 }]);
    expect(a["categories:accessibility"]).toEqual(["error", { minScore: 0.95 }]);
    expect(a["largest-contentful-paint"]).toEqual(["error", { maxNumericValue: 2500 }]);
    expect(a["cumulative-layout-shift"]).toEqual(["error", { maxNumericValue: 0.1 }]);
    expect(a["total-blocking-time"]).toEqual(["error", { maxNumericValue: 200 }]);
  });
});
