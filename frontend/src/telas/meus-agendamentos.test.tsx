import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, fixarRelogio, renderizar } from "@/testes/render";
import { MeusAgendamentos } from "./MeusAgendamentos";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

describe("meus agendamentos", () => {
  test("A38: lista paginada com status em texto, link Pagar só na pendente, motivo do cancelamento e nenhum cancelar ou remarcar; vazio e 401 tratados", async () => {
    const consultas: URLSearchParams[] = [];
    servidor.use(
      http.get(`${BFF}/me/agendamentos`, ({ request }) => {
        consultas.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          pagina: 0,
          tamanho: 10,
          total: 45,
          itens: [
            reserva({ id: "a1", status: "AGUARDANDO_PAGAMENTO", expiraEm: "2026-11-18T08:03:00-03:00", origem: "CLIENTE" }),
            reserva({ id: "a2", status: "CONFIRMADO", expiraEm: null, origem: "CLIENTE" }),
            reserva({ id: "a3", status: "CANCELADO", expiraEm: null, origem: "CLIENTE", motivoCancelamento: "Barbeiro doente" }),
          ],
        });
      }),
    );
    const { user, unmount } = renderizar(<MeusAgendamentos />, CLIENTE);
    const itens = await screen.findAllByRole("listitem");
    expect(itens).toHaveLength(3);
    expect(consultas[0].get("pagina")).toBe("0");
    expect(consultas[0].get("tamanho")).toBe("10");
    expect(itens[0]).toHaveTextContent("Aguardando pagamento");
    expect(within(itens[0]).getByRole("link", { name: "Pagar" })).toHaveAttribute("href", "/agendar/pagamento/a1");
    expect(itens[1]).toHaveTextContent("Confirmado");
    expect(within(itens[1]).queryByRole("link", { name: "Pagar" })).toBeNull();
    expect(itens[2]).toHaveTextContent("Cancelado");
    expect(itens[2]).toHaveTextContent("Motivo: Barbeiro doente");
    expect(itens[0]).toHaveTextContent("terça, 24/11 às 10:00");
    expect(itens[0]).toHaveTextContent("R$ 55,00");
    expect(screen.queryByRole("button", { name: /cancelar|remarcar/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /cancelar|remarcar/i })).toBeNull();
    expect(screen.getByText("Página 1 de 5")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Próxima" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("pagina")).toBe("1"));
    unmount();

    // vazio
    servidor.use(http.get(`${BFF}/me/agendamentos`, () => HttpResponse.json({ pagina: 0, tamanho: 10, total: 0, itens: [] })));
    const v = renderizar(<MeusAgendamentos />, CLIENTE);
    expect(await screen.findByText(/Você ainda não tem agendamentos/)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Agendar horário" })[0]).toHaveAttribute("href", "/agendar");
    v.unmount();

    // 401 leva ao login
    servidor.use(http.get(`${BFF}/me/agendamentos`, () => erro(401, "NAO_AUTENTICADO")));
    renderizar(<MeusAgendamentos />, CLIENTE);
    await vi.waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/entrar?voltar=%2Fmeus-agendamentos"));
  });
});
