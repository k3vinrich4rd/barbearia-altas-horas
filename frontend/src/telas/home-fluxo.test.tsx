import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { Rodape } from "@/componentes/Rodape";
import { BARBEIRO, SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// Fatia 16 (fluxo sem repetição): cada informação num lugar só e nenhuma seção sem finalidade.
function usarApi(alcance = { atualizadoEm: "2026-11-27T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }) {
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido" }])),
    http.get(`${BFF}/alcance`, () => HttpResponse.json(alcance)),
  );
}

async function renderizarHome() {
  const r = renderizar(
    <>
      <Home />
      <Rodape />
    </>,
  );
  await screen.findByText("Descoloração / Platinado (química)");
  await new Promise((fim) => setTimeout(fim, 50));
  return r;
}

describe("home: fluxo sem repetição (Fatia 16)", () => {
  test("A8: sem dado de alcance, a seção 'De onde vêm nossos clientes' não existe (nem o 'Em breve')", async () => {
    usarApi();
    const { container } = await renderizarHome();
    expect(container.querySelector("#alcance")).toBeNull();
    expect(screen.queryByText(/Em breve/)).toBeNull();
  });

  test("A21: nenhum total de clientes na home (nem número de pessoas, nem de atendimentos)", async () => {
    usarApi();
    const { container } = await renderizarHome();
    expect(container.textContent).not.toMatch(/\d[\d.]*\s*(clientes|pessoas|atendimentos|cortes feitos)/i);
    expect(container.textContent).not.toMatch(/(clientes|pessoas) atendid/i);
  });
});
