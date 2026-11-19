// @vitest-environment node
import { describe, expect, test } from "vitest";
import { apiFalsa, json, problemaApi, requisicao } from "@/testes/bff";
import { encaminhar } from "./proxy";

describe("erros no BFF", () => {
  test("A9: problem+json passa com code, traceId, errors e Retry-After; API fora e corpo não-JSON viram 502 API_INDISPONIVEL", async () => {
    const quente = { "set-cookie": "interno=1", server: "Apache-Coyote", "x-interno": "segredo" };
    const casos: [number, string, Record<string, unknown>, Record<string, string>][] = [
      [409, "HORARIO_INDISPONIVEL", {}, {}],
      [422, "ANTECEDENCIA_INSUFICIENTE", {}, {}],
      [400, "VALIDACAO_INVALIDA", { errors: [{ field: "nome", message: "obrigatório" }] }, {}],
      [429, "LIMITE_EXCEDIDO", {}, { "retry-after": "7" }],
    ];
    for (const [status, code, extra, cabecalhos] of casos) {
      const api = apiFalsa(() => problemaApi(status, code, extra, { ...quente, ...cabecalhos }));
      const r = await encaminhar(
        requisicao("/api/agendamentos/guest", { metodo: "POST", corpo: {} }),
        ["agendamentos", "guest"],
        api.deps,
      );
      expect(r.status).toBe(status);
      const corpo = await r.json();
      expect(corpo.code).toBe(code);
      expect(corpo.traceId).toBe("trace-9");
      if (extra.errors) expect(corpo.errors).toEqual(extra.errors);
      if (cabecalhos["retry-after"]) expect(r.headers.get("retry-after")).toBe("7");
      expect(r.headers.get("server")).toBeNull();
      expect(r.headers.get("x-interno")).toBeNull();
      expect(r.headers.getSetCookie()).toEqual([]);
    }

    const fora = apiFalsa(() => {
      throw new TypeError("connect ECONNREFUSED 127.0.0.1:8080");
    });
    const r1 = await encaminhar(requisicao("/api/servicos"), ["servicos"], fora.deps);
    expect(r1.status).toBe(502);
    const t1 = await r1.text();
    expect(JSON.parse(t1).code).toBe("API_INDISPONIVEL");
    expect(t1).not.toMatch(/ECONNREFUSED|127\.0\.0\.1|api\.interna|at \w+\./);

    const html = apiFalsa(
      () =>
        new Response("<html>Whitelabel Error Page at org.spring.Foo(Foo.java:1)</html>", {
          status: 500,
          headers: { "content-type": "text/html", server: "Tomcat" },
        }),
    );
    const r2 = await encaminhar(requisicao("/api/servicos"), ["servicos"], html.deps);
    expect(r2.status).toBe(502);
    const t2 = await r2.text();
    expect(JSON.parse(t2).code).toBe("API_INDISPONIVEL");
    expect(t2).not.toContain("Whitelabel");
    expect(r2.headers.get("server")).toBeNull();

    // sucesso continua passando intacto
    const ok = await encaminhar(requisicao("/api/servicos"), ["servicos"], apiFalsa(() => json([{ id: "1" }])).deps);
    expect(await ok.json()).toEqual([{ id: "1" }]);
  });
});
