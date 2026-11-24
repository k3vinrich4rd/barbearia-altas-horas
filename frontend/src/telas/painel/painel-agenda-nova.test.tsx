import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, barbeiroAdmin, colunaDoDia, gradeDoDia, resumoDoMes } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio("2026-12-01T08:00:00-03:00"); // terça
});
afterEach(() => vi.useRealTimers());

describe("agenda nova do painel (Fatia 14C)", () => {
  test("A47: /painel abre o calendário do mês e a grade de hoje; não existem Dia anterior, Próximo dia, Dia nem Semana", async () => {
    const pedidos: string[] = [];
    servidor.use(
      http.get(`${BFF}/admin/agenda/mes`, ({ request }) => {
        pedidos.push(new URL(request.url).search);
        return HttpResponse.json(resumoDoMes("2026-12", { "2026-12-01": { reservas: 1 } }));
      }),
      http.get(`${BFF}/admin/agenda/dia`, ({ request }) => {
        pedidos.push(new URL(request.url).search);
        return HttpResponse.json(
          gradeDoDia("2026-12-01", [
            colunaDoDia([agendamentoAdmin({ inicio: "2026-12-01T09:00:00-03:00", fim: "2026-12-01T10:00:00-03:00" })]),
          ]),
        );
      }),
      http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
    );
    renderizar(<PainelAgenda />, ADMIN);

    expect(await screen.findByRole("heading", { name: "Dezembro de 2026" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "terça, 1 de dezembro, 1 reserva" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Terça-feira, 1 de dezembro" })).toBeInTheDocument();
    expect(await screen.findByRole("columnheader", { name: "Horário" })).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: "09:00" })).toBeInTheDocument();
    expect(pedidos).toEqual(expect.arrayContaining(["?mes=2026-12", "?data=2026-12-01"]));
    for (const antigo of ["Dia anterior", "Próximo dia", "Dia", "Semana", "Semana anterior", "Próxima semana"])
      expect(screen.queryByRole("button", { name: antigo }), antigo).toBeNull();
  });
});
