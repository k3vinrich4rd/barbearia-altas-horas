// @vitest-environment node
import { describe, expect, test } from "vitest";
import { destinoAposSessaoEncerrada } from "@/api/sessao-encerrada";
import { mensagemDoErro } from "@/api/mensagens";
import { apiFalsa, cookiesDe, json, problemaApi, requisicao, tokensDeExemplo } from "@/testes/bff";
import { encaminhar } from "./proxy";
import { login, logout } from "./sessao";

describe("sessão do BFF", () => {
  test("A1: login devolve só { usuario, deveTrocarSenha } e grava os dois cookies HttpOnly", async () => {
    const api = apiFalsa(() => json(tokensDeExemplo()));
    const r = await login(requisicao("/api/auth/login", { metodo: "POST", corpo: { email: "a@exemplo.test", senha: "x" } }), api.deps);

    expect(r.status).toBe(200);
    const texto = await r.text();
    expect(Object.keys(JSON.parse(texto)).sort()).toEqual(["deveTrocarSenha", "usuario"]);
    expect(texto).not.toMatch(/accessToken|refreshToken|jwt\.acesso|refresh-exemplo/);

    const c = cookiesDe(r);
    expect(c.ah_refresh).toContain("HttpOnly");
    expect(c.ah_refresh).toContain("Secure");
    expect(c.ah_refresh).toContain("SameSite=Strict");
    expect(c.ah_refresh).toContain("Path=/api/auth");
    expect(c.ah_acesso).toContain("HttpOnly");
    expect(c.ah_acesso).toContain("Secure");
    expect(c.ah_acesso).toContain("SameSite=Strict");
    expect(c.ah_acesso).toContain("Path=/api;");
    expect(c.ah_acesso).toContain("Max-Age=900");
  });

  test("A4: refresh reutilizado apaga os dois cookies, responde 401 com o code e a tela volta ao login com aviso", async () => {
    const api = apiFalsa((c) => (c.caminho === "/auth/refresh" ? problemaApi(401, "REFRESH_REUTILIZADO") : problemaApi(401, "NAO_AUTENTICADO")));
    const r = await encaminhar(
      requisicao("/api/me/agendamentos", { cookies: { ah_acesso: "vencido", ah_refresh: "refresh-reusado-A4" } }),
      ["me", "agendamentos"],
      api.deps,
    );
    expect(r.status).toBe(401);
    expect((await r.json()).code).toBe("REFRESH_REUTILIZADO");
    const c = cookiesDe(r);
    expect(c.ah_refresh).toContain("Max-Age=0");
    expect(c.ah_acesso).toContain("Max-Age=0");

    const destino = destinoAposSessaoEncerrada("/meus-agendamentos");
    expect(destino).toBe("/entrar?voltar=%2Fmeus-agendamentos&aviso=sessao");
    expect(mensagemDoErro({ code: "REFRESH_INVALIDO", status: 401 })).toBe("Sua sessão terminou. Entre de novo.");
  });

  test("A7: logout chama a API com o refresh do cookie, apaga os cookies e responde 204 mesmo com a API fora do ar", async () => {
    const api = apiFalsa(() => json({}, 204));
    const r1 = await logout(requisicao("/api/auth/logout", { metodo: "POST", cookies: { ah_refresh: "refresh-logout" } }), api.deps);
    expect(r1.status).toBe(204);
    expect(JSON.parse(api.chamadas[0].corpo)).toEqual({ refreshToken: "refresh-logout" });
    expect(api.chamadas[0].caminho).toBe("/auth/logout");
    expect(cookiesDe(r1).ah_refresh).toContain("Max-Age=0");

    const fora = { fetch: (async () => { throw new TypeError("rede fora"); }) as unknown as typeof fetch, apiUrl: "http://api.interna:8080" };
    const r2 = await logout(requisicao("/api/auth/logout", { metodo: "POST", cookies: { ah_refresh: "refresh-logout" } }), fora);
    expect(r2.status).toBe(204);
    const c = cookiesDe(r2);
    expect(c.ah_refresh).toContain("Max-Age=0");
    expect(c.ah_acesso).toContain("Max-Age=0");
  });
});
