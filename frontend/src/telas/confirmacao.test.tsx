import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PAGAMENTO, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, fixarRelogio, renderizar } from "@/testes/render";
import { Confirmacao } from "./Confirmacao";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const ID = reserva().id;
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  servidor.use(http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)));
});
afterEach(() => vi.useRealTimers());

describe("confirmação", () => {
  test("A31: resumo no fuso, aviso da política, código do visitante com copiar e link com fragmento, link para o cliente e 404 amigável", async () => {
    const confirmada = reserva({ status: "CONFIRMADO", expiraEm: null });
    servidor.use(
      http.get(`${BFF}/agendamentos/guest/${ID}`, () => HttpResponse.json(confirmada)),
      http.get(`${BFF}/visitante`, () => HttpResponse.json({ reservaId: ID, codigo: "CODIGO-ABC-123" })),
    );
    const v = renderizar(<Confirmacao reservaId={ID} />, ANONIMO);
    expect(await screen.findByText(/terça, 24\/11 às 10:00/)).toBeInTheDocument();
    expect(screen.getByText(/Corte Degradê/)).toBeInTheDocument();
    expect(screen.getByText(/Valor pago: R\$ 55,00/)).toBeInTheDocument();
    expect(screen.getByText(/Confirmado/)).toBeInTheDocument();
    expect(await screen.findByText(POLITICA_PAGAMENTO.texto)).toBeInTheDocument(); // sem cancelamento nem remarcação (R18)
    expect(screen.queryByRole("button", { name: /cancelar|remarcar/i })).toBeNull();

    expect(await screen.findByLabelText("Código")).toHaveValue("CODIGO-ABC-123");
    expect(screen.getAllByRole("button", { name: "Copiar" })).toHaveLength(2);
    expect(screen.getByLabelText("Link para acompanhar")).toHaveValue(`http://localhost:3000/reserva#id=${ID}&codigo=CODIGO-ABC-123`);
    expect(screen.getByText(/Guarde este código: ele é a única forma de ver esta reserva/)).toBeInTheDocument();
    v.unmount();

    // cliente: link para Meus agendamentos e nenhum código
    servidor.use(http.get(`${BFF}/agendamentos/${ID}`, () => HttpResponse.json(reserva({ status: "CONFIRMADO", origem: "CLIENTE", expiraEm: null }))));
    const c = renderizar(<Confirmacao reservaId={ID} />, CLIENTE);
    expect(await screen.findByRole("link", { name: "Ver meus agendamentos" })).toHaveAttribute("href", "/meus-agendamentos");
    expect(screen.queryByText(/Código da reserva/)).toBeNull();
    c.unmount();

    // 404
    servidor.use(http.get(`${BFF}/agendamentos/guest/${ID}`, () => erro(404, "RECURSO_NAO_ENCONTRADO")));
    renderizar(<Confirmacao reservaId={ID} />, ANONIMO);
    expect(await screen.findByText("Não encontramos esta reserva.")).toBeInTheDocument();
  });
});
