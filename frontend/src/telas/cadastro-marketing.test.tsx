import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { Cadastro } from "./Cadastro";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  definirBusca("");
});

async function preencherEEnviar(user: ReturnType<typeof renderizar>["user"]) {
  await user.type(screen.getByLabelText("Nome"), "Fulana de Tal");
  await user.type(screen.getByLabelText("E-mail"), "fulana@exemplo.test");
  await user.type(screen.getByLabelText("WhatsApp"), "11900000002");
  await user.type(screen.getByLabelText("Senha"), "senha-bem-longa-123");
  await user.click(screen.getByRole("button", { name: "Criar conta" }));
}

describe("marketing no cadastro", () => {
  test("A34: caixa desmarcada com o texto da API e link da política; marcada vai true, desmarcada false; falha do texto desabilita só a caixa", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
      http.post(`${BFF}/auth/cadastro`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ sessao: true, usuario: {}, deveTrocarSenha: false }, { status: 201 });
      }),
    );

    let r = renderizar(<Cadastro />);
    const caixa = await screen.findByRole("checkbox", { name: /Aceito receber ofertas da barbearia pelo WhatsApp/ });
    expect(caixa).not.toBeChecked();
    expect(screen.getByRole("link", { name: "Política de privacidade" })).toHaveAttribute("href", "/privacidade");
    await r.user.click(caixa);
    await preencherEEnviar(r.user);
    await waitFor(() => expect(corpos).toHaveLength(1));
    expect(corpos[0].aceitaMarketing).toBe(true);
    r.unmount();

    r = renderizar(<Cadastro />);
    await screen.findByRole("checkbox", { name: /Aceito receber ofertas/ });
    await preencherEEnviar(r.user);
    await waitFor(() => expect(corpos).toHaveLength(2));
    expect(corpos[1].aceitaMarketing).toBe(false);
    r.unmount();

    // falha ao buscar o texto: só a caixa fica desabilitada; o cadastro segue
    servidor.use(http.get(`${BFF}/politica-privacidade`, () => erro(500, "ERRO_INTERNO")));
    r = renderizar(<Cadastro />);
    const desabilitada = await screen.findByRole("checkbox", { name: /indisponível agora/ });
    expect(desabilitada).toBeDisabled();
    expect(screen.getByRole("button", { name: "Criar conta" })).toBeEnabled();
    await preencherEEnviar(r.user);
    await waitFor(() => expect(corpos).toHaveLength(3));
    expect(corpos[2].aceitaMarketing).toBe(false);
  });
});
