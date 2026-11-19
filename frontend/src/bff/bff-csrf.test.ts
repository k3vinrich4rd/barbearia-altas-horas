// @vitest-environment node
import { describe, expect, test } from "vitest";
import { apiFalsa, json, requisicao } from "@/testes/bff";
import { encaminhar } from "./proxy";

const ESCRITAS: [string, string, string[]][] = [
  ["POST", "/api/agendamentos/guest", ["agendamentos", "guest"]],
  ["PUT", "/api/me/senha", ["me", "senha"]],
  ["PATCH", "/api/admin/agendamentos/abc/status", ["admin", "agendamentos", "abc", "status"]],
  ["DELETE", "/api/me", ["me"]],
];

describe("CSRF do BFF", () => {
  test("A5: escrita sem Origin ou com Origin de outro site dá 403 sem chamar a API; Origin do site passa; GET não exige", async () => {
    const api = apiFalsa(() => json({ ok: true }));
    for (const [metodo, url, segmentos] of ESCRITAS) {
      for (const origem of ["https://outro.exemplo", null]) {
        const r = await encaminhar(requisicao(url, { metodo, origem, corpo: {} }), segmentos, api.deps);
        expect(r.status, `${metodo} com origem ${origem}`).toBe(403);
      }
    }
    expect(api.chamadas).toHaveLength(0);

    for (const [metodo, url, segmentos] of ESCRITAS) {
      const r = await encaminhar(requisicao(url, { metodo, corpo: {} }), segmentos, api.deps);
      expect(r.status, `${metodo} do próprio site`).toBe(200);
    }
    expect(api.chamadas).toHaveLength(ESCRITAS.length);

    const get = await encaminhar(requisicao("/api/servicos", { origem: null }), ["servicos"], api.deps);
    expect(get.status).toBe(200);
  });
});
