import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, fixarRelogio, renderizar } from "@/testes/render";
import { ReservaDados } from "./ReservaDados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const INICIO = "2026-11-24T10:00:00-03:00";
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(
    new URLSearchParams({ servicoId: CINCO_SERVICOS[1].id, barbeiroId: BARBEIRO.id, inicio: INICIO }).toString(),
  );
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
  );
});
afterEach(() => vi.useRealTimers());

describe("reserva do cliente logado", () => {
  test("A25: POST /agendamentos só com servicoId, barbeiroId e inicio; 401 e 403 têm mensagem própria; o resto segue igual", async () => {
    const corpos: Record<string, unknown>[] = [];
    let resposta = () => HttpResponse.json(reserva({ origem: "CLIENTE" }), { status: 201 });
    servidor.use(
      http.post(`${BFF}/agendamentos`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return resposta();
      }),
    );

    const { user, unmount } = renderizar(<ReservaDados />, CLIENTE);
    await screen.findByRole("button", { name: "Reservar" });
    expect(screen.queryByLabelText("Nome")).toBeNull();
    expect(screen.queryByLabelText("E-mail")).toBeNull();
    expect(screen.queryByLabelText("WhatsApp")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Reservar" }));
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/pagamento/${reserva().id}`));
    expect(Object.keys(corpos[0]).sort()).toEqual(["barbeiroId", "inicio", "servicoId"]);
    unmount();

    resposta = () => erro(403, "ACESSO_NEGADO") as never;
    const a = renderizar(<ReservaDados />, CLIENTE);
    await a.user.click(await screen.findByRole("button", { name: "Reservar" }));
    expect(await screen.findByText(/Contas da equipe não fazem reservas como cliente/)).toBeInTheDocument();
    a.unmount();

    resposta = () => erro(401, "NAO_AUTENTICADO") as never;
    const b = renderizar(<ReservaDados />, CLIENTE);
    await b.user.click(await screen.findByRole("button", { name: "Reservar" }));
    expect(await screen.findByText("Entre na sua conta para continuar.", { exact: false })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Entrar" })).toBeInTheDocument();
  });
});
