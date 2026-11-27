import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { Menu } from "@/componentes/Menu";
import { BFF, delay, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { Entrar } from "./Entrar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  definirBusca("");
  servidor.use(
    http.get(`${BFF}/sessao`, () =>
      HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "N" }, papeis: ["CLIENTE"] }),
    ),
  );
});

const login = (r: () => Response | Promise<Response>) => servidor.use(http.post(`${BFF}/auth/login`, () => r()));
const resposta = (papeis: string[], deveTrocarSenha = false) =>
  HttpResponse.json({ usuario: { id: "u", nome: "N", papeis }, deveTrocarSenha });

async function entrar(user: ReturnType<typeof renderizar>["user"]) {
  await user.type(screen.getByLabelText("E-mail"), "alguem@exemplo.test");
  await user.type(screen.getByLabelText("Senha"), "uma-senha-qualquer");
  await user.click(screen.getByRole("button", { name: "Entrar" }));
}

describe("login e logout", () => {
  test("A36: cada papel vai ao seu destino, credencial errada tem uma mensagem só, 429 mostra a espera, senha com autocomplete certo e Sair derruba a sessão", async () => {
    // campos
    let r = renderizar(<Entrar />);
    expect(screen.getByLabelText("Senha")).toHaveAttribute("type", "password");
    expect(screen.getByLabelText("Senha")).toHaveAttribute("autocomplete", "current-password");
    expect(screen.getByLabelText("E-mail")).toHaveAttribute("autocomplete", "username");
    r.unmount();

    const destinos: [string[], boolean, string, string][] = [
      [["CLIENTE"], false, "", "/meus-agendamentos"],
      [["CLIENTE"], false, "voltar=%2Fagendar%2Fdados", "/agendar/dados"],
      [["CLIENTE"], false, "voltar=%2F%2Fevil.com", "/meus-agendamentos"],
      [["BARBEIRO"], false, "voltar=%2Fagendar", "/painel"],
      [["ADMIN"], false, "", "/painel"],
      [["ADMIN"], true, "", "/conta/trocar-senha"],
    ];
    for (const [papeis, troca, busca, esperado] of destinos) {
      zerarNavegacao();
      definirBusca(busca);
      login(() => resposta(papeis, troca));
      r = renderizar(<Entrar />);
      await entrar(r.user);
      await waitFor(() => expect(roteador.push).toHaveBeenCalledWith(esperado), { timeout: 3000 });
      r.unmount();
    }

    // credencial errada: uma mensagem só, igual para e-mail e senha
    login(() => erro(401, "CREDENCIAIS_INVALIDAS"));
    r = renderizar(<Entrar />);
    await entrar(r.user);
    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent(
      "E-mail ou senha não conferem. Confira e tente de novo; o botão Mostrar a senha ajuda.",
    );
    expect(alerta.textContent).not.toMatch(/não existe|não cadastrado|senha errada|e-mail errado/i);
    r.unmount();

    // 429: espera
    login(() => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "30" }));
    r = renderizar(<Entrar />);
    await entrar(r.user);
    expect((await screen.findAllByText(/Tente de novo em \d+ s/)).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Entrar" })).toBeDisabled();
    r.unmount();

    // logout: chama a rota, volta à home e o menu muda
    zerarNavegacao();
    let saiu = 0;
    servidor.use(
      http.post(`${BFF}/auth/logout`, async () => {
        saiu++;
        await delay(5);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const m = renderizar(<Menu />, CLIENTE);
    expect(screen.getByRole("link", { name: "Meus agendamentos" })).toBeInTheDocument();
    await m.user.click(screen.getByRole("button", { name: "Sair" }));
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/"));
    expect(saiu).toBe(1);
    expect(await screen.findByRole("link", { name: "Entrar ou criar conta" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sair" })).toBeNull();
  });
});
