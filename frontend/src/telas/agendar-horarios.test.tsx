import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const TZ_ORIGINAL = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "Asia/Tokyo"; // o navegador de teste está em Tóquio: a tela tem que mostrar o horário da barbearia
});
afterAll(() => {
  if (TZ_ORIGINAL === undefined) delete process.env.TZ;
  else process.env.TZ = TZ_ORIGINAL;
});
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
  servidor.use(http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)), http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])));
});
afterEach(() => vi.useRealTimers());

const disponibilidade = (barbeiros: unknown[]) =>
  HttpResponse.json({ data: "2026-11-24", fuso: "America/Sao_Paulo", servicoId: CINCO_SERVICOS[1].id, duracaoMin: 60, barbeiros });

describe("horários", () => {
  test("A19: horários no fuso da barbearia, agrupados por barbeiro, com grupo nomeado; dia vazio avisa; seletor respeita os limites", async () => {
    expect(new Date("2026-11-24T10:00:00-03:00").getHours()).toBe(22); // prova de que o fuso do navegador é Tóquio
    servidor.use(
      http.get(`${BFF}/disponibilidade`, () =>
        disponibilidade([
          { barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-11-24T10:00:00-03:00", "2026-11-24T10:30:00-03:00"] },
          { barbeiroId: "b2", nomeExibicao: "Outro Barbeiro", horarios: ["2026-11-24T10:30:00-03:00"] },
        ]),
      ),
    );
    renderizar(<Agendar />);
    const dia = await screen.findByLabelText("Dia");
    expect(dia).toHaveAttribute("min", "2026-11-18");
    expect(dia).toHaveAttribute("max", "2026-12-18");

    fireEvent.change(dia, { target: { value: "2026-11-24" } });
    const grupo = await screen.findByRole("group", { name: "Horários de Carlos Eduardo, terça 24/11" });
    expect(within(grupo).getAllByRole("button").map((b) => b.textContent)).toEqual(["10:00", "10:30"]);
    const outro = screen.getByRole("group", { name: "Horários de Outro Barbeiro, terça 24/11" });
    expect(within(outro).getAllByRole("button").map((b) => b.textContent)).toEqual(["10:30"]);

    // fora do intervalo: avisa e não chama a API (handler só responde à data válida)
    let chamadas = 0;
    servidor.use(http.get(`${BFF}/disponibilidade`, () => { chamadas++; return disponibilidade([]); }));
    fireEvent.change(dia, { target: { value: "2027-01-30" } });
    expect(await screen.findByText(/Escolha um dia de hoje até/)).toBeInTheDocument();
    fireEvent.change(dia, { target: { value: "2026-11-10" } });
    expect(screen.getByText(/Escolha um dia de hoje até/)).toBeInTheDocument();
    expect(chamadas).toBe(0);

    // dia sem horários
    fireEvent.change(dia, { target: { value: "2026-11-25" } });
    expect(await screen.findByText(/Sem horário livre neste dia/)).toBeInTheDocument();
    expect(screen.queryByRole("group")).toBeNull();
  });

  test("A20: 422 FORA_DO_HORIZONTE mostra a mensagem junto do campo, leva o foco a ela e limpa os horários antigos", async () => {
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) =>
        new URL(request.url).searchParams.get("data") === "2026-12-10"
          ? erro(422, "FORA_DO_HORIZONTE")
          : disponibilidade([{ barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-11-24T10:00:00-03:00"] }]),
      ),
    );
    renderizar(<Agendar />);
    const dia = await screen.findByLabelText("Dia");
    fireEvent.change(dia, { target: { value: "2026-11-24" } });
    expect(await screen.findByRole("button", { name: "10:00" })).toBeInTheDocument();

    fireEvent.change(dia, { target: { value: "2026-12-10" } });
    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Só dá para agendar com até 30 dias de antecedência.");
    await waitFor(() => expect(alerta).toHaveFocus());
    expect(screen.queryByRole("button", { name: "10:00" })).toBeNull();
    expect(dia.parentElement).toContainElement(alerta);
  });
});
