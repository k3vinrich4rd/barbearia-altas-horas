import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PAGAMENTO, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, fixarRelogio, renderizar } from "@/testes/render";
import { Confirmacao } from "./Confirmacao";
import { ConsultaVisitante } from "./ConsultaVisitante";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const ID = reserva().id;
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  servidor.use(
    http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
    http.get(`${BFF}/visitante`, () => HttpResponse.json({ reservaId: ID, codigo: "CODIGO-ABC-123" })),
  );
});
afterEach(() => vi.useRealTimers());

const confirmada = (regiaoAutorizada: boolean) =>
  reserva({ status: "CONFIRMADO", expiraEm: null, regiaoAutorizada });

describe("retirar a autorização de região", () => {
  test("A48: na confirmação o link \"Retirar minha autorização de região\" chama o BFF (que usa o cookie do visitante), apaga na hora e mostra o sucesso; sem região autorizada o link nem aparece", async () => {
    let chamadas = 0;
    servidor.use(
      http.get(`${BFF}/agendamentos/guest/${ID}`, () => HttpResponse.json(confirmada(true))),
      http.delete(`${BFF}/agendamentos/guest/${ID}/regiao`, () => {
        chamadas++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const v = renderizar(<Confirmacao reservaId={ID} />, ANONIMO);
    const botao = await screen.findByRole("button", { name: "Retirar minha autorização de região" });
    await v.user.click(botao);
    expect(await screen.findByText(/a região desta reserva foi apagada/)).toBeInTheDocument();
    expect(chamadas).toBe(1);
    expect(screen.queryByRole("button", { name: "Retirar minha autorização de região" })).toBeNull();
    v.unmount();

    servidor.use(http.get(`${BFF}/agendamentos/guest/${ID}`, () => HttpResponse.json(confirmada(false))));
    renderizar(<Confirmacao reservaId={ID} />, ANONIMO);
    await screen.findByText(/Valor pago/);
    expect(screen.queryByRole("button", { name: "Retirar minha autorização de região" })).toBeNull();
  });

  test("A48: o erro aparece como alerta e dá para tentar de novo; a consulta da reserva manda o código no cabeçalho", async () => {
    let tentativas = 0;
    const cabecalhos: (string | null)[] = [];
    servidor.use(
      http.get(`${BFF}/agendamentos/guest/${ID}`, () => HttpResponse.json(confirmada(true))),
      http.delete(`${BFF}/agendamentos/guest/${ID}/regiao`, ({ request }) => {
        tentativas++;
        cabecalhos.push(request.headers.get("x-guest-token"));
        return tentativas === 1 ? erro(404, "RECURSO_NAO_ENCONTRADO") : new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderizar(<ConsultaVisitante />, ANONIMO);
    await user.type(screen.getByLabelText("Número da reserva"), ID);
    await user.type(screen.getByLabelText("Código da reserva"), "CODIGO-ABC-123");
    await user.click(screen.getByRole("button", { name: "Consultar" }));

    const botao = await screen.findByRole("button", { name: "Retirar minha autorização de região" });
    await user.click(botao);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retirar minha autorização de região" }));
    expect(await screen.findByText(/a região desta reserva foi apagada/)).toBeInTheDocument();
    await waitFor(() => expect(tentativas).toBe(2));
    expect(cabecalhos).toEqual(["CODIGO-ABC-123", "CODIGO-ABC-123"]);
  });
});
