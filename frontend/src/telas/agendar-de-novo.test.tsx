import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS, reserva } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";
import { MeusAgendamentos } from "./MeusAgendamentos";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const OUTRO = { id: "b0000000-0000-4000-8000-0000000000b2", nomeExibicao: "Bruno Teste" };
const CONCLUIDO = reserva({ id: "r-concluido", status: "CONCLUIDO", expiraEm: null, barbeiro: OUTRO });

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  servidor.use(
    http.get(`${BFF}/me/agendamentos`, () =>
      HttpResponse.json({ itens: [CONCLUIDO], pagina: 0, tamanho: 10, total: 1 }),
    ),
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO, OUTRO])),
    http.get(`${BFF}/disponibilidade`, ({ request }) =>
      HttpResponse.json({
        data: new URL(request.url).searchParams.get("data"),
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 60,
        barbeiros: [],
      }),
    ),
  );
});
afterEach(() => vi.useRealTimers());

describe("agendar de novo (Fatia 14D, M8)", () => {
  test("A61: o agendamento concluído tem Agendar de novo, que leva a /agendar?servicoId=<id>&barbeiroId=<id> com os dois marcados", async () => {
    const { unmount } = renderizar(<MeusAgendamentos />, CLIENTE);
    const item = (await screen.findByText(/Corte Degradê/, { selector: "h3" })).closest("li")!;
    const link = within(item).getByRole("link", { name: /Agendar de novo/ });
    const destino = new URL(link.getAttribute("href")!, "http://localhost");
    expect(destino.pathname).toBe("/agendar");
    expect(Object.fromEntries(destino.searchParams)).toEqual({ servicoId: CINCO_SERVICOS[1].id, barbeiroId: OUTRO.id });
    unmount();

    definirBusca(destino.searchParams.toString());
    renderizar(<Agendar />);
    expect(await screen.findByRole("radio", { name: /Corte Degradê/ })).toBeChecked();
    expect(await screen.findByRole("radio", { name: "Bruno Teste" })).toBeChecked();
  });
});
