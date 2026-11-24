import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { PASSOS_DA_FAIXA } from "@/conteudo/historia";
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
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/disponibilidade`, ({ request }) =>
      HttpResponse.json({
        data: new URL(request.url).searchParams.get("data"),
        fuso: "America/Sao_Paulo",
        servicoId: CINCO_SERVICOS[1].id,
        duracaoMin: 60,
        barbeiros: [
          { barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-11-24T10:00:00-03:00"] },
        ],
      }),
    ),
  );
});
afterEach(() => vi.useRealTimers());

describe("resumo fixo de /agendar (Fatia 12C)", () => {
  test("A18: com serviço e horário o resumo mostra serviço, dia e hora e preço e 'Continuar' habilita; sem horário fica desabilitado", async () => {
    const { user } = renderizar(<Agendar />);
    const resumo = await screen.findByRole("complementary");
    const continuar = within(resumo).getByRole("button", { name: "Continuar" });
    expect(continuar).toBeDisabled();
    expect(resumo).toHaveTextContent("Escolha o horário");

    // o próximo dia com horário já vem escolhido: basta o chip
    const chip = await screen.findByRole("button", { name: "10:00" });
    await user.click(chip);
    expect(continuar).toBeEnabled();
    expect(resumo).toHaveTextContent(CINCO_SERVICOS[1].nome);
    expect(resumo).toHaveTextContent(/às 10:00/);
    expect(resumo).toHaveTextContent("R$ 55,00");

    await user.click(continuar);
    const destino = new URL(roteador.push.mock.calls[0][0], "http://x");
    expect(destino.pathname).toBe("/agendar/dados");
    expect(destino.searchParams.get("barbeiroId")).toBe(BARBEIRO.id);

    // trocar de dia limpa a escolha: o botão volta a ficar desabilitado até escolher de novo
    fireEvent.change(screen.getByLabelText(/Outro dia/), { target: { value: "2026-11-30" } });
    expect(await screen.findByRole("button", { name: "10:00" })).toHaveAttribute("aria-pressed", "false");
    expect(continuar).toBeDisabled();
  });

  test("A18: com um só barbeiro, o seletor de barbeiro some; com dois, vira opções", async () => {
    const { unmount } = renderizar(<Agendar />);
    await screen.findByRole("button", { name: "10:00" });
    expect(screen.queryByRole("radiogroup", { name: /Barbeiro/ })).toBeNull();
    unmount();

    servidor.use(
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO, { id: "b2", nomeExibicao: "Outro Barbeiro" }])),
    );
    renderizar(<Agendar />);
    const grupo = await screen.findByRole("radiogroup", { name: /Barbeiro/ });
    expect(within(grupo).getAllByRole("radio")).toHaveLength(3);
  });

  test("A19: antes da reserva, o resumo e a faixa da home falam em prazo curto, sem número de minutos", async () => {
    renderizar(<Agendar />);
    const resumo = await screen.findByRole("complementary");
    expect(resumo).toHaveTextContent(/prazo curto/);
    expect(resumo.textContent).not.toMatch(/\d+\s*(min|minutos)/i);
    const faixa = PASSOS_DA_FAIXA.map((p) => p.texto).join(" ");
    expect(faixa).toMatch(/prazo curto/);
    expect(faixa).not.toMatch(/\d+\s*(min|minutos)/i);
  });
});
