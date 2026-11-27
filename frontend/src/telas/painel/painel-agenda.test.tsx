import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import {
  agendamentoAdmin,
  barbeiroAdmin,
  colunaDoDia,
  gradeDoDia,
  JORNADA_PADRAO,
  resumoDoMes,
  SESSAO_BARBEIRO,
} from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

// A45 da Fatia 7, com a agenda nova da Fatia 14C (E17): a lista do dia continua (status em texto, data-estado, etiqueta
// MANUAL, nada de e-mail nem CPF); a navegação "Dia anterior · Próximo dia · Dia · Semana" deu lugar à data, ao "Hoje"
// e ao calendário do mês, e o almoço passou para a grade do dia.
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

function api(consultas: URLSearchParams[], itens: ReturnType<typeof agendamentoAdmin>[] = TRES) {
  servidor.use(
    http.get(`${BFF}/admin/agenda/dia`, ({ request }) => {
      const busca = new URL(request.url).searchParams;
      consultas.push(busca);
      return HttpResponse.json(gradeDoDia(busca.get("data") ?? "", [colunaDoDia(itens)]));
    }),
    http.get(`${BFF}/admin/agenda/mes`, ({ request }) =>
      HttpResponse.json(resumoDoMes(new URL(request.url).searchParams.get("mes") ?? "")),
    ),
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
    http.get(`${BFF}/admin/minha-jornada`, () =>
      HttpResponse.json({ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, intervalos: JORNADA_PADRAO }),
    ),
  );
}

const lista = () => screen.getByRole("region", { name: "Agendamentos do dia" });

describe("agenda do painel", () => {
  test("A45: lista ordenada por horário com status em texto e data-estado, troca de dia pela data e pelo Hoje, filtra barbeiro só para o ADMIN, mostra a etiqueta MANUAL e nunca e-mail nem CPF", async () => {
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

    await screen.findByText(/Carlos M\./, { selector: "li *" });
    const reservas = within(lista()).getAllByRole("listitem");
    expect(consultas[0].get("data")).toBe("2026-11-24");
    expect(consultas[0].has("visao")).toBe(false);
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

    // troca de dia: pela data e pelo Hoje
    fireEvent.change(screen.getByLabelText("Data"), { target: { value: "2026-11-25" } });
    await vi.waitFor(() => expect(consultas.at(-1)?.get("data")).toBe("2026-11-25"));
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("data")).toBe("2026-11-18"));

    // filtro de barbeiro (ADMIN) manda barbeiroId
    await user.selectOptions(screen.getByLabelText("Barbeiro"), BARBEIRO.id);
    await vi.waitFor(() => expect(consultas.at(-1)?.get("barbeiroId")).toBe(BARBEIRO.id));
    unmount();
  });

  test("A45: o almoço é a lacuna da jornada, listrado e com texto na grade; o BARBEIRO não tem filtro e a chamada não leva barbeiroId; dia vazio avisa", async () => {
    definirBusca("data=2026-11-24");
    const consultas: URLSearchParams[] = [];
    api(consultas);
    const { unmount } = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const linha = (await screen.findByRole("rowheader", { name: "12:00" })).closest("tr")!;
    const almoco = within(linha).getByText("Almoço");
    expect(almoco).toHaveAttribute("data-estado", "almoco");
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(consultas[0].has("barbeiroId")).toBe(false);
    unmount();

    api([], []);
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    expect(await screen.findByText("Nenhum cliente marcado neste dia ainda.")).toBeInTheDocument();
  });

  test("PF-1: o link Fechar horário do WhatsApp leva à reserva manual; o nome acessível não inclui o +", async () => {
    definirBusca("data=2026-11-24");
    api([]);
    renderizar(<PainelAgenda />, ADMIN);
    const link = await screen.findByRole("link", { name: "Fechar horário do WhatsApp" });
    expect(link).toHaveAttribute("href", "/painel/reserva-manual");
    expect(link).toHaveTextContent("+");
    expect(link).toHaveTextContent("Fechar horário do WhatsApp");
  });
});
