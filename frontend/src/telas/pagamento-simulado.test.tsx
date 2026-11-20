import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { cobrancaPix } from "@/testes/pagamento";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { PagamentoSimulado } from "@/telas/PagamentoSimulado";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const ROTULO = "Ver a tela de confirmação (simula o pagamento aprovado)";

const cobranca = cobrancaPix({
  metodo: "CARTAO_CREDITO",
  pix: null,
  status: "PAGA",
  cartao: { urlCheckout: "https://barbearia.test/pagamento-simulado/c0000000-0000-4000-8000-000000000001" },
});

beforeEach(() => {
  zerarNavegacao();
});

describe("pagamento simulado", () => {
  test("A63: 200 na confirmação simulada leva à tela de confirmação, sem campo de cartão", async () => {
    let chamado = 0;
    servidor.use(
      http.post(`${BFF}/pagamentos/simulacao/:cobrancaId/confirmar`, ({ params }) => {
        chamado += 1;
        expect(params.cobrancaId).toBe(cobranca.id);
        return HttpResponse.json(cobranca);
      }),
    );
    const { user } = renderizar(<PagamentoSimulado cobrancaId={cobranca.id} />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Pagamento com cartão");
    expect(screen.queryByLabelText(/número do cartão|cvv|validade/i)).toBeNull();
    expect(document.querySelector('input[autocomplete^="cc-"]')).toBeNull();
    await user.click(screen.getByRole("button", { name: ROTULO }));
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/confirmacao/${cobranca.reservaId}`));
    expect(chamado).toBe(1);
  });

  test("A63: 404 (cobrança de outro dono ou inexistente) mostra o erro e não sai da tela", async () => {
    servidor.use(
      http.post(`${BFF}/pagamentos/simulacao/:cobrancaId/confirmar`, () => erro(404, "RECURSO_NAO_ENCONTRADO")),
    );
    const { user } = renderizar(<PagamentoSimulado cobrancaId={cobranca.id} />);
    await user.click(screen.getByRole("button", { name: ROTULO }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(roteador.push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: ROTULO })).toBeEnabled();
  });

  test("A63: visitante sem cookie recebe 401, vê o erro e não é redirecionado", async () => {
    servidor.use(http.post(`${BFF}/pagamentos/simulacao/:cobrancaId/confirmar`, () => erro(401, "NAO_AUTENTICADO")));
    const { user } = renderizar(<PagamentoSimulado cobrancaId={cobranca.id} />);
    await user.click(screen.getByRole("button", { name: ROTULO }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(roteador.push).not.toHaveBeenCalled();
    expect(roteador.replace).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Pagamento com cartão");
  });
});
