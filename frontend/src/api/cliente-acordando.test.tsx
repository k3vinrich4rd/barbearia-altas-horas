import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { CINCO_SERVICOS } from "@/testes/dados";
import { SessaoProvider } from "@/componentes/Sessao";
import { delay, http, HttpResponse, BFF, ligarServidor, servidor } from "@/testes/msw";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);

ligarServidor();
beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  servidor.use(
    http.get(`${BFF}/alcance`, () =>
      HttpResponse.json({ atualizadoEm: "2026-11-18T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }),
    ),
  );
});
afterEach(() => vi.useRealTimers());

const tela = () =>
  render(
    <SessaoProvider inicial={{ autenticado: false }}>
      <Home />
    </SessaoProvider>,
  );
const avancar = (ms: number) =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });

describe("servidor acordando", () => {
  test("A14: nova tentativa a cada 3 s com aviso role=status; some quando a API responde", async () => {
    let chamadas = 0;
    servidor.use(
      http.get(`${BFF}/servicos`, () => {
        chamadas++;
        return chamadas < 3 ? HttpResponse.error() : HttpResponse.json(CINCO_SERVICOS);
      }),
    );
    tela();
    await screen.findByText(/A agenda estava descansando e já está acordando/);
    expect(chamadas).toBe(1);
    await avancar(2900);
    expect(chamadas).toBe(1);
    await avancar(200);
    await vi.waitFor(() => expect(chamadas).toBe(2));
    await screen.findByText(/A agenda estava descansando e já está acordando/);
    await avancar(3100);
    await screen.findByText("Corte Clássico / Social");
    expect(chamadas).toBe(3);
    expect(screen.queryByText(/A agenda estava descansando e já está acordando/)).toBeNull();
  });

  test("A14: se a API nunca volta, depois de 70 s mostra a mensagem final com 'Tentar de novo', sem laço e sem requisições em paralelo", async () => {
    let emAndamento = 0;
    let maximo = 0;
    let total = 0;
    servidor.use(
      http.get(`${BFF}/servicos`, async () => {
        total++;
        emAndamento++;
        maximo = Math.max(maximo, emAndamento);
        await delay(10);
        emAndamento--;
        return new HttpResponse("<html>502</html>", { status: 502, headers: { "content-type": "text/html" } });
      }),
    );
    tela();
    await screen.findByText(/A agenda estava descansando e já está acordando/);
    for (let i = 0; i < 30; i++) await avancar(3000);
    await screen.findByText(/não respondeu/i);
    expect(screen.getByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    const aoParar = total;
    await avancar(30000);
    expect(total).toBe(aoParar);
    expect(total).toBeLessThanOrEqual(25);
    expect(maximo).toBe(1);
  }, 30000);
});
