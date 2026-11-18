import { screen } from "@testing-library/react";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { voltarSeguro } from "@/api/sessao-encerrada";
import { proxy } from "@/proxy";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { ADMIN, ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { ExigePapel } from "./AcessoNegado";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { Entrar } from "./Entrar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const pedir = (caminho: string, cookie?: string) => proxy(new NextRequest(`http://localhost:3000${caminho}`, { headers: cookie ? { cookie } : {} }));

describe("rotas protegidas", () => {
  test("A37: sem sessão vai a /entrar?voltar=; voltar externo é ignorado; CLIENTE em /painel vê acesso negado", async () => {
    for (const caminho of ["/meus-agendamentos", "/minha-conta", "/painel", "/painel/cadastros"]) {
      const r = pedir(caminho);
      expect(r.status, caminho).toBe(307);
      const destino = new URL(r.headers.get("location")!);
      expect(destino.pathname).toBe("/entrar");
      expect(destino.searchParams.get("voltar")).toBe(caminho);
      // com cookie de sessão, a navegação segue (a autorização de verdade é da API)
      expect(pedir(caminho, "ah_acesso=x").headers.get("location"), caminho).toBeNull();
    }
    expect(pedir("/entrar").status).toBe(200);
    expect(pedir("/agendar").headers.get("location")).toBeNull();

    // `voltar` só vale como caminho interno
    for (const mau of ["//evil.com", "https://evil.com", "evil.com", "/\\evil.com", ""]) expect(voltarSeguro(mau, "/padrao"), mau).toBe("/padrao");
    expect(voltarSeguro("/meus-agendamentos?pagina=2", "/padrao")).toBe("/meus-agendamentos?pagina=2");
    servidor.use(
      http.post(`${BFF}/auth/login`, () => HttpResponse.json({ usuario: { id: "u", papeis: ["CLIENTE"] }, deveTrocarSenha: false })),
      http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "N" }, papeis: ["CLIENTE"] })),
    );
    definirBusca("voltar=//evil.com");
    const e = renderizar(<Entrar />);
    await e.user.type(screen.getByLabelText("E-mail"), "a@exemplo.test");
    await e.user.type(screen.getByLabelText("Senha"), "uma-senha-longa");
    await e.user.click(screen.getByRole("button", { name: "Entrar" }));
    await vi.waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/meus-agendamentos"));
    e.unmount();

    // CLIENTE no painel: acesso negado, não a tela do painel nem erro técnico
    const c = renderizar(<ExigePapel papeis={["ADMIN", "BARBEIRO"]}><h1>Painel secreto</h1></ExigePapel>, CLIENTE);
    expect(screen.getByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
    expect(screen.getByText("Você não tem acesso a esta área.")).toBeInTheDocument();
    expect(screen.queryByText("Painel secreto")).toBeNull();
    c.unmount();
    renderizar(<ExigePapel papeis={["ADMIN", "BARBEIRO"]}><h1>Painel secreto</h1></ExigePapel>, ADMIN);
    expect(screen.getByText("Painel secreto")).toBeInTheDocument();
    void ANONIMO;
    void erro;
  });
});
