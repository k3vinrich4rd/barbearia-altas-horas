import { screen } from "@testing-library/react";
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
  fixarRelogio("2026-12-01T20:30:00-03:00"); // terça à noite: hoje já não tem horário
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/disponibilidade`, ({ request }) => {
      const data = new URL(request.url).searchParams.get("data")!;
      const horarios = data === "2026-12-01" ? [] : [`${data}T07:00:00-03:00`, `${data}T07:30:00-03:00`];
      return HttpResponse.json({
        data,
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 60,
        barbeiros: [{ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, horarios }],
      });
    }),
  );
});
afterEach(() => vi.useRealTimers());

describe("próximo horário livre (Fatia 14D, M2)", () => {
  test("A55: com o serviço escolhido aparece 'Próximo livre: qua 02/12 às 07:00'; um toque seleciona o dia e o horário e habilita Continuar", async () => {
    const { user } = renderizar(<Agendar />);
    const proximo = await screen.findByRole("button", { name: "Próximo livre: qua 02/12 às 07:00" });
    expect(screen.getByRole("button", { name: "Continuar" })).toBeDisabled();

    await user.click(proximo);
    expect(await screen.findByRole("button", { name: "07:00" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Outro dia")).toHaveValue("2026-12-02");
    const continuar = screen.getByRole("button", { name: "Continuar" });
    expect(continuar).toBeEnabled();
    await user.click(continuar);
    expect(roteador.push).toHaveBeenCalledWith(
      `/agendar/dados?${new URLSearchParams({
        servicoId: CINCO_SERVICOS[1].id,
        barbeiroId: BARBEIRO.id,
        inicio: "2026-12-02T07:00:00-03:00",
      })}`,
    );
  });
});
