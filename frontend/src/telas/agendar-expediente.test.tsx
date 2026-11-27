import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// C8 (Fatia 15, E16 a E19; spec de UX da agenda, AF-10 a AF-12 e AF-15). Hoje é ter 01/12/2026, a tira vai de ter 1 a seg 7.
// A jornada do barbeiro é das 09:00 às 20:00 com almoço das 12:00 às 13:00, de terça a sábado; domingo e segunda fora.
const hora = (data: string, hhmm: string) => `${data}T${hhmm}:00-03:00`;
function grade(data: string, de: string, ate: string, tirar: (hhmm: string) => boolean = () => false) {
  const saida: string[] = [];
  for (let m = Number(de.slice(0, 2)) * 60; m < Number(ate.slice(0, 2)) * 60; m += 30) {
    const hhmm = `${String(Math.floor(m / 60)).padStart(2, "0")}:${m % 60 ? "30" : "00"}`;
    if (hhmm >= "12:00" && hhmm < "13:00") continue;
    if (!tirar(hhmm)) saida.push(hora(data, hhmm));
  }
  return saida;
}
const aberto = (data: string) => ({
  aberto: true,
  motivoFechado: null,
  inicio: hora(data, "09:00"),
  fim: hora(data, "20:00"),
  almoco: { inicio: hora(data, "12:00"), fim: hora(data, "13:00") },
});
const fechado = (motivoFechado: "FORA_DO_EXPEDIENTE" | "FECHADO") => ({
  aberto: false,
  motivoFechado,
  inicio: null,
  fim: null,
  almoco: null,
});

type Dia = { horarios: string[]; expediente?: object };
/** O que a API responde por dia; o resto da semana vem do `padrao`. */
function servir(dias: Record<string, Dia>, padrao: (data: string) => Dia) {
  const pedidas: string[] = [];
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/disponibilidade`, ({ request }) => {
      const data = new URL(request.url).searchParams.get("data")!;
      pedidas.push(data);
      const dia = dias[data] ?? padrao(data);
      return HttpResponse.json({
        data,
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 30,
        barbeiros: [{ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, ...dia }],
      });
    }),
  );
  return pedidas;
}

/** A semana da barbearia: dom 6 e seg 7 fora do expediente, qui 3 de férias o dia todo, qua 2 com uma folga que tira tudo. */
const semanaDaBarbearia = () =>
  servir(
    {
      "2026-12-02": { horarios: [], expediente: aberto("2026-12-02") },
      "2026-12-03": { horarios: [], expediente: fechado("FECHADO") },
      "2026-12-06": { horarios: [], expediente: fechado("FORA_DO_EXPEDIENTE") },
      "2026-12-07": { horarios: [], expediente: fechado("FORA_DO_EXPEDIENTE") },
    },
    (data) => ({ horarios: grade(data, "09:00", "20:00"), expediente: aberto(data) }),
  );

const tira = () => screen.getByRole("group", { name: "Próximos dias" });
const chipDoDia = (nome: string | RegExp) => within(tira()).getByRole("button", { name: nome });

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio("2026-12-01T08:00:00-03:00");
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
});
afterEach(() => vi.useRealTimers());

describe("expediente na agenda pública (Fatia 15)", () => {
  test("AF-10: dia com expediente fechado diz 'Fechado' na tira, com aria-disabled e nome 'dom 6, Fechado' (o texto visível); o toque não escolhe o dia e o motivo nunca aparece", async () => {
    semanaDaBarbearia();
    renderizar(<Agendar />);
    await waitFor(() => expect(chipDoDia(/^dom 6\s?,\s?Fechado$/)).toBeInTheDocument());
    for (const nome of [/^dom 6\s?,\s?Fechado$/, /^seg 7\s?,\s?Fechado$/, /^qui 3\s?,\s?Fechado$/]) {
      const chip = chipDoDia(nome);
      expect(chip).toHaveAttribute("aria-disabled", "true");
      expect(chip).toHaveTextContent("Fechado");
    }
    // o primeiro dia livre (ter 1) já vem escolhido; tocar no domingo não muda o dia
    await waitFor(() => expect(chipDoDia(/^ter 1/)).toHaveAttribute("aria-pressed", "true"));
    fireEvent.click(chipDoDia(/^dom 6\s?,\s?Fechado$/));
    expect(chipDoDia(/^ter 1/)).toHaveAttribute("aria-pressed", "true");
    expect(chipDoDia(/^dom 6\s?,\s?Fechado$/)).not.toHaveAttribute("aria-pressed", "true");
    // FF-12: o público nunca vê o tipo nem o motivo da folga
    expect(document.body.textContent).not.toMatch(/férias|folga/i);
    expect(document.body.textContent).not.toMatch(/FORA_DO_EXPEDIENTE|FECHADO/);
  });

  test("AF-10 (E18): dia aberto que ficou sem nenhum horário (folga de parte do dia que tirou tudo) aparece 'sem horário', nunca 'Fechado'; escolhido, mostra o dia cheio e a busca do próximo", async () => {
    semanaDaBarbearia();
    renderizar(<Agendar />);
    await waitFor(() => expect(chipDoDia(/^qua 2/)).toHaveTextContent("sem horário"));
    const qua = chipDoDia(/^qua 2/);
    expect(qua).not.toHaveTextContent("Fechado");
    expect(qua).not.toHaveAttribute("aria-disabled");
    fireEvent.click(qua);
    expect(await screen.findByText("Esse dia já está cheio. Veja o próximo dia com horário.")).toBeInTheDocument();
    expect(screen.queryByText("Fechado nesse dia. Veja o próximo dia com horário.")).toBeNull();
    expect(screen.getByRole("button", { name: "Ver o próximo dia com horário" })).toBeInTheDocument();
  });

  test("AF-10: dia fechado aberto pelo 'Outro dia' (ou pela URL) diz 'Fechado nesse dia.' e oferece o próximo dia com horário", async () => {
    semanaDaBarbearia();
    definirBusca(`servicoId=${CINCO_SERVICOS[1].id}&data=2026-12-06`);
    renderizar(<Agendar />);
    expect(await screen.findByText("Fechado nesse dia. Veja o próximo dia com horário.")).toHaveAttribute(
      "role",
      "status",
    );
    fireEvent.click(screen.getByRole("button", { name: "Ver o próximo dia com horário" }));
    // seg 7 está fechada: o próximo com horário é ter 8
    await waitFor(() => expect(screen.getByLabelText("Outro dia")).toHaveValue("2026-12-08"));
  });

  test("AF-11: com almoço das 12:00 às 13:00 aparece 'Fechado · 12h às 13h' entre Manhã e Tarde, sem a palavra almoço e sem chip '12:00 indisponível'; o expediente aparece", async () => {
    semanaDaBarbearia();
    definirBusca(`servicoId=${CINCO_SERVICOS[1].id}&data=2026-12-01`);
    renderizar(<Agendar />);
    const manha = await screen.findByRole("group", { name: "Manhã" });
    const tarde = screen.getByRole("group", { name: "Tarde" });
    const faixa = screen.getByText("Fechado · 12h às 13h");
    // a faixa fica entre os dois grupos na ordem do documento
    expect(manha.compareDocumentPosition(faixa) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(faixa.compareDocumentPosition(tarde) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(tarde).queryByText("12:00")).toBeNull();
    expect(within(tarde).queryByText("12:30")).toBeNull();
    expect(within(tarde).getAllByRole("button")[0]).toHaveTextContent("13:00");
    expect(screen.queryByText(/almoço/i)).toBeNull();
    expect(screen.getByText("Atende das 9h às 20h")).toBeInTheDocument();
  });

  test("AF-12: sem `expediente` (API antiga) a tira fica como antes: dia sem horário diz 'sem horário' e nada fica 'Fechado'", async () => {
    servir({ "2026-12-06": { horarios: [] } }, (data) => ({ horarios: grade(data, "09:00", "20:00") }));
    renderizar(<Agendar />);
    await waitFor(() => expect(chipDoDia(/^dom 6/)).toHaveTextContent("sem horário"));
    expect(within(tira()).queryByText("Fechado")).toBeNull();
    expect(chipDoDia(/^dom 6/)).not.toHaveAttribute("aria-disabled");
  });

  test("AF-15: folga de 14:00 a 16:00 na terça não fecha o dia; os horários que ela tira aparecem como indisponíveis, sem motivo", async () => {
    servir(
      {
        "2026-12-01": {
          horarios: grade("2026-12-01", "09:00", "20:00", (h) => h >= "14:00" && h < "16:00"),
          expediente: aberto("2026-12-01"),
        },
      },
      (data) => ({ horarios: grade(data, "09:00", "20:00"), expediente: aberto(data) }),
    );
    definirBusca(`servicoId=${CINCO_SERVICOS[1].id}&data=2026-12-01`);
    renderizar(<Agendar />);
    const tarde = await screen.findByRole("group", { name: "Tarde" });
    await waitFor(() => expect(chipDoDia(/^ter 1/)).toHaveTextContent("livre"));
    for (const h of ["14:00", "14:30", "15:00", "15:30"])
      expect(within(tarde).getByRole("button", { name: new RegExp(`^${h}\\s?indisponível$`) })).toHaveAttribute(
        "aria-disabled",
        "true",
      );
    expect(within(tarde).getByRole("button", { name: "13:30" })).not.toHaveAttribute("aria-disabled");
    expect(within(tarde).getByRole("button", { name: "16:00" })).not.toHaveAttribute("aria-disabled");
    expect(document.body.textContent).not.toMatch(/folga|férias/i);
  });
});
