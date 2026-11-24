import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { Cadastro } from "./Cadastro";
import { MinhaConta } from "./MinhaConta";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  definirBusca("");
  servidor.use(
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
    http.get(`${BFF}/sessao`, () =>
      HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "F" }, papeis: ["CLIENTE"] }),
    ),
  );
});

async function preencher(user: ReturnType<typeof renderizar>["user"]) {
  await user.type(screen.getByLabelText("Nome"), "Fulana de Tal");
  await user.type(screen.getByLabelText("E-mail"), "fulana@exemplo.test");
  await user.type(screen.getByLabelText("WhatsApp"), "11900000002");
  await user.type(screen.getByLabelText("Senha"), "senha-bem-longa-123");
}

describe("caixinha de consentimento de região", () => {
  test("A47: no cadastro a caixinha nasce desmarcada, com o texto da API, o canal de contato e o link da política; o campo de CEP só existe com ela marcada e, sem marcar, o CEP não sai do navegador", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/auth/cadastro`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ sessao: true, usuario: {}, deveTrocarSenha: false }, { status: 201 });
      }),
    );
    // nenhum GET /cep está registrado: qualquer consulta cairia em onUnhandledRequest: "error" e derrubaria o teste

    const { user, unmount } = renderizar(<Cadastro />);
    const caixa = await screen.findByRole("checkbox", { name: /Autorizo usar só meu bairro e minha cidade/ });
    await waitFor(() => expect(caixa).toBeEnabled());
    expect(caixa).not.toBeChecked();
    expect(screen.queryByRole("textbox", { name: "CEP" })).toBeNull();
    expect(screen.getByText(/Para falar sobre seus dados: contato@exemplo.test/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Saiba mais na política de privacidade" })).toHaveAttribute(
      "href",
      "/privacidade",
    );

    // marca, digita o CEP, desmarca de novo: o CEP some da tela e do corpo
    await user.click(caixa);
    await user.type(screen.getByRole("textbox", { name: "CEP" }), "01001-000");
    await user.click(caixa);
    expect(screen.queryByRole("textbox", { name: "CEP" })).toBeNull();
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Criar conta" }));
    await waitFor(() => expect(corpos).toHaveLength(1));
    expect("cep" in corpos[0]).toBe(false);
    expect("autorizaRegiao" in corpos[0]).toBe(false);
    unmount();
  });

  test("A47: sem o texto da política, a caixinha de região fica desabilitada e o cadastro segue sem CEP", async () => {
    servidor.use(http.get(`${BFF}/politica-privacidade`, () => new HttpResponse(null, { status: 500 })));
    const { user } = renderizar(<Cadastro />);
    const caixa = await screen.findByRole("checkbox", { name: /Indisponível agora: é opcional/ });
    expect(caixa).toBeDisabled();
    expect(screen.getByRole("button", { name: "Criar conta" })).toBeEnabled();
    expect(user).toBeDefined();
  });

  test("A47: em Meus dados a caixinha de região também nasce desmarcada, e o PUT /me só leva o CEP com ela marcada e a versão do texto", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/me`, () =>
        HttpResponse.json({ id: "u1", nome: "Cliente Teste", email: "c@exemplo.test", papeis: ["CLIENTE"], regiaoAutorizada: false }),
      ),
      http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([])),
      http.put(`${BFF}/me`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({
          id: "u1",
          nome: "Cliente Teste",
          email: "c@exemplo.test",
          papeis: ["CLIENTE"],
          regiaoAutorizada: true,
          endereco: { cep: null, bairro: "Centro", cidade: "Osasco", uf: "SP", completo: true },
        });
      }),
    );
    const { user } = renderizar(<MinhaConta />, CLIENTE);
    const caixa = await screen.findByRole("checkbox", { name: /Autorizo usar só meu bairro/ });
    await waitFor(() => expect(caixa).toBeEnabled());
    expect(caixa).not.toBeChecked();
    expect(screen.queryByRole("textbox", { name: "CEP" })).toBeNull();
    await user.click(caixa);
    await user.type(screen.getByRole("textbox", { name: "CEP" }), "06010-000");
    await user.click(screen.getByRole("button", { name: "Autorizar" }));
    await waitFor(() => expect(corpos).toHaveLength(1));
    expect(corpos[0]).toEqual({ cep: "06010-000", autorizaRegiao: true, versaoTextoRegiao: POLITICA_PRIVACIDADE.versao });
    expect(await screen.findByText("Região: autorizada (Centro, Osasco - SP).")).toBeInTheDocument();
  });
});
