import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { render } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirCaminho, zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { Menu, SECOES_DA_HOME } from "./Menu";
import { PainelDeAcessibilidade } from "./PainelDeAcessibilidade";
import { formatarWhatsapp, Rodape } from "./Rodape";

// Navegação (feature/ux-navegacao): menu com as âncoras do PDF, Disclosure no celular, rodapé do PDF.
vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
});

describe("menu do site", () => {
  test("tem as âncoras da home na ordem do PDF, os links da conta e um único Agendar horário", () => {
    renderizar(<Menu />, ANONIMO);
    const principal = screen.getByRole("navigation", { name: "Principal" });
    const links = within(principal).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual([
      "A história",
      "Serviços",
      "Como funciona",
      "Galeria",
      "Dúvidas",
      "Onde estamos",
    ]);
    expect(links.map((l) => l.getAttribute("href"))).toEqual(SECOES_DA_HOME.map((s) => s.href));
    const conta = screen.getByRole("navigation", { name: "Conta" });
    expect(within(conta).getByRole("link", { name: "Entrar" })).toHaveAttribute("href", "/entrar");
    expect(within(conta).getByRole("link", { name: "Consultar reserva" })).toHaveAttribute("href", "/reserva");
    expect(screen.getAllByRole("link", { name: "Agendar horário" })).toHaveLength(1);
  });

  test("o botão Menu é um Disclosure: aria-expanded alterna, Esc fecha e devolve o foco ao botão", async () => {
    const { user } = renderizar(<Menu />, ANONIMO);
    const botao = screen.getByRole("button", { name: "Menu" });
    expect(botao).toHaveAttribute("aria-expanded", "false");
    expect(botao).toHaveAttribute("aria-controls", "menu-do-site");
    await user.click(botao);
    expect(screen.getByRole("button", { name: "Fechar menu" })).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById("menu-do-site")).toHaveAttribute("data-aberto", "true");
    await user.keyboard("{Escape}");
    expect(screen.getByRole("button", { name: "Menu" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByRole("button", { name: "Menu" })).toHaveFocus();
  });

  test("marca a página atual com aria-current e mostra os links do cliente logado", async () => {
    definirCaminho("/meus-agendamentos");
    servidor.use(
      http.get(`${BFF}/sessao`, () =>
        HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "N" }, papeis: ["CLIENTE"] }),
      ),
    );
    renderizar(<Menu />, CLIENTE);
    expect(await screen.findByRole("link", { name: "Meus agendamentos" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("link", { name: "Entrar" })).toBeNull();
  });
});

describe("painel de acessibilidade", () => {
  test("no celular, as opções ficam atrás de um botão Acessibilidade com aria-expanded", async () => {
    const user = userEvent.setup();
    render(<PainelDeAcessibilidade />);
    const alternar = screen.getByRole("button", { name: /Acessibilidade/ });
    expect(alternar).toHaveAttribute("aria-expanded", "false");
    await user.click(alternar);
    expect(alternar).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById("painel-a11y-opcoes")).toHaveAttribute("data-aberto", "true");
    expect(screen.getByRole("button", { name: "Alto contraste" })).toBeInTheDocument();
  });
});

describe("rodapé", () => {
  test("mostra Instagram da loja e do Eduardo, o mapa em link e o crédito; o WhatsApp vem da API", async () => {
    servidor.use(
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([{ id: "b1", nomeExibicao: "Eduardo Candido", whatsapp: "11900000000" }]),
      ),
    );
    render(<Rodape />);
    expect(screen.getByRole("link", { name: "@altashorascabeleireiros" })).toHaveAttribute(
      "href",
      "https://www.instagram.com/altashorascabeleireiros/",
    );
    expect(screen.getByRole("link", { name: "@eduardo_candido_barber" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Abrir no mapa (nova aba)" })).toHaveAttribute("target", "_blank");
    expect(screen.getByText(/Agenda online feita por Kevin Richard, filho do Eduardo\./)).toBeInTheDocument();
    const whats = await screen.findByRole("link", { name: /\(11\) 90000-0000 \(abre o WhatsApp\)/ });
    expect(whats).toHaveAttribute("href", "https://wa.me/5511900000000");
  });

  test("sem número na API, a linha do WhatsApp some", async () => {
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([])));
    render(<Rodape />);
    expect(await screen.findByRole("heading", { name: "Instagram" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /abre o WhatsApp/ })).toBeNull();
  });

  test("formata o número público para exibição", () => {
    expect(formatarWhatsapp("11906941689")).toBe("(11) 90694-1689");
    expect(formatarWhatsapp("5511906941689")).toBe("(11) 90694-1689");
    expect(formatarWhatsapp("1133334444")).toBe("(11) 3333-4444");
  });
});
