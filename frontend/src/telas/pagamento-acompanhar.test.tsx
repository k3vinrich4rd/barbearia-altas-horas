import { act, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { api, cobrancaPix, montar, preencher } from "@/testes/pagamento";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("qrcode", () => ({ default: { toDataURL: vi.fn(async () => "data:image/png;base64,QR") } }));
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date("2026-11-18T08:00:00-03:00"));
  servidor.use(http.post(`${BFF}/pagamentos/cobrancas`, () => HttpResponse.json(cobrancaPix(), { status: 201 })));
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.useRealTimers();
});
const avancar = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
const opcoes = () => ({ advanceTimers: vi.advanceTimersByTime });

describe("acompanhar o pagamento", () => {
  test("A30: consulta o status a cada 3 s e para ao CONFIRMADO; o botão Simular só existe com a variável ligada e some com 404", async () => {
    vi.stubEnv("NEXT_PUBLIC_PAGAMENTO_SIMULADO", "true");
    let status = "AGUARDANDO_PAGAMENTO";
    const leituras = api({ status: () => status });
    let simulacoes = 0;
    servidor.use(http.post(`${BFF}/pagamentos/simulacao/:id/confirmar`, () => { simulacoes++; return HttpResponse.json(cobrancaPix({ status: "PAGA" })); }));
    const { user, unmount } = montar(opcoes());
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    await screen.findByLabelText("Pix copia e cola");

    const base = leituras.reserva;
    await avancar(3100);
    expect(leituras.reserva).toBe(base + 1);
    await avancar(3000);
    expect(leituras.reserva).toBe(base + 2);

    // o botão chama a rota de simulação
    await user.click(screen.getByRole("button", { name: "Simular pagamento" }));
    expect(simulacoes).toBe(1);

    status = "CONFIRMADO";
    await avancar(3100);
    await vi.waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/confirmacao/${reserva().id}`));
    const parou = leituras.reserva;
    await avancar(9000);
    expect(leituras.reserva).toBe(parou);
    unmount();

    // modo asaas: a rota responde 404 e o botão some sem erro
    status = "AGUARDANDO_PAGAMENTO";
    servidor.use(http.post(`${BFF}/pagamentos/simulacao/:id/confirmar`, () => erro(404, "RECURSO_NAO_ENCONTRADO")));
    const outra = montar(opcoes());
    await preencher(outra.user);
    await outra.user.click(screen.getByRole("button", { name: "Pagar" }));
    await outra.user.click(await screen.findByRole("button", { name: "Simular pagamento" }));
    await vi.waitFor(() => expect(screen.queryByRole("button", { name: "Simular pagamento" })).toBeNull());
    expect(screen.queryByRole("alert")).toBeNull();
  });

  test("A30: sem a variável o botão não existe no HTML", async () => {
    vi.stubEnv("NEXT_PUBLIC_PAGAMENTO_SIMULADO", "");
    api();
    const { user } = montar(opcoes());
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    await screen.findByLabelText("Pix copia e cola");
    expect(screen.queryByRole("button", { name: /simular/i })).toBeNull();
    expect(document.body.innerHTML).not.toMatch(/simular/i);
  });
});
