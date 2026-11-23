// @vitest-environment node
import { describe, expect, test } from "vitest";
import { apiFalsa, cookiesDe, json, problemaApi, requisicao, tokensDeExemplo } from "@/testes/bff";
import { encaminhar } from "./proxy";
import { cadastro, convite, login } from "./sessao";

const CODIGO = "q3tD7mYb2uVn0pLwXc8sKfHrJ1aZgE5iO9vTyNdB4Mk";
const CORPO = { codigo: CODIGO, email: "barbeiro.novo@exemplo.test", senha: "senha-bem-longa-2026" };

describe("BFF do convite do barbeiro", () => {
  test("A37: POST /api/convite repassa o corpo para POST /auth/convite SEM token quando não há sessão e devolve só o status, sem corpo nem cookie", async () => {
    const api = apiFalsa(() => json({}, 204));
    const r = await convite(requisicao("/api/convite", { metodo: "POST", corpo: CORPO }), api.deps);

    expect(r.status).toBe(204);
    expect(await r.text()).toBe("");
    expect(r.headers.get("cache-control")).toBe("no-store");
    expect(r.headers.getSetCookie()).toEqual([]);
    expect(api.chamadas).toHaveLength(1);
    expect(api.chamadas[0].caminho).toBe("/auth/convite");
    expect(api.chamadas[0].headers.get("authorization")).toBeNull();
    expect(JSON.parse(api.chamadas[0].corpo)).toEqual(CORPO);
  });

  test("A37: quem já tem conta (H32) vai com o token da sessão, e depois do aceite a sessão local cai para o papel novo vir do login", async () => {
    const api = apiFalsa(() => json({}, 204));
    const r = await convite(
      requisicao("/api/convite", {
        metodo: "POST",
        corpo: { codigo: CODIGO, email: "ana@exemplo.test" },
        cookies: { ah_acesso: "jwt.da.ana", ah_refresh: "refresh-da-ana" },
      }),
      api.deps,
    );

    expect(r.status).toBe(204);
    expect(api.chamadas[0].headers.get("authorization")).toBe("Bearer jwt.da.ana");
    const c = cookiesDe(r);
    expect(c.ah_acesso).toContain("Max-Age=0");
    expect(c.ah_refresh).toContain("Max-Age=0");
  });

  test("A37: 422 e 429 da API passam adiante com o code (e o Retry-After), sem o código do convite no corpo", async () => {
    const invalido = apiFalsa(() => problemaApi(422, "CONVITE_INVALIDO"));
    const r422 = await convite(requisicao("/api/convite", { metodo: "POST", corpo: CORPO }), invalido.deps);
    expect(r422.status).toBe(422);
    const corpo = await r422.text();
    expect(JSON.parse(corpo).code).toBe("CONVITE_INVALIDO");
    expect(corpo).not.toContain(CODIGO);

    const limite = apiFalsa(() => problemaApi(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "42" }));
    const r429 = await convite(requisicao("/api/convite", { metodo: "POST", corpo: CORPO }), limite.deps);
    expect(r429.status).toBe(429);
    expect(r429.headers.get("retry-after")).toBe("42");
  });

  test("A37: origem de outro site é recusada sem chamar a API, e o proxy genérico NUNCA encaminha /auth/convite (só a rota própria)", async () => {
    const api = apiFalsa(() => json({}, 204));
    const fora = await convite(
      requisicao("/api/convite", { metodo: "POST", corpo: CORPO, origem: "https://outro-site.example" }),
      api.deps,
    );
    expect(fora.status).toBe(403);
    const generico = await encaminhar(requisicao("/api/auth/convite", { metodo: "POST", corpo: CORPO }), ["auth", "convite"], api.deps);
    expect(generico.status).toBe(404);
    expect(api.chamadas).toHaveLength(0);
  });

  test("A37: o BFF manda à API só o IP real do cliente (o primeiro do x-forwarded-for da Vercel, ou o x-real-ip) em X-Forwarded-For; valor que não é IP é ignorado", async () => {
    const api = apiFalsa(() => json({}, 204));
    await convite(
      requisicao("/api/convite", { metodo: "POST", corpo: CORPO, headers: { "x-forwarded-for": "203.0.113.9, 10.1.1.1" } }),
      api.deps,
    );
    await convite(requisicao("/api/convite", { metodo: "POST", corpo: CORPO, headers: { "x-real-ip": "198.51.100.4" } }), api.deps);
    await convite(
      requisicao("/api/convite", { metodo: "POST", corpo: CORPO, headers: { "x-forwarded-for": "<script>, 1.2.3.4" } }),
      api.deps,
    );
    await convite(requisicao("/api/convite", { metodo: "POST", corpo: CORPO }), api.deps);

    expect(api.chamadas.map((c) => c.headers.get("x-forwarded-for"))).toEqual(["203.0.113.9", "198.51.100.4", null, null]);
  });

  test("A37: login, cadastro e o proxy genérico também levam o IP real do cliente à API", async () => {
    const api = apiFalsa((c) => (c.caminho === "/auth/login" ? json(tokensDeExemplo()) : json({}, 201)));
    const ip = { "x-forwarded-for": "203.0.113.9" };
    await login(requisicao("/api/auth/login", { metodo: "POST", corpo: { email: "a@exemplo.test", senha: "x" }, headers: ip }), api.deps);
    await cadastro(requisicao("/api/auth/cadastro", { metodo: "POST", corpo: { email: "a@exemplo.test", senha: "x" }, headers: ip }), api.deps);
    await encaminhar(requisicao("/api/servicos", { headers: ip }), ["servicos"], api.deps);

    expect(api.chamadas.length).toBeGreaterThanOrEqual(3);
    for (const chamada of api.chamadas) expect(chamada.headers.get("x-forwarded-for")).toBe("203.0.113.9");
  });
});
