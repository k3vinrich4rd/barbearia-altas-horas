import { act, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { renderizar } from "@/testes/render";
import { PainelNovidades } from "@/telas/painel/PainelNovidades";
import { NavegacaoDoPainel } from "./NavegacaoDoPainel";
import { NovidadesProvider } from "./Novidades";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const aviso = (id: string, extra: Record<string, unknown> = {}) => ({
  id,
  reservaId: `r-${id}`,
  servico: "Corte Degradê",
  inicio: "2026-11-27T10:00:00-03:00",
  criadoEm: new Date(Date.now() - 5 * 60_000).toISOString(),
  lido: false,
  ...extra,
});

let naoLidas = 2;
let consultas = 0;
let marcadas = 0;
const mensageiro = new EventTarget();

beforeEach(() => {
  zerarNavegacao();
  naoLidas = 2;
  consultas = 0;
  marcadas = 0;
  Object.defineProperty(navigator, "serviceWorker", { value: mensageiro, configurable: true });
  servidor.use(
    http.get(`${BFF}/admin/notificacoes`, () => {
      consultas++;
      return HttpResponse.json({ naoLidas, itens: [aviso("a1"), aviso("a2"), aviso("a3", { lido: true })] });
    }),
    http.post(`${BFF}/admin/notificacoes/marcar-lidas`, () => {
      marcadas++;
      naoLidas = 0;
      return new HttpResponse(null, { status: 204 });
    }),
  );
});

afterEach(() => {
  Reflect.deleteProperty(navigator, "serviceWorker");
});

function painel(conteudo = <PainelNovidades />) {
  return renderizar(
    <NovidadesProvider>
      <NavegacaoDoPainel />
      {conteudo}
    </NovidadesProvider>,
    SESSAO_BARBEIRO,
  );
}

describe("selo e lista Novidades (A42)", () => {
  test("A42: o painel abre com 2 não lidos: o selo mostra 2 (número e palavra) no item Novidades do menu", async () => {
    painel(<p>conteúdo</p>);
    const selo = await screen.findByTestId("selo-novidades");
    expect(selo).toHaveTextContent("2 novidades");
    expect(screen.getByRole("link", { name: /Novidades/ })).toContainElement(selo);
  });

  test("A42: abrir a lista chama marcar-lidas e o selo some, mas os itens que eram novos continuam destacados na lista", async () => {
    painel();
    await waitFor(() => expect(marcadas).toBe(1));
    await waitFor(() => expect(screen.queryByTestId("selo-novidades")).not.toBeInTheDocument());
    expect(screen.getAllByText("Novo")).toHaveLength(2);
    expect(screen.getAllByText("Corte Degradê")).toHaveLength(3);
    // dia, hora e "há 5 min"; nunca nome nem WhatsApp
    expect(screen.getAllByText(/sexta, 27\/11 às 10:00/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("há 5 min").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Ver na agenda" })[0]).toHaveAttribute("href", "/painel?data=2026-11-27");
    expect(screen.queryByText(/WhatsApp/i)).not.toBeInTheDocument();
  });

  test("A42: a mensagem do service worker atualiza o selo SEM polling", async () => {
    painel(<p>conteúdo</p>);
    expect(await screen.findByTestId("selo-novidades")).toHaveTextContent("2");
    expect(consultas).toBe(1);
    await new Promise((r) => setTimeout(r, 300));
    expect(consultas, "nenhuma consulta nova sem evento").toBe(1);

    naoLidas = 3;
    act(() => {
      mensageiro.dispatchEvent(Object.assign(new Event("message"), { data: { tipo: "novidade" } }));
    });

    await waitFor(() => expect(screen.getByTestId("selo-novidades")).toHaveTextContent("3"));
    expect(consultas).toBe(2);
    // mensagem de outro tipo não consulta
    act(() => {
      mensageiro.dispatchEvent(Object.assign(new Event("message"), { data: { tipo: "outra" } }));
    });
    await new Promise((r) => setTimeout(r, 100));
    expect(consultas).toBe(2);
  });

  test("A42: voltar o foco para a aba atualiza o selo", async () => {
    painel(<p>conteúdo</p>);
    await screen.findByTestId("selo-novidades");
    naoLidas = 5;
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });
    await waitFor(() => expect(screen.getByTestId("selo-novidades")).toHaveTextContent("5"));
  });

  test("A42: sem nenhuma novidade a lista diz isso e não há selo", async () => {
    servidor.use(http.get(`${BFF}/admin/notificacoes`, () => HttpResponse.json({ naoLidas: 0, itens: [] })));
    painel();
    expect(await screen.findByText("Nenhuma novidade por enquanto.")).toBeInTheDocument();
    expect(screen.queryByTestId("selo-novidades")).not.toBeInTheDocument();
  });

  test("A42: falha da API não derruba o painel: o selo some e a lista mostra vazio", async () => {
    servidor.use(http.get(`${BFF}/admin/notificacoes`, () => new HttpResponse(null, { status: 500 })));
    painel();
    expect(await screen.findByText("Nenhuma novidade por enquanto.")).toBeInTheDocument();
  });
});
