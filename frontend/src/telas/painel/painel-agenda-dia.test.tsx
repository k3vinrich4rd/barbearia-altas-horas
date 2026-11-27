import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import {
  agendamentoAdmin,
  barbeiroAdmin,
  colunaDoDia,
  gradeDoDia,
  resumoDoMes,
  SESSAO_BARBEIRO,
} from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { SessaoProvider } from "@/componentes/Sessao";
import { PainelAgenda } from "./PainelAgenda";
import { PainelReservaManual } from "./PainelReservaManual";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  servidor.use(
    http.get(`${BFF}/admin/agenda/mes`, ({ request }) =>
      HttpResponse.json(resumoDoMes(new URL(request.url).searchParams.get("mes") ?? "2026-11")),
    ),
    http.get(`${BFF}/admin/agenda/dia`, ({ request }) =>
      HttpResponse.json(gradeDoDia(new URL(request.url).searchParams.get("data") ?? "", [colunaDoDia([])])),
    ),
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
  );
});
afterEach(() => vi.useRealTimers());

describe("agenda do painel: dia de abertura", () => {
  test("/painel sem ?data abre hoje no fuso de São Paulo (terça, 24/11/2026, 16h17), mesmo com o relógio do navegador já em UTC do dia seguinte", async () => {
    fixarRelogio("2026-11-24T16:17:00-03:00");
    renderizar(<PainelAgenda />, ADMIN);
    expect(await screen.findByText("Terça-feira, 24 de novembro")).toBeInTheDocument();
    expect(screen.queryByText(/19 de novembro/)).toBeNull();
  });

  test("às 22h de São Paulo (já 25/11 em UTC) o dia ainda é 24", async () => {
    fixarRelogio("2026-11-24T22:30:00-03:00");
    renderizar(<PainelAgenda />, ADMIN);
    expect(await screen.findByText("Terça-feira, 24 de novembro")).toBeInTheDocument();
  });

  test("voltar de /painel?data=2026-11-19 para /painel (mesma tela montada, o componente não remonta sozinho) mostra hoje, não o dia guardado", async () => {
    fixarRelogio("2026-11-24T16:17:00-03:00");
    definirBusca("data=2026-11-19");
    const { rerender } = renderizar(<PainelAgenda />, ADMIN);
    expect(await screen.findByText("Quinta-feira, 19 de novembro")).toBeInTheDocument();

    definirBusca("");
    rerender(
      <SessaoProvider inicial={ADMIN}>
        <PainelAgenda />
      </SessaoProvider>,
    );
    expect(await screen.findByText("Terça-feira, 24 de novembro")).toBeInTheDocument();
    expect(screen.queryByText(/19 de novembro/)).toBeNull();
  });
});

// ---- Fatia 14C: a grade do dia (A50 a A52) ---------------------------------------------------------------------------

const BRUNO = "b0000000-0000-4000-8000-0000000000b2";
const RESERVA_0900 = agendamentoAdmin({
  id: "r0900",
  inicio: "2026-12-01T09:00:00-03:00",
  fim: "2026-12-01T10:00:00-03:00",
  cliente: { nome: "Maria Souza", whatsapp: "11988880001" },
  version: 2,
});
const RESERVA_1500 = agendamentoAdmin({
  id: "r1500",
  status: "AGUARDANDO_PAGAMENTO",
  inicio: "2026-12-01T15:00:00-03:00",
  fim: "2026-12-01T16:00:00-03:00",
  expiraEm: "2026-12-01T12:04:00-03:00",
  cobrancaStatus: "PENDENTE",
  cliente: { nome: "Visitante Teste", whatsapp: "11988880002" },
  prorrogacao: {
    usada: true,
    usadas: 3,
    maximo: 10,
    restantes: 7,
    segundos: 120,
    abreEm: "2026-12-01T12:07:00-03:00",
    fechaEm: "2026-12-01T12:08:00-03:00",
  },
});

function agendaDe(colunas: ReturnType<typeof colunaDoDia>[], leituras = { mes: 0, dia: 0 }) {
  servidor.use(
    http.get(`${BFF}/admin/agenda/mes`, () => {
      leituras.mes++;
      return HttpResponse.json(resumoDoMes("2026-12", { "2026-12-01": { reservas: 2 } }));
    }),
    http.get(`${BFF}/admin/agenda/dia`, () => {
      leituras.dia++;
      return HttpResponse.json(gradeDoDia("2026-12-01", colunas));
    }),
  );
  return leituras;
}

const linhaDa = async (hora: string) => (await screen.findByRole("rowheader", { name: hora })).closest("tr")!;

describe("grade do dia do painel (Fatia 14C)", () => {
  beforeEach(() => {
    definirBusca("data=2026-12-01");
    fixarRelogio("2026-12-01T12:00:00-03:00"); // a reserva das 09:00 já começou
  });

  test("A50: Livre com Marcar aqui (reserva manual com barbeiro, dia e hora), a reserva com nome, serviço e etiqueta de status, quantas vezes pediu mais tempo, Almoço e Folga", async () => {
    agendaDe([colunaDoDia([RESERVA_0900, RESERVA_1500], { folgas: ["17:00", "17:30"] })]);
    const { unmount } = renderizar(<PainelAgenda />, ADMIN);

    const as0700 = await linhaDa("07:00");
    expect(as0700).toHaveTextContent("Livre");
    const marcar = within(as0700).getByRole("link", { name: /Marcar aqui/ });
    const destino = new URL(marcar.getAttribute("href")!, "http://localhost");
    expect(destino.pathname).toBe("/painel/reserva-manual");
    expect(Object.fromEntries(destino.searchParams)).toEqual({
      barbeiroId: "b0000000-0000-4000-8000-000000000001",
      data: "2026-12-01",
      hora: "07:00",
    });

    const as0900 = await linhaDa("09:00");
    expect(as0900).toHaveTextContent("Maria Souza");
    expect(as0900).toHaveTextContent("Corte Degradê");
    expect(as0900).toHaveTextContent("Confirmado");
    expect(within(await linhaDa("09:30")).getByText("Maria Souza")).toBeInTheDocument();
    const as1500 = await linhaDa("15:00");
    expect(as1500).toHaveTextContent("Aguardando pagamento");
    expect(as1500).toHaveTextContent("Mais tempo pedido 3 vezes");
    expect(await linhaDa("12:00")).toHaveTextContent("Almoço");
    expect(await linhaDa("17:00")).toHaveTextContent("Folga");
    expect(within(await linhaDa("17:00")).queryByRole("link")).toBeNull();
    unmount();

    // a reserva manual abre com barbeiro, dia e hora já marcados
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json([])),
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([{ id: "b0000000-0000-4000-8000-000000000001", nomeExibicao: "Carlos Eduardo" }]),
      ),
    );
    window.history.replaceState(null, "", marcar.getAttribute("href"));
    renderizar(<PainelReservaManual />, ADMIN);
    await vi.waitFor(() => expect(screen.getByLabelText("Dia")).toHaveValue("2026-12-01"));
    expect(screen.getByLabelText("Horário")).toHaveValue("07:00");
    await vi.waitFor(() =>
      expect(screen.getByLabelText("Barbeiro")).toHaveValue("b0000000-0000-4000-8000-000000000001"),
    );
    window.history.replaceState(null, "", "/");
  });

  test("A51: Concluído, Não compareceu e (ADMIN) Cancelar pela barbearia com motivo: as mesmas chamadas e mensagens da 7B; depois a grade e a contagem do mês se atualizam; o BARBEIRO não vê Cancelar", async () => {
    const leituras = agendaDe([colunaDoDia([RESERVA_0900])]);
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.patch(`${BFF}/admin/agendamentos/:id/status`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        corpos.push(corpo);
        return HttpResponse.json({ ...RESERVA_0900, status: corpo.status, version: 3 });
      }),
    );

    // BARBEIRO: conclui; não vê Cancelar
    const barbeiro = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    let item = (await screen.findByText(/Maria Souza/, { selector: "li *" })).closest("li") as HTMLElement;
    expect(within(item).queryByRole("button", { name: "Cancelar pela barbearia" })).toBeNull();
    await barbeiro.user.click(within(item).getByRole("button", { name: "Concluído" }));
    expect(await screen.findByText("Atendimento concluído.")).toBeInTheDocument();
    expect(corpos[0]).toEqual({ status: "CONCLUIDO", version: 2 });
    await vi.waitFor(() => expect(leituras.mes).toBe(2)); // a contagem do mês é lida de novo
    expect(await linhaDa("09:00")).toHaveTextContent("Concluído"); // a grade mudou sem ler o dia de novo
    expect(leituras.dia).toBe(1);
    barbeiro.unmount();

    // Não compareceu: confirma antes, com o aviso de que o valor fica retido
    const falta = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    item = (await screen.findByText(/Maria Souza/, { selector: "li *" })).closest("li") as HTMLElement;
    await falta.user.click(within(item).getByRole("button", { name: "Não compareceu" }));
    const dialogo = screen.getByRole("dialog");
    expect(dialogo).toHaveTextContent("O valor pago fica retido");
    await falta.user.click(within(dialogo).getByRole("button", { name: "Confirmar falta" }));
    await vi.waitFor(() => expect(corpos.at(-1)).toEqual({ status: "NAO_COMPARECEU", version: 2 }));
    expect(await screen.findByText("Falta registrada. O valor pago fica retido.")).toBeInTheDocument();
    expect(await linhaDa("09:00")).toHaveTextContent("Não compareceu");
    falta.unmount();

    // ADMIN cancela com motivo; o horário volta a Livre na grade
    const admin = renderizar(<PainelAgenda />, ADMIN);
    item = (await screen.findByText(/Maria Souza/, { selector: "li *" })).closest("li") as HTMLElement;
    await admin.user.click(within(item).getByRole("button", { name: "Cancelar pela barbearia" }));
    await admin.user.type(
      within(screen.getByRole("dialog")).getByLabelText("Motivo do cancelamento"),
      "Barbeiro doente",
    );
    await admin.user.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    await vi.waitFor(() =>
      expect(corpos.at(-1)).toEqual({ status: "CANCELADO", version: 2, motivo: "Barbeiro doente" }),
    );
    expect(await screen.findByText(/Agendamento cancelado/)).toBeInTheDocument();
    await vi.waitFor(async () => expect(await linhaDa("09:00")).toHaveTextContent("Livre"));
    admin.unmount();

    // a mensagem de erro da 7B continua
    servidor.use(http.patch(`${BFF}/admin/agendamentos/:id/status`, () => erro(422, "TRANSICAO_INVALIDA")));
    const outra = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    item = (await screen.findByText(/Maria Souza/, { selector: "li *" })).closest("li") as HTMLElement;
    await outra.user.click(within(item).getByRole("button", { name: "Concluído" }));
    expect(await screen.findByText("Só dá para fechar o atendimento depois do horário de início.")).toBeInTheDocument();
  });

  test("A52: ADMIN com 2 barbeiros tem o filtro com Todos e uma coluna por barbeiro (um por vez no celular, pelo seletor); o BARBEIRO não tem filtro", async () => {
    agendaDe([
      colunaDoDia([RESERVA_0900], { nomeExibicao: "Bruno Teste", barbeiroId: BRUNO }),
      colunaDoDia([], { nomeExibicao: "Eduardo Teste" }),
    ]);
    servidor.use(
      http.get(`${BFF}/admin/barbeiros`, () =>
        HttpResponse.json([barbeiroAdmin(), barbeiroAdmin({ id: BRUNO, nomeExibicao: "Bruno Teste" })]),
      ),
    );
    const { user, unmount } = renderizar(<PainelAgenda />, ADMIN);

    const filtro = await screen.findByLabelText("Barbeiro");
    expect(within(filtro).getByRole("option", { name: "Todos" })).toBeInTheDocument();
    expect(filtro).toHaveValue("");
    expect(await screen.findByRole("columnheader", { name: "Bruno Teste" })).toHaveAttribute("data-visivel", "true");
    expect(screen.getByRole("columnheader", { name: "Eduardo Teste" })).not.toHaveAttribute("data-visivel");
    expect(screen.getByRole("table", { name: /Horários do dia/ })).toHaveAttribute("data-varias-colunas", "true");
    // no celular, o seletor troca a coluna visível
    await user.selectOptions(screen.getByLabelText("Ver a coluna de"), "b0000000-0000-4000-8000-000000000001");
    expect(screen.getByRole("columnheader", { name: "Eduardo Teste" })).toHaveAttribute("data-visivel", "true");
    expect(screen.getByRole("columnheader", { name: "Bruno Teste" })).not.toHaveAttribute("data-visivel");
    unmount();

    agendaDe([colunaDoDia([RESERVA_0900])]);
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    expect(await screen.findByRole("rowheader", { name: "09:00" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(screen.queryByLabelText("Ver a coluna de")).toBeNull();
    expect(screen.getByRole("table", { name: /Horários do dia/ })).not.toHaveAttribute("data-varias-colunas");
  });
});
