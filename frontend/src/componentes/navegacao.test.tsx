import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { render } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirCaminho, zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { BarraAgendar } from "./BarraAgendar";
import { Menu, SECOES_DA_HOME } from "./Menu";
import { PainelDeAcessibilidade } from "./PainelDeAcessibilidade";
import NaoEncontrada from "@/app/not-found";
import Home from "@/app/page";
import { formatarWhatsapp, Rodape } from "./Rodape";

// Navegação (feature/ux-navegacao): menu com as âncoras do PDF, Disclosure no celular, rodapé do PDF.
vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
});

describe("menu do site", () => {
  test("A2: tem as âncoras da home (Serviços, Barbeiro, Galeria, Trajetória, Dúvidas, Onde estamos), sem A história nem Como funciona, os links da conta e um único Agendar horário", () => {
    renderizar(<Menu />, ANONIMO);
    const principal = screen.getByRole("navigation", { name: "Principal" });
    const links = within(principal).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual([
      "Serviços",
      "Barbeiro",
      "Galeria",
      "Trajetória",
      "Dúvidas",
      "Onde estamos",
    ]);
    expect(links.map((l) => l.getAttribute("href"))).toEqual(SECOES_DA_HOME.map((s) => s.href));
    expect(links.find((l) => l.textContent === "Trajetória")).toHaveAttribute("href", "/#trajetoria");
    expect(within(principal).queryByRole("link", { name: "A história" })).toBeNull();
    expect(within(principal).queryByRole("link", { name: "Como funciona" })).toBeNull();
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

describe("barra fixa Agendar horário (Fatia 12, E8)", () => {
  test("A6: não há barra fixa em /agendar, /agendar/dados, /entrar, /cadastro e /painel; na home e nas outras telas ela leva a /agendar", () => {
    for (const caminho of [
      "/agendar",
      "/agendar/dados",
      "/agendar/pagamento/r1",
      "/entrar",
      "/cadastro",
      "/painel",
      "/painel/agenda",
    ]) {
      definirCaminho(caminho);
      const { container, unmount } = render(<BarraAgendar />);
      expect(container.querySelector(".barra-agendar"), caminho).toBeNull();
      unmount();
    }
    for (const caminho of ["/", "/servicos", "/reserva"]) {
      definirCaminho(caminho);
      const { container, unmount } = render(<BarraAgendar />);
      expect(container.querySelector(".barra-agendar"), caminho).not.toBeNull();
      expect(screen.getByRole("link", { name: "Agendar horário" }), caminho).toHaveAttribute("href", "/agendar");
      unmount();
    }
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

  test("A20: Onde estamos tem endereço, horário de terça a sábado e o link Abrir no mapa em nova aba, sem iframe", () => {
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([])));
    const { container } = render(<Rodape />);
    const onde = screen.getByRole("region", { name: "Onde estamos" });
    expect(onde).toHaveTextContent("R. São Judas Tadeu, 313");
    expect(onde).toHaveTextContent("Osasco/SP");
    expect(screen.getByRole("region", { name: "Horário de funcionamento" })).toHaveTextContent(/Terça a sábado/);
    const mapa = within(onde).getByRole("link", { name: /Abrir no mapa/ });
    expect(mapa).toHaveAttribute("target", "_blank");
    expect(mapa).toHaveAttribute("rel", "noopener noreferrer");
    expect(mapa.getAttribute("href")).toContain("google.com/maps");
    expect(container.querySelector("iframe")).toBeNull();
  });

  test("sem número na API, a linha do WhatsApp some", async () => {
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([])));
    render(<Rodape />);
    expect(await screen.findByRole("heading", { name: "Contato" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /abre o WhatsApp/ })).toBeNull();
    expect(screen.queryByText(/\(11\)/)).toBeNull();
  });

  test("o título do bloco de contato não muda quando o número chega, e o rodapé tem os blocos nomeados", async () => {
    servidor.use(
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([{ id: "b1", nomeExibicao: "Eduardo Candido", whatsapp: "11900000000" }]),
      ),
    );
    render(<Rodape />);
    expect(screen.getByRole("heading", { name: "Contato" })).toBeInTheDocument();
    await screen.findByRole("link", { name: /abre o WhatsApp/ });
    expect(screen.getByRole("heading", { name: "Contato" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Instagram e WhatsApp" })).toBeNull();
    expect(screen.getByRole("region", { name: "Onde estamos" })).toHaveAttribute("id", "onde-estamos");
    expect(screen.getByRole("region", { name: "Horário de funcionamento" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar ao topo" })).toHaveAttribute("href", "#conteudo");
  });

  test("formata o número público para exibição", () => {
    expect(formatarWhatsapp("11906941689")).toBe("(11) 90694-1689");
    expect(formatarWhatsapp("5511906941689")).toBe("(11) 90694-1689");
    expect(formatarWhatsapp("1133334444")).toBe("(11) 3333-4444");
  });
});

describe("página não encontrada", () => {
  test("mostra o título e os caminhos de volta", () => {
    render(<NaoEncontrada />);
    expect(screen.getByRole("heading", { level: 1, name: "Página não encontrada" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar para o início" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Agendar horário" })).toHaveAttribute("href", "/agendar");
    expect(screen.getByRole("link", { name: "Consultar reserva" })).toHaveAttribute("href", "/reserva");
  });
});

describe("regiões da home", () => {
  test("a home com menu e rodapé não tem duas regiões ou navegações com o mesmo nome", () => {
    renderizar(
      <>
        <Menu />
        <main>
          <Home />
        </main>
        <Rodape />
      </>,
      ANONIMO,
    );
    const nomes = [
      ...screen.getAllByRole("region").map((r) => r.getAttribute("aria-label") ?? ""),
      ...screen.getAllByRole("navigation").map((n) => n.getAttribute("aria-label") ?? ""),
    ];
    // as seções nomeadas por aria-labelledby: pega o texto do título que as nomeia
    const porTitulo = screen.getAllByRole("region").map((r) => {
      const id = r.getAttribute("aria-labelledby");
      return id ? (document.getElementById(id)?.textContent ?? "") : "";
    });
    const todos = [...nomes, ...porTitulo].filter(Boolean);
    expect(todos.length).toBeGreaterThan(8);
    expect(new Set(todos).size).toBe(todos.length);
    expect(screen.getAllByRole("region", { name: "Onde estamos" })).toHaveLength(1);
    expect(screen.getAllByText("Horário de funcionamento")).toHaveLength(1);
  });
});
