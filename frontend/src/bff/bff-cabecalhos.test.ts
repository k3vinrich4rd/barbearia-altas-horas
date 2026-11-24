// @vitest-environment node
import { NextRequest } from "next/server";
import { describe, expect, test } from "vitest";
import { proxy } from "@/proxy";
import { apiFalsa, cookiesDe, json, requisicao, tokensDeExemplo } from "@/testes/bff";
import { login, logout } from "./sessao";

function pedir(caminho: string, cookie?: string) {
  return proxy(new NextRequest(`http://localhost:3000${caminho}`, { headers: cookie ? { cookie } : {} }));
}

describe("cabeçalhos de segurança", () => {
  test("A10: CSP, nosniff, Referrer-Policy e no-store onde há dado pessoal; nenhum Set-Cookie sem HttpOnly em /api/auth", async () => {
    for (const caminho of ["/", "/agendar", "/api/servicos", "/minha-conta", "/meus-agendamentos"]) {
      const r = pedir(caminho, "ah_sessao=1");
      const csp = r.headers.get("content-security-policy") ?? "";
      expect(csp, caminho).toContain("frame-ancestors 'none'");
      expect(csp).toContain("default-src 'self'");
      expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+'/);
      expect(csp).toContain("connect-src 'self'");
      expect(csp).toContain("worker-src 'self'");
      expect(csp).toContain("form-action 'self'");
      expect(r.headers.get("x-content-type-options")).toBe("nosniff");
      expect(r.headers.get("referrer-policy")).toBe("no-referrer");
      expect(r.headers.get("permissions-policy")).toBe("camera=(), microphone=(), geolocation=(), payment=()");
    }
    expect(pedir("/api/servicos").headers.get("cache-control")).toBe("no-store");
    expect(pedir("/minha-conta", "ah_sessao=1").headers.get("cache-control")).toBe("no-store");
    // cada resposta com um nonce novo
    expect(pedir("/").headers.get("content-security-policy")).not.toBe(
      pedir("/").headers.get("content-security-policy"),
    );

    const api = apiFalsa(() => json(tokensDeExemplo()));
    const respostas = [
      await login(
        requisicao("/api/auth/login", { metodo: "POST", corpo: { email: "a@b.test", senha: "x" } }),
        api.deps,
      ),
      await logout(requisicao("/api/auth/logout", { metodo: "POST", cookies: { ah_refresh: "r" } }), api.deps),
    ];
    for (const r of respostas) {
      expect(r.headers.get("cache-control")).toBe("no-store");
      for (const linha of Object.values(cookiesDe(r))) expect(linha).toContain("HttpOnly");
    }
  });
});
