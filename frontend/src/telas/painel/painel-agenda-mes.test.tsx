import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { barbeiroAdmin, colunaDoDia, gradeDoDia, resumoDoMes } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const meses: string[] = [];
const dias: string[] = [];

beforeEach(() => {
  zerarNavegacao();
  meses.length = 0;
  dias.length = 0;
  fixarRelogio("2026-12-10T08:00:00-03:00"); // quinta, 10/12/2026
  servidor.use(
    http.get(`${BFF}/admin/agenda/mes`, ({ request }) => {
      const mes = new URL(request.url).searchParams.get("mes") ?? "";
      meses.push(mes);
      return HttpResponse.json(
        resumoDoMes(mes, { "2026-12-01": { reservas: 3 }, "2026-12-02": { reservas: 0, folga: true } }),
      );
    }),
    http.get(`${BFF}/admin/agenda/dia`, ({ request }) => {
      const data = new URL(request.url).searchParams.get("data") ?? "";
      dias.push(data);
      return HttpResponse.json(gradeDoDia(data, [colunaDoDia([])]));
    }),
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
  );
});
afterEach(() => vi.useRealTimers());

describe("calendário do mês no painel (Fatia 14C)", () => {
  test("A48: o dia 1 de dezembro se chama 'terça, 1 de dezembro, 3 reservas' e o dia 2 diz folga; tocar o dia 1 abre a grade de 01/12 e a URL vira ?data=2026-12-01; as setas movem o foco e PageDown vai para janeiro", async () => {
    const { user } = renderizar(<PainelAgenda />, ADMIN);

    const dia1 = await screen.findByRole("button", { name: "terça, 1 de dezembro, 3 reservas" });
    expect(screen.getByRole("button", { name: /^quarta, 2 de dezembro, sem reservas, folga$/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^domingo, 6 de dezembro, sem reservas, fechado$/ })).toBeInTheDocument();

    await user.click(dia1);
    await vi.waitFor(() => expect(dias.at(-1)).toBe("2026-12-01"));
    expect(roteador.replace).toHaveBeenLastCalledWith("/painel?data=2026-12-01", { scroll: false });
    expect(await screen.findByRole("heading", { name: "Terça-feira, 1 de dezembro" })).toBeInTheDocument();
    expect(dia1).toHaveAttribute("aria-pressed", "true");

    // teclado: a seta leva o foco ao dia seguinte (o dia com o foco é o único no Tab)
    dia1.focus();
    await user.keyboard("{ArrowRight}");
    const dia2 = screen.getByRole("button", { name: /^quarta, 2 de dezembro/ });
    expect(dia2).toHaveFocus();
    expect(dia2).toHaveAttribute("tabindex", "0");
    expect(dia1).toHaveAttribute("tabindex", "-1");
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: /^quarta, 9 de dezembro/ })).toHaveFocus();
    await user.keyboard("{Home}");
    expect(screen.getByRole("button", { name: /^segunda, 7 de dezembro/ })).toHaveFocus();

    // PageDown troca o mês: carrega janeiro e o foco vai para o mesmo dia de janeiro
    await user.keyboard("{PageDown}");
    expect(await screen.findByRole("heading", { name: "Janeiro de 2027" })).toBeInTheDocument();
    expect(meses).toContain("2027-01");
    await vi.waitFor(() => expect(screen.getByRole("button", { name: /^quinta, 7 de janeiro/ })).toHaveFocus());
  });

  test("A49: escolher 15/01/2027 no seletor de data carrega o mês 2027-01 e a grade de 15/01; Hoje volta para o mês e o dia de hoje", async () => {
    const { user } = renderizar(<PainelAgenda />, ADMIN);
    await screen.findByRole("heading", { name: "Dezembro de 2026" });

    fireEvent.change(screen.getByLabelText("Data"), { target: { value: "2027-01-15" } });
    expect(await screen.findByRole("heading", { name: "Janeiro de 2027" })).toBeInTheDocument();
    await vi.waitFor(() => expect(dias.at(-1)).toBe("2027-01-15"));
    expect(meses.at(-1)).toBe("2027-01");
    expect(screen.getByRole("heading", { name: "Sexta-feira, 15 de janeiro" })).toBeInTheDocument();
    expect(roteador.replace).toHaveBeenLastCalledWith("/painel?data=2027-01-15", { scroll: false });

    await user.click(screen.getByRole("button", { name: "Hoje" }));
    expect(await screen.findByRole("heading", { name: "Dezembro de 2026" })).toBeInTheDocument();
    await vi.waitFor(() => expect(dias.at(-1)).toBe("2026-12-10"));
    expect(meses.at(-1)).toBe("2026-12");
    expect(screen.getByRole("heading", { name: "Quinta-feira, 10 de dezembro" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hoje" })).toBeDisabled();
  });
});
