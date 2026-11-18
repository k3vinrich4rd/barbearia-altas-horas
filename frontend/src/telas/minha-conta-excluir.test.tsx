import { screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { MinhaConta } from "./MinhaConta";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const perfil = (papeis: string[]) => ({ id: "u", nome: "Fulano", email: "f@exemplo.test", papeis });
beforeEach(() => {
  zerarNavegacao();
  servidor.use(
    http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([])),
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
    http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: false })),
  );
});

describe("excluir conta", () => {
  test("A41: equipe não vê a seção; o diálogo prende o foco, Esc fecha e devolve o foco; só habilita ao digitar EXCLUIR; 204 vai à home; 409 explica e nada muda", async () => {
    for (const papeis of [["BARBEIRO"], ["ADMIN"]]) {
      servidor.use(http.get(`${BFF}/me`, () => HttpResponse.json(perfil(papeis))));
      const { unmount } = renderizar(<MinhaConta />, CLIENTE);
      await screen.findByRole("button", { name: "Baixar meus dados" });
      expect(screen.queryByRole("heading", { name: "Excluir conta" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Excluir minha conta" })).toBeNull();
      unmount();
    }

    servidor.use(http.get(`${BFF}/me`, () => HttpResponse.json(perfil(["CLIENTE"]))));
    let r = renderizar(<MinhaConta />, CLIENTE);
    const origem = await screen.findByRole("button", { name: "Excluir minha conta" });
    await r.user.click(origem);
    const dialogo = await screen.findByRole("dialog", { name: "Excluir a conta?" });
    expect(dialogo).toHaveTextContent("Isso não tem volta");
    const final = within(dialogo).getByRole("button", { name: "Excluir para sempre" });
    expect(final).toBeDisabled();

    // foco preso: Tab percorre só o diálogo
    for (let i = 0; i < 6; i++) {
      await r.user.tab();
      expect(dialogo.contains(document.activeElement)).toBe(true);
    }
    await r.user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
    await waitFor(() => expect(origem).toHaveFocus());

    // digitar errado não habilita; certo habilita
    await r.user.click(origem);
    const caixa = await screen.findByLabelText("Digite EXCLUIR para confirmar");
    await r.user.type(caixa, "excluir");
    expect(screen.getByRole("button", { name: "Excluir para sempre" })).toBeDisabled();
    await r.user.clear(caixa);
    await r.user.type(caixa, "EXCLUIR");
    expect(screen.getByRole("button", { name: "Excluir para sempre" })).toBeEnabled();

    // 409: explica sem culpar e nada muda
    servidor.use(http.delete(`${BFF}/me`, () => erro(409, "EXCLUSAO_BLOQUEADA_POR_RESERVA")));
    await r.user.click(screen.getByRole("button", { name: "Excluir para sempre" }));
    expect(await screen.findByText(/Você tem um agendamento pago\. Combine o cancelamento com a barbearia/)).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(roteador.push).not.toHaveBeenCalled();
    r.unmount();

    // 204: home com o aviso
    servidor.use(http.delete(`${BFF}/me`, () => new HttpResponse(null, { status: 204 })));
    r = renderizar(<MinhaConta />, CLIENTE);
    await r.user.click(await screen.findByRole("button", { name: "Excluir minha conta" }));
    await r.user.type(await screen.findByLabelText("Digite EXCLUIR para confirmar"), "EXCLUIR");
    await r.user.click(screen.getByRole("button", { name: "Excluir para sempre" }));
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/?aviso=conta-excluida"));
  });
});
