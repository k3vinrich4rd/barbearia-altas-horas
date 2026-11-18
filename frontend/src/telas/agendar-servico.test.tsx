import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

describe("escolha de serviço", () => {
  test("A18: radiogroup com nome, duração e preço; escolher leva o servicoId para o passo seguinte; lista vazia avisa", async () => {
    let consulta: URLSearchParams | undefined;
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
      http.get(`${BFF}/disponibilidade`, ({ request }) => {
        consulta = new URL(request.url).searchParams;
        return HttpResponse.json({ data: "2026-11-24", fuso: "America/Sao_Paulo", servicoId: CINCO_SERVICOS[2].id, duracaoMin: 60, barbeiros: [{ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, horarios: ["2026-11-24T10:00:00-03:00"] }] });
      }),
    );
    const { user } = renderizar(<Agendar />);

    const grupo = await screen.findByRole("radiogroup");
    const opcoes = within(grupo).getAllByRole("radio");
    expect(opcoes).toHaveLength(5);
    expect(within(grupo).getByText("R$ 60,00")).toBeInTheDocument();
    expect(grupo).toHaveTextContent("60 min");

    await user.click(within(grupo).getByRole("radio", { name: /Visagismo/ }));
    expect(opcoes[2]).toBeChecked();
    fireEvent.change(screen.getByLabelText("Dia"), { target: { value: "2026-11-24" } });
    await waitFor(() => expect(consulta?.get("servicoId")).toBe(CINCO_SERVICOS[2].id));

    await user.click(await screen.findByRole("button", { name: "10:00" }));
    const destino = new URL(roteador.push.mock.calls[0][0], "http://x");
    expect(destino.pathname).toBe("/agendar/dados");
    expect(destino.searchParams.get("servicoId")).toBe(CINCO_SERVICOS[2].id);
    expect(destino.searchParams.get("barbeiroId")).toBe(BARBEIRO.id);
    expect(destino.searchParams.get("inicio")).toBe("2026-11-24T10:00:00-03:00");
  });

  test("A18: sem serviços mostra 'sem serviços no momento'", async () => {
    definirBusca("");
    servidor.use(http.get(`${BFF}/servicos`, () => HttpResponse.json([])), http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])));
    renderizar(<Agendar />);
    expect(await screen.findByText("Sem serviços no momento.")).toBeInTheDocument();
    expect(screen.queryByRole("radio")).toBeNull();
  });
});
