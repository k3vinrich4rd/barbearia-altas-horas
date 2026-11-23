import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { NavegacaoDoPainel } from "@/componentes/NavegacaoDoPainel";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { barbeiroAdmin } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { CadastroDeBarbeiros } from "./CadastroDeBarbeiros";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

const CODIGO = "q3tD7mYb2uVn0pLwXc8sKfHrJ1aZgE5iO9vTyNdB4Mk";
const RAFA = barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", acesso: "SEM_CONTA" });

function lista(...barbeiros: unknown[]) {
  servidor.use(http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json(barbeiros)));
}

describe("convite do barbeiro no painel (Cadastros → Barbeiros)", () => {
  test("A35: 'Gerar convite' sem pedir e-mail mostra o link /convite#código, o QR, o prazo, 'Copiar link' e 'Enviar pelo WhatsApp'; fechou, o código some e só resta 'convite pendente até <data>' com 'Gerar outro' e 'Revogar'", async () => {
    lista(RAFA);
    const corpos: string[] = [];
    servidor.use(
      http.post(`${BFF}/admin/barbeiros/:id/convite`, async ({ request, params }) => {
        corpos.push(await request.text());
        expect(params.id).toBe("b2");
        return HttpResponse.json({ codigo: CODIGO, expiraEm: "2026-11-20T08:00:00-03:00" }, { status: 201 });
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    const tabela = await screen.findByRole("table", { name: "Barbeiros" });
    expect(within(tabela).getByText("Sem acesso")).toBeInTheDocument();

    lista(barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", acesso: "CONVITE_PENDENTE", conviteExpiraEm: "2026-11-20T08:00:00-03:00" }));
    await user.click(screen.getByRole("button", { name: "Gerar convite para Rafa" }));

    const dialogo = await screen.findByRole("dialog", { name: "Convite de Rafa" });
    expect(corpos).toEqual([""]); // sem corpo: o dono não informa e-mail
    expect(within(dialogo).queryByLabelText(/e-mail/i)).not.toBeInTheDocument();
    expect(within(dialogo).getByLabelText("Link do convite")).toHaveValue(`http://localhost:3000/convite#${CODIGO}`);
    expect(within(dialogo).getByRole("button", { name: "Copiar link" })).toBeInTheDocument();
    expect(within(dialogo).getByText(/aparece só agora e vale até .*20\/11 às 08:00/)).toBeInTheDocument();
    expect(within(dialogo).getByText(/senha nunca vai por mensagem/)).toBeInTheDocument();
    const whatsapp = within(dialogo).getByRole("link", { name: "Enviar pelo WhatsApp" });
    expect(whatsapp.getAttribute("href")).toContain("https://wa.me/?text=");
    expect(decodeURIComponent(whatsapp.getAttribute("href") ?? "")).toContain(`/convite#${CODIGO}`);
    expect(await within(dialogo).findByRole("img", { name: /QR Code do convite de Rafa/ })).toBeInTheDocument();

    // a lista recarregada já traz o convite pendente; fechar o diálogo apaga o código da tela
    await user.click(within(dialogo).getByRole("button", { name: "Fechar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(document.body.innerHTML).not.toContain(CODIGO);
    expect(await screen.findByText(/Convite pendente até .*20\/11 às 08:00/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gerar outro convite para Rafa" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Revogar o convite de Rafa" })).toBeInTheDocument();
  });

  test("A35: 'Revogar' chama DELETE e avisa; barbeiro com acesso ativo ou desligado não tem botão de convite", async () => {
    lista(
      barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", acesso: "CONVITE_PENDENTE", conviteExpiraEm: "2026-11-20T08:00:00-03:00" }),
      barbeiroAdmin({ id: "b3", nomeExibicao: "Bia", acesso: "ATIVO", usuarioId: "u3" }),
      barbeiroAdmin({ id: "b4", nomeExibicao: "Caio", acesso: "DESLIGADO", ativo: false }),
    );
    const chamadas: string[] = [];
    servidor.use(
      http.delete(`${BFF}/admin/barbeiros/:id/convite`, ({ params }) => {
        chamadas.push(String(params.id));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    await screen.findByRole("table", { name: "Barbeiros" });
    expect(screen.queryByRole("button", { name: /convite para Bia/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /convite para Caio/ })).not.toBeInTheDocument();
    expect(screen.getByText("Acesso ativo")).toBeInTheDocument();
    expect(screen.getByText("Desligado")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Revogar o convite de Rafa" }));
    expect(await screen.findByText("Convite de Rafa revogado.")).toBeInTheDocument();
    expect(chamadas).toEqual(["b2"]);
  });

  test("A35: erro ao gerar (409 de quem já tem acesso) aparece como aviso e nenhum link é mostrado", async () => {
    lista(RAFA);
    servidor.use(http.post(`${BFF}/admin/barbeiros/:id/convite`, () => erro(409, "USUARIO_JA_VINCULADO")));
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    await user.click(await screen.findByRole("button", { name: "Gerar convite para Rafa" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/já está ligada a outro barbeiro/);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  test("A35: desligar (desmarcar 'Ativo' e salvar) pede confirmação explicando que mantém o histórico e anonimiza; só então envia ativo false", async () => {
    lista(barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", acesso: "ATIVO", usuarioId: "u-rafa", version: 4 }));
    const puts: Record<string, unknown>[] = [];
    servidor.use(
      http.put(`${BFF}/admin/barbeiros/:id`, async ({ request }) => {
        puts.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json(barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", ativo: false, acesso: "DESLIGADO" }));
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    await user.click(await screen.findByRole("button", { name: "Editar Rafa" }));
    await user.click(screen.getByLabelText(/Ativo \(aparece/));
    await user.click(screen.getByRole("button", { name: "Salvar barbeiro" }));

    const dialogo = await screen.findByRole("dialog", { name: "Desligar Rafa" });
    expect(dialogo).toHaveTextContent(/mantém o histórico/);
    expect(dialogo).toHaveTextContent(/anonimizada/);
    expect(dialogo).toHaveTextContent(/convite novo/);
    expect(puts).toEqual([]);

    await user.click(within(dialogo).getByRole("button", { name: "Desligar barbeiro" }));
    await vi.waitFor(() => expect(puts).toHaveLength(1));
    expect(puts[0]).toMatchObject({ nomeExibicao: "Rafa", ativo: false, version: 4 });
  });

  test("A35: o papel ADMIN aparece como 'Dono/Admin' no menu do painel (o BARBEIRO, como 'Barbeiro')", async () => {
    const { unmount } = renderizar(<NavegacaoDoPainel />, ADMIN);
    expect(screen.getByText("Você entrou como Dono/Admin")).toBeInTheDocument();
    unmount();
    renderizar(<NavegacaoDoPainel />, { autenticado: true, usuario: { id: "u3", nome: "Rafa" }, papeis: ["BARBEIRO"] });
    expect(screen.getByText("Você entrou como Barbeiro")).toBeInTheDocument();
    expect(screen.queryByText(/Dono\/Admin/)).not.toBeInTheDocument();
  });
});
