// @vitest-environment node
import { describe, expect, test } from "vitest";
import { apiFalsa, cookiesDe, json, problemaApi, requisicao, tokensDeExemplo } from "@/testes/bff";
import { encaminhar } from "./proxy";

describe("proxy do BFF", () => {
  test("A2: repassa o Bearer do cookie e nenhuma resposta do BFF expõe o JWT", async () => {
    const api = apiFalsa(() => json({ nome: "Cliente Teste" }, 200, { "set-cookie": "x=1", server: "Tomcat" }));
    const r = await encaminhar(requisicao("/api/me", { cookies: { ah_acesso: "jwt.do.cookie" } }), ["me"], api.deps);
    expect(api.chamadas[0].headers.get("authorization")).toBe("Bearer jwt.do.cookie");
    expect(api.chamadas[0].url).toBe("http://api.interna:8080/me");
    expect(await r.json()).toEqual({ nome: "Cliente Teste" });
    const tudo = JSON.stringify([...r.headers.entries()]);
    expect(tudo).not.toContain("jwt.do.cookie");
    expect(r.headers.getSetCookie()).toEqual([]);
  });

  test("A3: com 401 renova UMA vez, repete UMA vez e duas chamadas simultâneas dividem a renovação", async () => {
    // renovação bem-sucedida e repetição com o token novo
    const api = apiFalsa((c) => {
      if (c.caminho === "/auth/refresh")
        return json(tokensDeExemplo({ accessToken: "jwt.novo", refreshToken: "refresh-novo" }));
      return c.headers.get("authorization") === "Bearer jwt.novo"
        ? json({ itens: [] })
        : problemaApi(401, "NAO_AUTENTICADO");
    });
    const r = await encaminhar(
      requisicao("/api/me/agendamentos", { cookies: { ah_acesso: "jwt.velho", ah_refresh: "refresh-A3-a" } }),
      ["me", "agendamentos"],
      api.deps,
    );
    expect(r.status).toBe(200);
    expect(api.quantas("/auth/refresh")).toBe(1);
    expect(api.quantas("/me/agendamentos")).toBe(2);
    expect(cookiesDe(r).ah_acesso).toContain("Max-Age=900");
    expect(cookiesDe(r).ah_refresh).toBeDefined();

    // a repetição também dá 401: o navegador recebe 401 e não há terceira tentativa
    const sempre401 = apiFalsa((c) =>
      c.caminho === "/auth/refresh" ? json(tokensDeExemplo()) : problemaApi(401, "NAO_AUTENTICADO"),
    );
    const r2 = await encaminhar(
      requisicao("/api/me/agendamentos", { cookies: { ah_acesso: "jwt.velho", ah_refresh: "refresh-A3-b" } }),
      ["me", "agendamentos"],
      sempre401.deps,
    );
    expect(r2.status).toBe(401);
    expect(sempre401.quantas("/me/agendamentos")).toBe(2);
    expect(sempre401.quantas("/auth/refresh")).toBe(1);

    // duas chamadas simultâneas, mesmo refresh: uma só renovação
    const par = apiFalsa(async (c) => {
      if (c.caminho === "/auth/refresh") {
        await new Promise((ok) => setTimeout(ok, 20));
        return json(tokensDeExemplo({ accessToken: "jwt.novo" }));
      }
      return c.headers.get("authorization") === "Bearer jwt.novo" ? json({}) : problemaApi(401, "NAO_AUTENTICADO");
    });
    const pedir = () =>
      encaminhar(
        requisicao("/api/me", { cookies: { ah_acesso: "jwt.velho", ah_refresh: "refresh-A3-c" } }),
        ["me"],
        par.deps,
      );
    const [a, b] = await Promise.all([pedir(), pedir()]);
    expect([a.status, b.status]).toEqual([200, 200]);
    expect(par.quantas("/auth/refresh")).toBe(1);
  });

  test("A6: rotas fora do contrato ou proibidas dão 404 sem chamar a API", async () => {
    const api = apiFalsa(() => json({}));
    const casos: [string, string[]][] = [
      ["/api/pagamentos/webhook", ["pagamentos", "webhook"]],
      ["/api/actuator/health", ["actuator", "health"]],
      ["/api/admin/inexistente", ["admin", "inexistente"]],
      ["/api/../etc", ["..", "etc"]],
      ["/api/auth/refresh", ["auth", "refresh"]],
    ];
    for (const [url, segmentos] of casos) {
      const r = await encaminhar(requisicao(url, { metodo: "POST" }), segmentos, api.deps);
      expect(r.status, url).toBe(404);
    }
    expect(api.chamadas).toHaveLength(0);
  });
});
