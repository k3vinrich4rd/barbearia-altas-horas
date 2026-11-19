// @vitest-environment node
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";
import { parse } from "yaml";
import { codigoParaSuporte, MENSAGEM_GENERICA, MENSAGENS, mensagemDoErro } from "./mensagens";

const spec = parse(readFileSync(resolve(__dirname, "../../../docs/contrato/openapi.yaml"), "utf8"));
const CODIGOS: string[] = spec.components.schemas.CodigoErro.enum;

describe("catálogo de mensagens", () => {
  test("A13: todo código do enum tem texto em português; código inventado cai no genérico com traceId", () => {
    expect(CODIGOS.length).toBeGreaterThan(30);
    for (const code of [...CODIGOS, "API_INDISPONIVEL"]) {
      const texto = mensagemDoErro({ code, status: 400, retryAfter: 5 });
      expect(texto, code).toBeTruthy();
      expect(MENSAGENS[code], `${code} sem texto no catálogo`).toBeTruthy();
      expect(texto).not.toMatch(/erro \d{3}|HTTP|status|exception|null|undefined|\{n\}/i);
      expect(texto).toMatch(/[a-zá-ú]/);
    }
    const inventado = mensagemDoErro({ code: "CODIGO_DA_VERSAO_NOVA", status: 418 });
    expect(inventado).toBe(MENSAGEM_GENERICA);
    expect(codigoParaSuporte({ traceId: "trace-77" })).toContain("trace-77");
    expect(codigoParaSuporte({})).toBeUndefined();
    // a espera do 429 entra no texto
    expect(mensagemDoErro({ code: "LIMITE_EXCEDIDO", status: 429, retryAfter: 9 })).toContain("9 segundos");
  });
});
