import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";
import { ReservaDados } from "./ReservaDados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const INICIO = "2026-11-24T10:00:00-03:00";
const ESCOLHA = { servicoId: CINCO_SERVICOS[1].id, barbeiroId: BARBEIRO.id, inicio: INICIO };

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/disponibilidade`, ({ request }) => {
      const data = new URL(request.url).searchParams.get("data")!;
      return HttpResponse.json({
        data,
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 60,
        barbeiros: [
          {
            barbeiroId: BARBEIRO.id,
            nomeExibicao: BARBEIRO.nomeExibicao,
            horarios: [`${data}T09:00:00-03:00`, `${data}T10:00:00-03:00`],
          },
        ],
      });
    }),
  );
});
afterEach(() => vi.useRealTimers());

describe("resumo fixo em /agendar/dados (Fatia 14D, M3)", () => {
  test("A56: o resumo fixo mostra serviço, dia e hora e preço, com Alterar voltando para /agendar com tudo marcado", async () => {
    definirBusca(new URLSearchParams(ESCOLHA).toString());
    const { unmount } = renderizar(<ReservaDados />, ANONIMO);
    const resumo = await screen.findByRole("complementary", { name: "Resumo" });
    expect(await within(resumo).findByText("Corte Degradê / Fade / Navalhado")).toBeInTheDocument();
    expect(within(resumo).getByText(/terça, 24\/11 às 10:00/)).toBeInTheDocument();
    expect(within(resumo).getByText("R$ 55,00")).toBeInTheDocument();
    const alterar = within(resumo).getByRole("link", { name: "Alterar" });
    const destino = new URL(alterar.getAttribute("href")!, "http://localhost");
    expect(destino.pathname).toBe("/agendar");
    expect(Object.fromEntries(destino.searchParams)).toEqual({ ...ESCOLHA, data: "2026-11-24" });
    unmount();

    // de volta em /agendar: serviço, dia e horário já marcados, e Continuar habilitado
    definirBusca(destino.searchParams.toString());
    renderizar(<Agendar />);
    expect(await screen.findByRole("radio", { name: /Corte Degradê/ })).toBeChecked();
    expect(screen.getByLabelText("Outro dia")).toHaveValue("2026-11-24");
    await vi.waitFor(() =>
      expect(screen.getByRole("button", { name: "10:00" })).toHaveAttribute("aria-pressed", "true"),
    );
    expect(screen.getByRole("button", { name: "Continuar" })).toBeEnabled();
  });
});
