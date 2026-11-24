import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, barbeiroAdmin, JORNADA_PADRAO, SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio(); // quarta-feira, 18/11/2026
});
afterEach(() => vi.useRealTimers());

const TRES = [
  agendamentoAdmin({
    id: "a1",
    inicio: "2026-11-24T09:00:00-03:00",
    fim: "2026-11-24T10:00:00-03:00",
    cliente: { nome: "Carlos M.", whatsapp: "11988880002" },
    status: "CONCLUIDO",
    version: 5,
  }),
  agendamentoAdmin({ id: "a2" }),
  agendamentoAdmin({
    id: "a3",
    status: "AGUARDANDO_PAGAMENTO",
    origem: "GUEST",
    inicio: "2026-11-24T14:00:00-03:00",
    fim: "2026-11-24T15:00:00-03:00",
    expiraEm: "2026-11-24T13:03:00-03:00",
    cliente: { nome: "Visitante", whatsapp: "11988880003" },
    cobrancaStatus: "PENDENTE",
  }),
];

function api(consultas: URLSearchParams[], itens: unknown[] = TRES) {
  servidor.use(
    http.get(`${BFF}/admin/agenda`, ({ request }) => {
      consultas.push(new URL(request.url).searchParams);
      return HttpResponse.json(itens);
    }),
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
    http.get(`${BFF}/admin/minha-jornada`, () =>
      HttpResponse.json({ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, intervalos: JORNADA_PADRAO }),
    ),
  );
}

describe("agenda do painel", () => {
  test("A45: lista ordenada por horário com status em texto e data-estado, navega de dia, alterna dia e semana, filtra barbeiro só para o ADMIN, mostra o almoço, a etiqueta MANUAL e nunca e-mail nem CPF", async () => {
    definirBusca("data=2026-11-24");
    const consultas: URLSearchParams[] = [];
    api(consultas, [
      ...TRES,
      agendamentoAdmin({
        id: "a4",
        origem: "MANUAL",
        inicio: "2026-11-24T16:00:00-03:00",
        fim: "2026-11-24T17:00:00-03:00",
        cliente: { nome: "Sem nome", whatsapp: null },
        cobrancaStatus: null,
        observacao: "Combinado pelo WhatsApp.",
      }),
    ]);
    const { user, unmount } = renderizar(<PainelAgenda />, ADMIN);

    const itens = await screen.findAllByRole("listitem");
    const reservas = itens.filter((i) => i.dataset.estado !== "almoco");
    expect(consultas[0].get("data")).toBe("2026-11-24");
    expect(consultas[0].get("visao")).toBe("DIA");
    expect(consultas[0].has("barbeiroId")).toBe(false);
    expect(reservas.map((i) => i.dataset.estado)).toEqual(["concluido", "confirmado", "aguardando", "confirmado"]);
    expect(reservas[0]).toHaveTextContent("09:00–10:00");
    expect(reservas[0]).toHaveTextContent("Concluído");
    expect(reservas[0]).toHaveTextContent("Carlos M.");
    expect(reservas[1]).toHaveTextContent("Confirmado");
    expect(reservas[1]).toHaveTextContent("PAGO");
    expect(reservas[1]).toHaveTextContent("(11) 98888-0001");
    expect(reservas[1]).toHaveTextContent("Pagamento: Paga");
    expect(reservas[2]).toHaveTextContent("Aguardando pagamento");
    expect(reservas[2]).toHaveTextContent("Pagamento: Pagamento pendente");
    // reserva manual: etiqueta, "Sem nome", observação e nada de pagamento nem devolução
    expect(reservas[3]).toHaveTextContent("MANUAL · WHATSAPP");
    expect(reservas[3]).toHaveTextContent("Sem nome");
    expect(reservas[3]).toHaveTextContent("Observação: Combinado pelo WhatsApp.");
    expect(reservas[3]).not.toHaveTextContent(/pagamento|devolu|PAGO/i);
    expect(document.body.textContent).not.toMatch(/@|CPF/);

    // navegação de dia
    await user.click(screen.getByRole("button", { name: "Próximo dia" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("data")).toBe("2026-11-25"));
    await user.click(screen.getByRole("button", { name: "Dia anterior" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("data")).toBe("2026-11-24"));
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("data")).toBe("2026-11-18"));

    // semana
    await user.click(screen.getByRole("button", { name: "Semana" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("visao")).toBe("SEMANA"));
    expect(screen.getByRole("button", { name: "Semana" })).toHaveAttribute("aria-pressed", "true");

    // filtro de barbeiro (ADMIN) manda barbeiroId; com ele o almoço aparece listrado
    await user.click(screen.getByRole("button", { name: "Dia" }));
    await user.selectOptions(screen.getByLabelText("Barbeiro"), BARBEIRO.id);
    await vi.waitFor(() => expect(consultas.at(-1)?.get("barbeiroId")).toBe(BARBEIRO.id));
    unmount();
  });

  test("A45: o almoço é a lacuna da jornada, listrado e com texto; o BARBEIRO não tem filtro e a chamada não leva barbeiroId; dia vazio avisa", async () => {
    definirBusca("data=2026-11-24");
    const consultas: URLSearchParams[] = [];
    api(consultas);
    const { unmount } = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const almoco = await screen.findByText(/Almoço · 12h às 13h/);
    expect(almoco).toHaveAttribute("data-estado", "almoco");
    // entre o 10:00 e o 14:00, por horário
    const ordem = screen.getAllByRole("listitem").map((i) => i.dataset.estado);
    expect(ordem).toEqual(["concluido", "confirmado", "almoco", "aguardando"]);
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(consultas[0].has("barbeiroId")).toBe(false);
    unmount();

    servidor.use(http.get(`${BFF}/admin/agenda`, () => HttpResponse.json([])));
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    expect(await screen.findByText("Nenhum cliente marcado neste dia ainda.")).toBeInTheDocument();
    expect(within(document.body).queryByText(/Almoço/)).toBeNull();
  });
});
