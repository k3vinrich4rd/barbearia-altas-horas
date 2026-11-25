import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, delay, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "@/telas/Agendar";
import { PainelAgenda } from "@/telas/painel/PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const sempreEsperando = async () => {
  await delay("infinite");
  return HttpResponse.json({});
};

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

function semCarregando() {
  expect(document.body.textContent).not.toMatch(/Carregando/);
}

describe("skeletons (Fatia 14D, M4)", () => {
  test("A57: serviços, horários e a agenda do painel em andamento mostram skeleton com aria-busy=true e texto acessível curto; nenhum Carregando…", async () => {
    // serviços
    definirBusca("");
    servidor.use(http.get(`${BFF}/servicos`, sempreEsperando), http.get(`${BFF}/barbeiros`, sempreEsperando));
    const servicos = renderizar(<Agendar />);
    const esqueleto = (await screen.findAllByRole("status")).find((e) => e.getAttribute("aria-busy") === "true")!;
    expect(esqueleto).toHaveTextContent("Só um instante, já estamos trazendo.");
    expect(esqueleto.querySelectorAll(".esqueleto-bloco").length).toBeGreaterThan(0);
    semCarregando();
    servicos.unmount();

    // horários
    definirBusca(`servicoId=${CINCO_SERVICOS[1].id}&data=2026-11-24`);
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
      http.get(`${BFF}/disponibilidade`, sempreEsperando),
    );
    const horarios = renderizar(<Agendar />);
    const passo = (await screen.findByRole("heading", { name: /Horário/ })).closest("section")!;
    await vi.waitFor(() => expect(passo.querySelector("[aria-busy='true']")).not.toBeNull());
    semCarregando();
    horarios.unmount();

    // agenda do painel
    definirBusca("data=2026-11-24");
    servidor.use(
      http.get(`${BFF}/admin/agenda/dia`, sempreEsperando),
      http.get(`${BFF}/admin/agenda/mes`, sempreEsperando),
      http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([])),
    );
    renderizar(<PainelAgenda />, ADMIN);
    await vi.waitFor(() => expect(document.querySelector(".esqueleto[aria-busy='true']")).not.toBeNull());
    semCarregando();
  });
});
