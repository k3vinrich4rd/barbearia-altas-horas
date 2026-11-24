import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { barbeiroAdmin } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { SessaoProvider } from "@/componentes/Sessao";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  servidor.use(
    http.get(`${BFF}/admin/agenda`, () => HttpResponse.json([])),
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
