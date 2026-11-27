// Fatia 16, F3: herói enxuto e "Como marcar" (A8 em parte, A11, A12 em parte; spec 09, TJ-1, TJ-3 a TJ-5).
import { act, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { ComoMarcar } from "@/componentes/ComoMarcar";
import { SUBTITULO_DA_HOME } from "@/conteudo/historia";
import { BARBEIRO, SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { SessaoProvider } from "@/componentes/Sessao";
import { CLIENTE, renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const COM_WHATSAPP = [{ ...BARBEIRO, nomeExibicao: "Eduardo Candido", whatsapp: "11996941689" }];

function api(barbeiros: unknown[]) {
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json(barbeiros)),
    http.get(`${BFF}/alcance`, () =>
      HttpResponse.json({ atualizadoEm: "2026-11-27T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }),
    ),
  );
}

describe("home: herói enxuto (Fatia 16, F3)", () => {
  test("TJ-1 e A8: subtítulo do PDF, um botão e o link dos serviços; sem fatos, sem Como funciona, sem WhatsApp e sem foto no herói", async () => {
    api(COM_WHATSAPP);
    const { container } = renderizar(<Home />);
    await screen.findByText("Descoloração / Platinado (química)");
    const heroi = container.querySelector("#inicio") as HTMLElement;

    expect(within(heroi).getByText(SUBTITULO_DA_HOME)).toBeInTheDocument();
    expect(heroi).not.toHaveTextContent("sem esperar resposta no WhatsApp");
    expect(within(heroi).getAllByRole("link", { name: "Agendar horário" })).toHaveLength(1);
    expect(within(heroi).getByRole("link", { name: "Ver serviços e preços" })).toHaveAttribute("href", "#servicos");
    expect(within(heroi).queryByRole("list")).toBeNull();
    expect(within(heroi).queryByRole("img")).toBeNull(); // o selo do logo é decorativo (alt="")
    expect(heroi.querySelector('a[href^="https://wa.me/"]')).toBeNull();
    expect(heroi).not.toHaveTextContent("R. São Judas Tadeu");
    expect(screen.queryByRole("list", { name: "Como funciona" })).toBeNull();
    expect(screen.queryByRole("heading", { name: "Antes de agendar" })).toBeNull();
  });
});

describe("home: Como marcar (Fatia 16, F3)", () => {
  test("A11: três cartões na ordem do PDF; só Agendar sem cadastro é principal; cada botão leva ao lugar certo", async () => {
    api(COM_WHATSAPP);
    renderizar(<ComoMarcar />);
    const secao = screen.getByRole("region", { name: "Três jeitos de marcar" });
    expect(within(secao).getByText("03 · Como marcar")).toBeInTheDocument();
    const cartoes = within(secao).getAllByRole("article");
    expect(cartoes.map((c) => within(c).getByRole("heading", { level: 3 }).textContent)).toEqual([
      "Criar conta e agendar",
      "Agendar sem cadastro",
      "Combinar com o Eduardo",
    ]);
    expect(within(cartoes[0]).getByRole("link", { name: "Criar conta ou entrar" })).toHaveAttribute("href", "/entrar");
    expect(within(cartoes[1]).getByRole("link", { name: "Agendar sem cadastro" })).toHaveAttribute("href", "/agendar");
    expect(within(cartoes[2]).getByRole("link", { name: "Ver horários livres" })).toHaveAttribute("href", "/agendar");
    expect(secao.querySelectorAll(".botao-principal")).toHaveLength(1);
    expect(within(cartoes[1]).getByRole("link", { name: "Agendar sem cadastro" })).toHaveClass("botao-principal");
  });

  test("A11: Antes de pagar tem só prazo, cancelamento e antecedência, em <details> nativos", async () => {
    api(COM_WHATSAPP);
    const { user, container } = renderizar(<ComoMarcar />);
    const bloco = container.querySelector("#duvidas") as HTMLElement; // links antigos /#duvidas chegam aqui
    expect(within(bloco).getByRole("heading", { level: 3, name: "Antes de pagar" })).toBeInTheDocument();
    const itens = bloco.querySelectorAll("details");
    expect([...itens].map((d) => d.querySelector("summary")?.textContent?.replace("+", "").trim())).toEqual([
      "E se eu não pagar no prazo?",
      "Posso cancelar ou remarcar?",
      "Com quanta antecedência posso agendar?",
    ]);
    expect(bloco).not.toHaveTextContent("Preciso criar uma conta?");
    expect(bloco).not.toHaveTextContent("Como eu pago?");
    expect(itens[0].open).toBe(false);
    await user.click(within(itens[0] as HTMLElement).getByText("E se eu não pagar no prazo?"));
    expect(itens[0].open).toBe(true);
  });

  test("TJ-5 e A12: com número, um só link de WhatsApp na seção, no cartão 3, avisando que abre o WhatsApp", async () => {
    api(COM_WHATSAPP);
    renderizar(<ComoMarcar />);
    const link = await screen.findByRole("link", { name: "Ou abrir o WhatsApp direto (abre o WhatsApp)" });
    expect(link).toHaveAttribute("href", "https://wa.me/5511996941689");
    const secao = screen.getByRole("region", { name: "Três jeitos de marcar" });
    expect(secao.querySelectorAll('a[href^="https://wa.me/"]')).toHaveLength(1);
    expect(within(screen.getAllByRole("article")[2]).getByRole("link", { name: /WhatsApp direto/ })).toBe(link);
  });

  test("TJ-5: sem número na API, o link some e o cartão 3 continua com Ver horários livres", async () => {
    api([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido", whatsapp: null }]);
    renderizar(<ComoMarcar />);
    await waitFor(() => expect(screen.getByRole("link", { name: "Ver horários livres" })).toBeInTheDocument());
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole("link", { name: /WhatsApp direto/ })).toBeNull();
  });
});

describe("Como marcar: cartão 1 com sessão (Fatia 16, decisão 6)", () => {
  const cartao1 = () => screen.getByRole("heading", { level: 3, name: "Criar conta e agendar" }).closest("article")!;
  const acaoDoCartao1 = () => within(cartao1()).getByRole("link");

  test("A29: com sessão, o cartão 1 mostra \"Meus agendamentos\" (para /meus-agendamentos) no lugar de \"Criar conta ou entrar\"; sem sessão, o contrário; o resto do cartão não muda", () => {
    const { unmount } = renderizar(<ComoMarcar />, CLIENTE);
    expect(acaoDoCartao1()).toHaveAccessibleName("Meus agendamentos");
    expect(acaoDoCartao1()).toHaveAttribute("href", "/meus-agendamentos");
    expect(screen.queryByRole("link", { name: "Criar conta ou entrar" })).toBeNull();
    // sem CLS: o outro rótulo fica reservado no mesmo botão, invisível e fora da leitura
    const reserva = acaoDoCartao1().querySelector(".rotulo-reserva")!;
    expect(reserva).toHaveTextContent("Criar conta ou entrar");
    expect(reserva).toHaveAttribute("aria-hidden", "true");
    const comSessao = { classe: acaoDoCartao1().className, texto: cartao1().querySelector("p:not(.jeito-acoes):not(.sobretitulo)")!.textContent };
    unmount();

    renderizar(<ComoMarcar />);
    expect(acaoDoCartao1()).toHaveAccessibleName("Criar conta ou entrar");
    expect(acaoDoCartao1()).toHaveAttribute("href", "/entrar");
    expect(screen.queryByRole("link", { name: "Meus agendamentos" })).toBeNull();
    // mesmo botão, mesma classe e o mesmo texto do cartão nos dois estados: nada além do rótulo e do destino muda
    expect(acaoDoCartao1().className).toBe(comSessao.classe);
    expect(cartao1().querySelector("p:not(.jeito-acoes):not(.sobretitulo)")!.textContent).toBe(comSessao.texto);
    // os outros cartões não dependem da sessão
    expect(screen.getByRole("link", { name: "Agendar sem cadastro" })).toHaveAttribute("href", "/agendar");
  });

  test("A29: a sessão vem de /api/sessao (mesmo BFF do menu, sem cache); carregando ou com erro, o cartão fica no estado sem sessão", async () => {
    const pedidos: Request[] = [];
    let responder = () => HttpResponse.json({ error: "x" }, { status: 500 });
    servidor.use(
      http.get(`${BFF}/sessao`, ({ request }) => {
        pedidos.push(request);
        return responder();
      }),
    );
    render(
      <SessaoProvider>
        <ComoMarcar />
      </SessaoProvider>,
    );
    // carregando: estado sem sessão (é o mesmo HTML do servidor)
    expect(acaoDoCartao1()).toHaveAccessibleName("Criar conta ou entrar");
    await waitFor(() => expect(pedidos).toHaveLength(1));
    expect(pedidos[0].cache).toBe("no-store");
    expect(pedidos[0].credentials).toBe("same-origin");
    await new Promise((fim) => setTimeout(fim, 20));
    expect(acaoDoCartao1()).toHaveAccessibleName("Criar conta ou entrar");

    // volta do bfcache (pageshow com persisted): pergunta de novo
    responder = () => HttpResponse.json({ autenticado: true, usuario: { id: "u1", nome: "Cliente" }, papeis: ["CLIENTE"] });
    act(() => {
      window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true }));
    });
    await waitFor(() => expect(acaoDoCartao1()).toHaveAccessibleName("Meus agendamentos"));
    expect(pedidos).toHaveLength(2);
  });

  test("A29: depois do logout, ao voltar pelo bfcache, o cartão 1 aparece sem sessão; um pageshow sem persisted não pergunta de novo", async () => {
    let autenticado = true;
    let pedidos = 0;
    servidor.use(
      http.get(`${BFF}/sessao`, () => {
        pedidos++;
        return HttpResponse.json(autenticado ? { autenticado, usuario: { id: "u1", nome: "Cliente" }, papeis: ["CLIENTE"] } : { autenticado });
      }),
    );
    render(
      <SessaoProvider>
        <ComoMarcar />
      </SessaoProvider>,
    );
    await waitFor(() => expect(acaoDoCartao1()).toHaveAccessibleName("Meus agendamentos"));

    autenticado = false; // saiu em outra aba ou em outra página
    act(() => {
      window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: false }));
    });
    await new Promise((fim) => setTimeout(fim, 20));
    expect(pedidos).toBe(1);
    expect(acaoDoCartao1()).toHaveAccessibleName("Meus agendamentos");

    act(() => {
      window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true }));
    });
    await waitFor(() => expect(acaoDoCartao1()).toHaveAccessibleName("Criar conta ou entrar"));
    expect(acaoDoCartao1()).toHaveAttribute("href", "/entrar");
    expect(pedidos).toBe(2);
  });
});
