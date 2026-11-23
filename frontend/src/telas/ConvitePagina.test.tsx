import { screen, waitFor } from "@testing-library/react";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { proxy } from "@/proxy";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { Convite } from "./Convite";
import { Entrar } from "./Entrar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const CODIGO = "q3tD7mYb2uVn0pLwXc8sKfHrJ1aZgE5iO9vTyNdB4Mk";
const SENHA = "senha-bem-longa-2026";

function abrirLink(fragmento = `#${CODIGO}`) {
  window.history.replaceState(null, "", `/convite${fragmento}`);
}

beforeEach(() => {
  zerarNavegacao();
  window.sessionStorage.clear();
  abrirLink();
});
afterEach(() => window.history.replaceState(null, "", "/"));

describe("página do convite do barbeiro", () => {
  test("A36: abrir o link não chama a API (nenhum pedido sai), tira o código da barra de endereço e o guarda só na aba; a página sai sem cache e sem Referer", async () => {
    const pedidos: string[] = [];
    servidor.events.on("request:start", ({ request }) => void pedidos.push(request.url));
    renderizar(<Convite />, ANONIMO);

    expect(await screen.findByRole("heading", { name: "Criar minha senha" })).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 50));
    expect(pedidos).toEqual([]);
    expect(window.location.hash).toBe("");
    expect(window.location.href).not.toContain(CODIGO);
    servidor.events.removeAllListeners();

    const resposta = proxy(new NextRequest("http://localhost:3000/convite"));
    expect(resposta.headers.get("referrer-policy")).toBe("no-referrer");
    expect(resposta.headers.get("cache-control")).toBe("no-store");
  });

  test("A36: sem código, com código em formato errado ou depois de aceito, a página mostra uma frase só e o caminho para entrar, sem chamar a API", async () => {
    abrirLink("");
    renderizar(<Convite />, ANONIMO);
    expect(await screen.findByText(/Este link não vale mais ou não pode ser usado aqui/)).toBeInTheDocument();
    expect(screen.getByText(/Peça outro convite ao dono/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Já tenho conta no site: entrar" })).toHaveAttribute("href", "/entrar");
  });

  test("A37: e-mail, senha e confirmação: o BFF recebe { codigo, email, senha } e o sucesso leva ao login com a mensagem certa", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/convite`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderizar(<Convite />, ANONIMO);
    await user.type(await screen.findByLabelText("Seu e-mail"), "barbeiro.novo@exemplo.test");
    await user.type(screen.getByLabelText("Senha"), SENHA);
    await user.type(screen.getByLabelText("Repita a senha"), SENHA);
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));

    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/entrar?aviso=convite-aceito"));
    expect(corpos).toEqual([{ codigo: CODIGO, email: "barbeiro.novo@exemplo.test", senha: SENHA }]);
    expect(window.sessionStorage.getItem("ah_convite")).toBeNull();

    definirBusca("aviso=convite-aceito");
    const { findByText } = renderizar(<Entrar />, ANONIMO);
    expect(await findByText("Senha criada. Entre com seu e-mail e a nova senha.")).toBeInTheDocument();
  });

  test("A37: senha curta e confirmação diferente travam no navegador (sem chamar a API); 400 do servidor aparece no campo da senha", async () => {
    let chamadas = 0;
    servidor.use(
      http.post(`${BFF}/convite`, () => {
        chamadas++;
        return erro(400, "VALIDACAO_INVALIDA", { errors: [{ field: "senha", message: "deve ter de 10 a 72 bytes" }] });
      }),
    );
    const { user } = renderizar(<Convite />, ANONIMO);
    await user.type(await screen.findByLabelText("Seu e-mail"), "barbeiro.novo@exemplo.test");
    await user.type(screen.getByLabelText("Senha"), "curta");
    await user.type(screen.getByLabelText("Repita a senha"), "curta");
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));
    expect(await screen.findByText(/A senha precisa ter de 10 a 72/)).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Senha"));
    await user.type(screen.getByLabelText("Senha"), SENHA);
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));
    expect(await screen.findByText("As duas senhas precisam ser iguais.")).toBeInTheDocument();
    expect(chamadas).toBe(0);

    await user.clear(screen.getByLabelText("Repita a senha"));
    await user.type(screen.getByLabelText("Repita a senha"), SENHA);
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));
    await waitFor(() => expect(chamadas).toBe(1));
    expect(await screen.findByText(/deve ter de 10 a 72 bytes/)).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveAttribute("aria-invalid", "true");
    expect(roteador.push).not.toHaveBeenCalled();
  });

  test("A37: 422 mostra uma frase só (sem dizer o motivo) com o caminho 'Já tenho conta no site: entrar'; 429 mostra o tempo de espera e trava o botão", async () => {
    let resposta: () => Response = () => erro(422, "CONVITE_INVALIDO");
    servidor.use(http.post(`${BFF}/convite`, () => resposta()));
    const { user } = renderizar(<Convite />, ANONIMO);
    await user.type(await screen.findByLabelText("Seu e-mail"), "barbeiro.novo@exemplo.test");
    await user.type(screen.getByLabelText("Senha"), SENHA);
    await user.type(screen.getByLabelText("Repita a senha"), SENHA);
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));

    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Este link não vale mais ou não pode ser usado aqui.");
    expect(alerta).not.toHaveTextContent(/vencid|revogad|inexistent|já foi usado|e-mail não/i);
    expect(screen.getByRole("link", { name: "Já tenho conta no site: entrar" })).toHaveAttribute("href", "/entrar?voltar=%2Fconvite");

    resposta = () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "30" });
    await user.type(screen.getByLabelText("Senha"), SENHA); // o envio apaga as senhas da tela
    await user.type(screen.getByLabelText("Repita a senha"), SENHA);
    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));
    expect(await screen.findByText(/Tente de novo em [0-9]+ s/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Criar minha senha" })).toBeDisabled();
  });

  test("A37: quem já tem conta (logado) vê 'Aceitar convite', só o e-mail e nenhuma senha; o BFF recebe só { codigo, email } e o aceite leva ao login com o aviso do papel novo", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/convite`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return new HttpResponse(null, { status: 204 });
      }),
      http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: false })),
    );
    const { user } = renderizar(<Convite />, CLIENTE);
    expect(await screen.findByRole("heading", { name: "Aceitar convite" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    expect(screen.getByText(/Sua senha não muda/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Seu e-mail"), "ana@exemplo.test");
    await user.click(screen.getByRole("button", { name: "Aceitar convite" }));

    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/entrar?aviso=convite-aceito-conta"));
    expect(corpos).toEqual([{ codigo: CODIGO, email: "ana@exemplo.test" }]);
  });

  test("A38: rótulos ligados aos campos, senha com autocomplete new-password e botão mostrar, erro com role alert, foco no primeiro campo com erro", async () => {
    const { user } = renderizar(<Convite />, ANONIMO);
    const email = await screen.findByLabelText("Seu e-mail");
    const senha = screen.getByLabelText("Senha");
    const confirmacao = screen.getByLabelText("Repita a senha");
    expect(email).toHaveAttribute("autocomplete", "username");
    expect(senha).toHaveAttribute("autocomplete", "new-password");
    expect(confirmacao).toHaveAttribute("autocomplete", "new-password");
    expect(senha).toHaveAttribute("type", "password");

    const mostrar = screen.getByRole("button", { name: "Mostrar a senha" });
    expect(mostrar).toHaveAttribute("aria-pressed", "false");
    await user.click(mostrar);
    expect(senha).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Esconder a senha" })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "Criar minha senha" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Confira os campos marcados.");
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email).toHaveAccessibleDescription(/Digite o seu e-mail/);
    await waitFor(() => expect(email).toHaveFocus());
  });
});
