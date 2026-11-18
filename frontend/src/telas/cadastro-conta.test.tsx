import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { Cadastro } from "./Cadastro";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  definirBusca("");
  servidor.use(
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
    http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "F" }, papeis: ["CLIENTE"] })),
  );
});

async function enviar(user: ReturnType<typeof renderizar>["user"], senha = "senha-bem-longa-123") {
  await user.type(screen.getByLabelText("Nome"), "Fulana de Tal");
  await user.type(screen.getByLabelText("E-mail"), "fulana@exemplo.test");
  await user.type(screen.getByLabelText("WhatsApp"), "11900000002");
  await user.type(screen.getByLabelText("Senha"), senha);
  await user.click(screen.getByRole("button", { name: "Criar conta" }));
}

describe("cadastro", () => {
  test("A35: e-mail repetido e erros da API caem no campo certo; senha de 10 a 72 bytes é validada antes; 201 leva ao destino; só o login falhando manda para /entrar", async () => {
    let posts = 0;
    let resposta: () => Response = () => erro(409, "EMAIL_JA_CADASTRADO");
    servidor.use(http.post(`${BFF}/auth/cadastro`, () => { posts++; return resposta(); }));

    // e-mail repetido: no campo e-mail, sem revelar mais nada
    let r = renderizar(<Cadastro />);
    await enviar(r.user);
    const email = await screen.findByLabelText("E-mail");
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
    expect(email).toHaveAccessibleDescription(/Já existe uma conta com este e-mail/);
    r.unmount();

    // errors[] nos campos
    resposta = () => erro(400, "VALIDACAO_INVALIDA", { errors: [{ field: "whatsapp", message: "WhatsApp inválido" }, { field: "nome", message: "nome curto" }] });
    r = renderizar(<Cadastro />);
    await enviar(r.user);
    await waitFor(() => expect(screen.getByLabelText("WhatsApp")).toHaveAttribute("aria-invalid", "true"));
    expect(screen.getByLabelText("Nome")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Nome")).toHaveFocus();
    r.unmount();
    const antes = posts;

    // senha de 9 caracteres e de 73 bytes: barradas no navegador
    for (const senha of ["123456789", "a" + "é".repeat(36)]) {
      r = renderizar(<Cadastro />);
      await enviar(r.user, senha);
      expect(await screen.findByText(/de 10 a 72 caracteres \(acentos/)).toBeInTheDocument();
      r.unmount();
    }
    expect(posts).toBe(antes);
    expect(new TextEncoder().encode("a" + "é".repeat(36)).length).toBe(73);

    // 201 com sessão: vai para onde o cliente estava (voltar) ou Meus agendamentos
    resposta = () => HttpResponse.json({ sessao: true, usuario: { id: "u", nome: "F", papeis: ["CLIENTE"] }, deveTrocarSenha: false }, { status: 201 }) as never;
    definirBusca("voltar=%2Fagendar%2Fdados%3FservicoId%3D1");
    r = renderizar(<Cadastro />);
    await enviar(r.user);
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/agendar/dados?servicoId=1"));
    r.unmount();
    roteador.push.mockClear();
    definirBusca("voltar=%2F%2Fevil.com");
    r = renderizar(<Cadastro />);
    await enviar(r.user);
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/meus-agendamentos"));
    r.unmount();
    roteador.push.mockClear();

    // só o login falhou (BFF devolve 201 sem sessão)
    definirBusca("");
    resposta = () => HttpResponse.json({ sessao: false }, { status: 201 }) as never;
    r = renderizar(<Cadastro />);
    await enviar(r.user);
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/entrar?aviso=conta-criada"));
  }, 30000);
});
