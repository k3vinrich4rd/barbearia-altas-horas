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
  test("A45: grade ordenada por horário com status em texto e data-estado, navega de dia, alterna dia e semana, filtra barbeiro só para o ADMIN, mostra o almoço, a etiqueta MANUAL e nunca e-mail nem CPF", async () => {
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
    expect(reservas[2]).toHaveTextContent("Aguardando pagamento");
    // reserva manual: etiqueta, "Sem nome", observação e nada de pagamento nem devolução
    expect(reservas[3]).toHaveTextContent("MANUAL · WHATSAPP");
    expect(reservas[3]).toHaveTextContent("Sem nome");
    expect(reservas[3]).toHaveTextContent("Obs.: Combinado pelo WhatsApp.");
    expect(reservas[3]).not.toHaveTextContent(/pagamento|devolu|PAGO/i);

    // os detalhes (WhatsApp mascarado, pagamento) saem no painel ao lado, depois de escolher a reserva
    await user.click(within(reservas[1]).getByRole("button"));
    expect(screen.getByRole("complementary")).toHaveTextContent("(11) 98888-0001");
    expect(screen.getByRole("complementary")).toHaveTextContent("PagamentoPaga");
    await user.click(within(reservas[2]).getByRole("button"));
    expect(screen.getByRole("complementary")).toHaveTextContent("PagamentoPagamento pendente");
    await user.click(within(reservas[3]).getByRole("button"));
    expect(screen.getByRole("complementary")).toHaveTextContent("Reserva manual (WhatsApp), sem cobrança");
    expect(screen.getByRole("complementary")).toHaveTextContent("ObservaçãoCombinado pelo WhatsApp.");
    expect(screen.getByRole("complementary")).not.toHaveTextContent(/pagamento|devolu|PAGO/i);
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
    expect(almoco.closest('[role="listitem"]')).toHaveAttribute("data-estado", "almoco");
    // entre o 10:00 e o 14:00, por horário
    const ordem = screen.getAllByRole("listitem").map((i) => i.dataset.estado);
    expect(ordem).toEqual(["concluido", "confirmado", "almoco", "aguardando"]);
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(consultas[0].has("barbeiroId")).toBe(false);
    unmount();

    servidor.use(http.get(`${BFF}/admin/agenda`, () => HttpResponse.json([])));
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    expect(await screen.findByText("Sem agendamentos neste dia.")).toBeInTheDocument();
    expect(within(document.body).queryByText(/Almoço/)).toBeNull();
  });
});

const hora = (hhmm: string) => `2026-11-24T${hhmm}:00-03:00`;
const reserva = (id: string, de: string, ate: string, extra: Record<string, unknown> = {}) =>
  agendamentoAdmin({ id, inicio: hora(de), fim: hora(ate), ...extra });

describe("agenda do dia em grade de horas (7C)", () => {
  test("A1: linhas de 30 min das 07:00 às 20:00 com o rótulo só nas horas cheias; a reserva de 60 min ocupa 2 linhas, a que começa na linha certa (09:00 = linha 5, 14:00 = linha 15)", async () => {
    definirBusca("data=2026-11-24");
    api([], TRES);
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const grade = await screen.findByRole("list", { name: "Agenda do dia" });

    expect(grade.querySelectorAll(".grade-trilho")).toHaveLength(26);
    const rotulos = [...grade.querySelectorAll(".grade-hora")].map((h) => h.textContent);
    expect(rotulos.filter(Boolean)).toHaveLength(13);
    expect(rotulos[0]).toBe("07:00");
    expect(rotulos[1]).toBe(""); // 07:30: só o traço tracejado
    expect(rotulos[12]).toBe("13:00");
    expect(rotulos[25]).toBe("");
    expect(grade.querySelectorAll('.grade-trilho[data-cheia="true"]')).toHaveLength(13);
    // a grade desenhada é decoração: o leitor de tela lê a lista de reservas
    for (const e of grade.querySelectorAll(".grade-hora, .grade-trilho")) expect(e).toHaveAttribute("aria-hidden", "true");

    const blocos = within(grade)
      .getAllByRole("listitem")
      .filter((i) => i.dataset.estado !== "almoco");
    expect(blocos.map((b) => [b.dataset.linha, b.dataset.span, b.dataset.faixa])).toEqual([
      ["5", "2", "1"], // 09:00
      ["7", "2", "1"], // 10:00
      ["15", "2", "1"], // 14:00
    ]);
    expect(grade).toHaveAttribute("data-faixas", "1");
  });

  test("A1: a janela cresce de hora em hora quando uma reserva cai fora das 07:00 às 20:00", async () => {
    definirBusca("data=2026-11-24");
    api([], [reserva("c1", "06:00", "07:00"), reserva("c2", "20:30", "21:30")]);
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const grade = await screen.findByRole("list", { name: "Agenda do dia" });
    expect(grade.querySelectorAll(".grade-trilho")).toHaveLength(32); // 06:00 às 22:00
    expect(grade.querySelector(".grade-hora")?.textContent).toBe("06:00");
    const blocos = within(grade).getAllByRole("listitem");
    expect(blocos.map((b) => [b.dataset.linha, b.dataset.span])).toEqual([
      ["1", "2"], // 06:00
      ["30", "2"], // 20:30
    ]);
  });

  test("A2: o almoço da jornada é um bloco listrado com texto nas linhas certas (12:00 às 13:00 = linhas 11 e 12) e some quando a jornada não é conhecida (ADMIN em Todos)", async () => {
    definirBusca("data=2026-11-24");
    api([], TRES);
    const { unmount } = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const almoco = await screen.findByText(/Almoço · 12h às 13h/);
    const bloco = almoco.closest('[role="listitem"]') as HTMLElement;
    expect(bloco).toHaveClass("grade-almoco");
    expect(bloco.dataset.estado).toBe("almoco");
    expect([bloco.dataset.linha, bloco.dataset.span]).toEqual(["11", "2"]);
    unmount();

    renderizar(<PainelAgenda />, ADMIN);
    await screen.findByRole("list", { name: "Agenda do dia" });
    expect(screen.queryByText(/Almoço/)).toBeNull();
  });

  test("A3: cada estado tem forma (data-estado) e texto; PAGO e MANUAL · WHATSAPP aparecem só onde valem; a ordem de leitura é por horário mesmo que a API devolva fora de ordem", async () => {
    definirBusca("data=2026-11-24");
    api(
      [],
      [
        reserva("e6", "18:00", "19:00", { status: "EXPIRADO", cobrancaStatus: "CANCELADA" }),
        reserva("e1", "08:00", "09:00", { status: "CONCLUIDO" }),
        reserva("e5", "16:00", "17:00", { status: "CANCELADO", motivoCancelamento: "Imprevisto" }),
        reserva("e2", "09:00", "10:00", { status: "NAO_COMPARECEU" }),
        reserva("e4", "14:00", "15:00", { status: "AGUARDANDO_PAGAMENTO", cobrancaStatus: "PENDENTE", expiraEm: hora("13:03") }),
        reserva("e3", "10:00", "11:00", { status: "CONFIRMADO" }),
        reserva("e7", "11:00", "12:00", { status: "CONFIRMADO", origem: "MANUAL", cobrancaStatus: null, cliente: { nome: "Seu Jorge", whatsapp: null } }),
      ],
    );
    renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const grade = await screen.findByRole("list", { name: "Agenda do dia" });
    const itens = within(grade)
      .getAllByRole("listitem")
      .filter((i) => i.dataset.estado !== "almoco");
    expect(itens.map((i) => i.dataset.estado)).toEqual([
      "concluido",
      "nao-compareceu",
      "confirmado",
      "confirmado",
      "aguardando",
      "cancelado",
      "expirado",
    ]);
    const textos = ["Concluído", "Não compareceu", "PAGO", "MANUAL · WHATSAPP", "Aguardando pagamento até 13:03", "Cancelado", "Expirado"];
    itens.forEach((item, i) => expect(item, textos[i]).toHaveTextContent(textos[i]));
    // PAGO só no confirmado pago; o manual não tem PAGO e o concluído usa a etiqueta do status
    expect(within(itens[3]).queryByText("PAGO")).toBeNull();
    expect(within(itens[0]).queryByText("PAGO")).toBeNull();
    // a ordem do DOM (a de leitura) é a do horário: 08, 09, 10, 11, 12 (almoço), 14, 16, 18
    const todos = within(grade).getAllByRole("listitem");
    expect(todos.map((i) => i.dataset.linha)).toEqual(["3", "5", "7", "9", "11", "15", "19", "23"]);
  });

  test("A4: reservas que se sobrepõem (barbeiros diferentes em Todos, ou um horário que expirou e foi reservado de novo) ficam lado a lado, cada uma na sua faixa, e a grade ganha as colunas", async () => {
    definirBusca("data=2026-11-24");
    api([], [
      reserva("p1", "10:00", "11:00"),
      reserva("p2", "10:00", "11:00", { status: "CONFIRMADO", cliente: { nome: "Outro Cliente", whatsapp: null } }),
      reserva("p3", "10:30", "11:30", { status: "EXPIRADO" }),
      reserva("p4", "12:00", "13:00"),
    ]);
    renderizar(<PainelAgenda />, ADMIN);
    const grade = await screen.findByRole("list", { name: "Agenda do dia" });
    const blocos = within(grade).getAllByRole("listitem");
    expect(blocos.map((b) => [b.dataset.linha, b.dataset.faixa])).toEqual([
      ["7", "1"],
      ["7", "2"],
      ["8", "3"],
      ["11", "1"], // depois que as faixas esvaziam, volta para a primeira
    ]);
    expect(grade).toHaveAttribute("data-faixas", "3");
  });

  test("A5: escolher um bloco marca aria-pressed, abre os detalhes no painel ao lado (aria-live) e liberta as ações; funciona só com o teclado (Tab, Enter e Espaço) e o painel avisa quando nada foi escolhido", async () => {
    definirBusca("data=2026-11-24");
    api([], TRES);
    const { user } = renderizar(<PainelAgenda />, ADMIN);
    const grade = await screen.findByRole("list", { name: "Agenda do dia" });
    const painel = screen.getByRole("complementary");
    expect(painel).toHaveAttribute("aria-live", "polite");
    expect(painel).toHaveTextContent("Escolha um horário da grade");
    expect(within(painel).queryByRole("button")).toBeNull();

    const botoes = within(grade).getAllByRole("button");
    expect(botoes).toHaveLength(3);
    botoes.forEach((b) => expect(b).toHaveAttribute("aria-pressed", "false"));

    // só teclado: foco no segundo bloco, Enter escolhe e o painel mostra o horário, o cliente e as ações
    botoes[1].focus();
    await user.keyboard("{Enter}");
    expect(botoes[1]).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("complementary", { name: "10:00–11:00" })).toHaveTextContent("Rafael Souza");
    expect(within(screen.getByRole("complementary")).getAllByRole("button").map((b) => b.textContent)).toEqual([
      "Concluído",
      "Não compareceu",
      "Cancelar pela barbearia",
    ]);

    // Espaço no outro bloco troca a escolha; o concluído (09:00) não tem ações de fechar
    botoes[0].focus();
    await user.keyboard(" ");
    expect(botoes[0]).toHaveAttribute("aria-pressed", "true");
    expect(botoes[1]).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("complementary", { name: "09:00–10:00" })).toHaveTextContent("Carlos M.");
    expect(within(screen.getByRole("complementary")).queryByRole("button")).toBeNull();

    // navegar de dia limpa a escolha
    await user.click(screen.getByRole("button", { name: "Próximo dia" }));
    await vi.waitFor(() => expect(screen.getByRole("complementary")).toHaveTextContent("Escolha um horário da grade"));
  });

  test("A6: a semana continua em lista (o canvas não desenha a semana), por dia, com data-estado, o almoço e as ações dentro de cada reserva", async () => {
    definirBusca("data=2026-11-24");
    api([], TRES);
    const { user } = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    await screen.findByRole("list", { name: "Agenda do dia" });
    await user.click(screen.getByRole("button", { name: "Semana" }));
    await screen.findByRole("heading", { level: 3, name: /Terça-feira, 24 de novembro/ });
    expect(screen.queryByRole("list", { name: "Agenda do dia" })).toBeNull();
    expect(document.querySelector(".grade")).toBeNull();
    const itens = screen.getAllByRole("listitem");
    expect(itens.map((i) => i.dataset.estado)).toEqual(["concluido", "confirmado", "almoco", "aguardando"]);
    expect(within(itens[1]).getByRole("button", { name: "Concluído" })).toBeInTheDocument();
    expect(itens[1]).toHaveTextContent("(11) 98888-0001");
  });
});
