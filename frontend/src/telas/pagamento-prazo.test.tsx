import { act, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { ligarServidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { api, montar } from "@/testes/pagamento";
import { reserva } from "@/testes/dados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// "agora" fixo e timers falsos: o prazo vem do expiraEm da API (hoje 3 min), nunca de um número fixo no código.
beforeEach(() => {
  zerarNavegacao();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date("2026-11-18T08:00:00-03:00"));
});
afterEach(() => vi.useRealTimers());

const avancar = (ms: number) =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
const PRAZO_MS = new Date(reserva().expiraEm).getTime() - new Date("2026-11-18T08:00:00-03:00").getTime();

describe("prazo do pagamento", () => {
  test("A29: mostra o tempo restante vindo do expiraEm, consulta o status UMA vez ao zerar e só a API decide (expirado, confirmado ou ainda aguardando)", async () => {
    // A API diz EXPIRADO só depois da primeira leitura
    let leituras = 0;
    api({ status: () => (++leituras === 1 ? "AGUARDANDO_PAGAMENTO" : "EXPIRADO") });
    montar();
    expect(await screen.findByText("03:00")).toBeInTheDocument();
    expect(leituras).toBe(1);
    await avancar(60_000);
    expect(screen.getByText("02:00")).toBeInTheDocument();
    expect(screen.getByText(/Faltam 2 minutos para pagar/)).toBeInTheDocument();

    await avancar(PRAZO_MS - 60_000 + 1000);
    expect(await screen.findByText(/O tempo acabou e o horário voltou para a agenda/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Escolher outro horário" })).toHaveAttribute("href", "/agendar");
    expect(screen.queryByRole("button", { name: "Pagar" })).toBeNull();
    expect(leituras).toBe(2); // uma consulta ao zerar
    await avancar(20_000);
    expect(leituras).toBe(2);
  });

  test("A29: se pagou no limite (CONFIRMADO) segue para a confirmação; se a API ainda diz AGUARDANDO, o relógio sozinho não encerra", async () => {
    let leituras = 0;
    api({ status: () => (++leituras === 1 ? "AGUARDANDO_PAGAMENTO" : "CONFIRMADO") });
    const { unmount } = montar();
    await screen.findByText("03:00");
    await avancar(PRAZO_MS + 1000);
    await vi.waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/confirmacao/${reserva().id}`));
    unmount();
    vi.setSystemTime(new Date("2026-11-18T08:00:00-03:00"));

    api({ status: () => "AGUARDANDO_PAGAMENTO" });
    montar();
    await screen.findByText("03:00");
    await avancar(PRAZO_MS + 1000);
    expect(await screen.findByText("00:00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pagar" })).toBeInTheDocument();
    expect(screen.queryByText(/O tempo acabou e o horário voltou/)).toBeNull();
  });
});
