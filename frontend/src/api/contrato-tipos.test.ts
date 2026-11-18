// @vitest-environment node
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { describe, expect, test } from "vitest";
import { parse } from "yaml";
// @ts-expect-error script .mjs sem tipos
import { gerarArquivo } from "../../scripts/gerar-rotas.mjs";
import { ROTAS_PERMITIDAS } from "./rotas-permitidas";

const raiz = resolve(__dirname, "../..");
const contrato = resolve(raiz, "../docs/contrato/openapi.yaml");
const normalizar = (t: string) => t.replace(/\r\n/g, "\n");

describe("contrato × cliente", () => {
  test("A11: schema.d.ts e a lista de rotas permitidas batem com o openapi.yaml (contrato mudou sem regenerar → falha)", () => {
    const pasta = mkdtempSync(join(tmpdir(), "tipos-"));
    try {
      const saida = join(pasta, "schema.d.ts");
      const cli = resolve(raiz, "node_modules/openapi-typescript/bin/cli.js");
      execFileSync(process.execPath, [cli, contrato, "-o", saida], { stdio: "pipe" });
      expect(normalizar(readFileSync(saida, "utf8"))).toBe(normalizar(readFileSync(resolve(raiz, "src/api/schema.d.ts"), "utf8")));
    } finally {
      rmSync(pasta, { recursive: true, force: true });
    }

    // rotas permitidas = rotas do contrato menos o webhook (conta feita aqui, de forma independente do gerador)
    const spec = parse(readFileSync(contrato, "utf8")) as { paths: Record<string, Record<string, unknown>> };
    const esperadas: string[] = [];
    for (const [caminho, item] of Object.entries(spec.paths)) {
      for (const m of ["get", "post", "put", "patch", "delete"]) if (item[m] && caminho !== "/pagamentos/webhook") esperadas.push(`${m.toUpperCase()} ${caminho}`);
    }
    expect([...ROTAS_PERMITIDAS].sort()).toEqual(esperadas.sort());
    expect(ROTAS_PERMITIDAS.some((r) => r.includes("webhook") || r.includes("actuator"))).toBe(false);

    // o arquivo versionado é exatamente o que o gerador produz hoje
    expect(normalizar(readFileSync(resolve(raiz, "src/api/rotas-permitidas.ts"), "utf8"))).toBe(gerarArquivo(readFileSync(contrato, "utf8")));
    // o achado da spec: o código do DELETE /me está no enum
    expect(readFileSync(resolve(raiz, "src/api/schema.d.ts"), "utf8")).toContain("EXCLUSAO_BLOQUEADA_POR_RESERVA");
  }, 60000);
});
