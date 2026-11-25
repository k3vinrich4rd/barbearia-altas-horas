import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

/** 07:00 a 19:30 de 30 em 30 min, no fuso da barbearia. */
const horarios = (de: number, ate: number) =>
  Array.from({ length: (ate - de) * 2 }, (_, i) => {
    const m = de * 60 + i * 30;
    return `2026-11-24T${String(Math.floor(m / 60)).padStart(2, "0")}:${m % 60 ? "30" : "00"}:00-03:00`;
  });

function disponivel(lista: string[]) {
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/disponibilidade`, () =>
      HttpResponse.json({
        data: "2026-11-24",
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 30,
        barbeiros: [{ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, horarios: lista }],
      }),
    ),
  );
}

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}&data=2026-11-24`);
});
afterEach(() => vi.useRealTimers());

describe("horários por período (Fatia 14D, M1)", () => {
  test("A54: 07:00 a 19:30 livres viram Manhã (07:00 a 11:30), Tarde (12:00 a 17:30) e Noite (18:00 a 19:30), cada um um group com nome; período sem horário não aparece", async () => {
    disponivel(horarios(7, 20));
    const { unmount } = renderizar(<Agendar />);
    const manha = await screen.findByRole("group", { name: "Manhã" });
    const tarde = screen.getByRole("group", { name: "Tarde" });
    const noite = screen.getByRole("group", { name: "Noite" });
    const textos = (g: HTMLElement) =>
      within(g)
        .getAllByRole("button")
        .map((b) => b.textContent);
    expect(textos(manha)).toEqual([
      "07:00",
      "07:30",
      "08:00",
      "08:30",
      "09:00",
      "09:30",
      "10:00",
      "10:30",
      "11:00",
      "11:30",
    ]);
    expect(textos(tarde)[0]).toBe("12:00");
    expect(textos(tarde).at(-1)).toBe("17:30");
    expect(textos(tarde)).toHaveLength(12);
    expect(textos(noite)).toEqual(["18:00", "18:30", "19:00", "19:30"]);
    unmount();

    // só de tarde: Manhã e Noite somem
    disponivel(horarios(13, 16));
    renderizar(<Agendar />);
    expect(await screen.findByRole("group", { name: "Tarde" })).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Manhã" })).toBeNull();
    expect(screen.queryByRole("group", { name: "Noite" })).toBeNull();
  });
});
