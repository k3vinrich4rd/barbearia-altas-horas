import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { FormEvent } from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { Cadastro } from "@/telas/Cadastro";
import { Convite } from "@/telas/Convite";
import { Entrar } from "@/telas/Entrar";
import { TrocarSenha } from "@/telas/TrocarSenha";
import { CampoSenha } from "./CampoSenha";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("@/api/navegacao", () => ({ irPara: vi.fn() }));
ligarServidor();

const CODIGO = "q3tD7mYb2uVn0pLwXc8sKfHrJ1aZgE5iO9vTyNdB4Mk";

beforeEach(() => {
  zerarNavegacao();
  servidor.use(
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
    http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: false })),
    http.get(`${BFF}/me`, () =>
      HttpResponse.json({
        id: "u",
        nome: "Cliente",
        email: "c@exemplo.test",
        papeis: ["CLIENTE"],
        deveTrocarSenha: false,
      }),
    ),
  );
});
afterEach(() => window.history.replaceState(null, "", "/"));

/** As 4 telas com campo de senha e o autocomplete que cada campo precisa ter (A28, A30). */
const TELAS = [
  { nome: "Entrar", abrir: () => renderizar(<Entrar />, ANONIMO), campos: { Senha: "current-password" } },
  { nome: "Cadastro", abrir: () => renderizar(<Cadastro />, ANONIMO), campos: { Senha: "new-password" } },
  {
    nome: "Trocar senha",
    abrir: () => renderizar(<TrocarSenha />, CLIENTE),
    campos: { "Senha atual": "current-password", "Nova senha": "new-password" },
  },
  {
    nome: "Convite",
    abrir: () => {
      window.history.replaceState(null, "", `/convite#${CODIGO}`);
      return renderizar(<Convite />, ANONIMO);
    },
    campos: { Senha: "new-password", "Repita a senha": "new-password" },
  },
] as const;

function botaoDoCampo(campo: HTMLElement) {
  return screen
    .getAllByRole("button", { name: /(Mostrar|Esconder) a senha/ })
    .find((b) => b.getAttribute("aria-controls") === campo.id)!;
}

function Formulario({ aoEnviar }: { aoEnviar: (tipoNoEnvio: string | null) => void }) {
  function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    aoEnviar(e.currentTarget.querySelector("#teste-senha")!.getAttribute("type"));
  }
  return (
    <form onSubmit={enviar} aria-label="Teste">
      <CampoSenha id="teste-senha" rotulo="Senha" autoComplete="current-password" />
      <button type="submit">Enviar</button>
    </form>
  );
}

describe("CampoSenha: o olhinho em todo campo de senha (Fatia 14B)", () => {
  test("A28: Entrar (1), Cadastro (1), Trocar senha (2) e Convite (2): cada campo de senha tem o seu botão Mostrar a senha, type=button, aria-pressed=false e aria-controls = id do campo", async () => {
    for (const tela of TELAS) {
      const { unmount } = tela.abrir();
      const rotulos = Object.keys(tela.campos);
      await screen.findByLabelText(rotulos[0]);
      const botoes = screen.getAllByRole("button", { name: "Mostrar a senha" });
      expect(botoes, tela.nome).toHaveLength(rotulos.length);
      for (const rotulo of rotulos) {
        const campo = screen.getByLabelText(rotulo);
        expect(campo, `${tela.nome}: ${rotulo}`).toHaveAttribute("type", "password");
        const botao = botaoDoCampo(campo);
        expect(botao, `${tela.nome}: ${rotulo}`).toBeDefined();
        expect(botao).toHaveAttribute("type", "button");
        expect(botao).toHaveAttribute("aria-pressed", "false");
        expect(botao).toHaveAttribute("aria-controls", campo.id);
      }
      unmount();
    }
  });

  test("A29: com 'segredo1' digitado, Mostrar a senha vira type=text com o mesmo valor, o botão diz Esconder a senha com aria-pressed=true e o foco fica no botão; tocar de novo volta tudo", async () => {
    const user = userEvent.setup();
    render(<Formulario aoEnviar={() => {}} />);
    const campo = screen.getByLabelText("Senha");
    await user.type(campo, "segredo1");
    await user.click(screen.getByRole("button", { name: "Mostrar a senha" }));

    expect(campo).toHaveAttribute("type", "text");
    expect(campo).toHaveValue("segredo1");
    const esconder = screen.getByRole("button", { name: "Esconder a senha" });
    expect(esconder).toHaveAttribute("aria-pressed", "true");
    expect(esconder).toHaveFocus();

    await user.click(esconder);
    expect(campo).toHaveAttribute("type", "password");
    expect(campo).toHaveValue("segredo1");
    const mostrar = screen.getByRole("button", { name: "Mostrar a senha" });
    expect(mostrar).toHaveAttribute("aria-pressed", "false");
    expect(mostrar).toHaveFocus();
  });

  test("A30: Entrar e Senha atual com current-password; Cadastro, Nova senha e Convite com new-password; igual com a senha escondida e visível", async () => {
    for (const tela of TELAS) {
      const { user, unmount } = tela.abrir();
      await screen.findByLabelText(Object.keys(tela.campos)[0]);
      for (const [rotulo, esperado] of Object.entries(tela.campos)) {
        const campo = screen.getByLabelText(rotulo);
        expect(campo, `${tela.nome}: ${rotulo} escondida`).toHaveAttribute("autocomplete", esperado);
        await user.click(botaoDoCampo(campo));
        expect(campo).toHaveAttribute("type", "text");
        expect(campo, `${tela.nome}: ${rotulo} visível`).toHaveAttribute("autocomplete", esperado);
      }
      unmount();
    }
  });

  test("A31: com a senha visível, enviar (botão ou Enter) volta o campo a type=password antes do envio; o botão de mostrar não envia", async () => {
    const user = userEvent.setup();
    const enviados: (string | null)[] = [];
    render(<Formulario aoEnviar={(tipo) => enviados.push(tipo)} />);
    const campo = screen.getByLabelText("Senha");
    await user.type(campo, "segredo1");

    await user.click(screen.getByRole("button", { name: "Mostrar a senha" }));
    expect(enviados).toEqual([]); // o botão de mostrar é type=button
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    expect(enviados).toEqual(["password"]);
    expect(campo).toHaveAttribute("type", "password");

    await user.click(screen.getByRole("button", { name: "Mostrar a senha" }));
    await user.type(campo, "{Enter}");
    expect(enviados).toEqual(["password", "password"]);
    expect(
      within(screen.getByRole("form", { name: "Teste" })).getByRole("button", { name: "Mostrar a senha" }),
    ).toHaveAttribute("aria-pressed", "false");
  });

  test("A32: só teclado: Tab a partir do campo leva ao botão de mostrar; Enter e Espaço alternam", async () => {
    const user = userEvent.setup();
    render(<Formulario aoEnviar={() => {}} />);
    const campo = screen.getByLabelText("Senha");
    await user.click(campo);
    await user.tab();
    const botao = screen.getByRole("button", { name: "Mostrar a senha" });
    expect(botao).toHaveFocus();

    await user.keyboard("{Enter}");
    expect(campo).toHaveAttribute("type", "text");
    expect(botao).toHaveAttribute("aria-pressed", "true");
    await user.keyboard(" ");
    expect(campo).toHaveAttribute("type", "password");
    expect(botao).toHaveAttribute("aria-pressed", "false");
  });
});
