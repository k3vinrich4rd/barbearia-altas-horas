import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import PainelLayout from "@/app/painel/layout";
import PaginaAuditoria from "@/app/painel/auditoria/page";
import PaginaCadastros from "@/app/painel/cadastros/page";
import PaginaPendencias from "@/app/painel/pendencias/page";
import { BFF, erro, http, ligarServidor, servidor } from "@/testes/msw";
import { definirCaminho, zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { PainelPendencias } from "./PainelPendencias";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  definirCaminho("/painel");
});

const doMenu = () => screen.queryAllByRole("link").map((l) => l.textContent);

describe("acesso ao painel", () => {
  test("A44: sem sessão pede o login, CLIENTE vê acesso negado, BARBEIRO e ADMIN entram e o menu mostra cada item só para quem tem o papel; a URL escondida dá acesso negado e o 403 da API também", async () => {
    // sem sessão: nada do painel, só o convite para entrar (o proxy.ts já manda para /entrar antes da página)
    const anonimo = renderizar(
      <PainelLayout>
        <p>conteúdo do painel</p>
      </PainelLayout>,
      ANONIMO,
    );
    expect(await screen.findByRole("link", { name: "Entre para continuar" })).toHaveAttribute("href", "/entrar");
    expect(screen.queryByText("conteúdo do painel")).toBeNull();
    anonimo.unmount();

    // CLIENTE: acesso negado, sem menu
    const cliente = renderizar(
      <PainelLayout>
        <p>conteúdo do painel</p>
      </PainelLayout>,
      CLIENTE,
    );
    expect(screen.getByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Painel" })).toBeNull();
    expect(screen.queryByText("conteúdo do painel")).toBeNull();
    cliente.unmount();

    // BARBEIRO: agenda, reserva manual e alcance; nada de pendências, cadastros nem auditoria
    const barbeiro = renderizar(
      <PainelLayout>
        <p>conteúdo do painel</p>
      </PainelLayout>,
      SESSAO_BARBEIRO,
    );
    expect(screen.getByText("conteúdo do painel")).toBeInTheDocument();
    expect(doMenu()).toEqual(["Agenda", "Reserva manual", "Alcance"]);
    expect(screen.getByRole("link", { name: "Agenda" })).toHaveAttribute("aria-current", "page");
    barbeiro.unmount();

    // abrir a URL do item escondido mostra acesso negado
    for (const Pagina of [PaginaPendencias, PaginaCadastros, PaginaAuditoria]) {
      const escondida = renderizar(<Pagina />, SESSAO_BARBEIRO);
      expect(screen.getByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
      escondida.unmount();
    }

    // ADMIN: o menu inteiro
    const admin = renderizar(
      <PainelLayout>
        <p>conteúdo do painel</p>
      </PainelLayout>,
      ADMIN,
    );
    expect(doMenu()).toEqual(["Agenda", "Reserva manual", "Pendências", "Cadastros", "Auditoria", "Alcance"]);
    admin.unmount();

    // e o 403 da API (papel mudou depois do login) também é tratado, com texto e sem quebrar a tela
    servidor.use(
      http.get(`${BFF}/admin/pagamentos`, () => erro(403, "ACESSO_NEGADO")),
      http.get(`${BFF}/admin/pagamentos/eventos`, () => erro(403, "ACESSO_NEGADO")),
    );
    renderizar(<PainelPendencias />, ADMIN);
    expect((await screen.findAllByText(/Você não tem acesso a esta área/)).length).toBeGreaterThan(0);
  });
});
