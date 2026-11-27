import { screen, waitFor } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { Rodape } from "@/componentes/Rodape";
import { BARBEIRO, SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

describe("home: ordem das seções (Fatia 12, atualizada pela Fatia 16, F3)", () => {
  test("A1: herói, serviços, como marcar (com as dúvidas dentro), barbeiro, galeria, trajetória, alcance e onde estamos; sem #como-funciona nem #premios", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido" }])),
      http.get(`${BFF}/alcance`, () =>
        HttpResponse.json({ atualizadoEm: "2026-11-23T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }),
      ),
    );
    const { container } = renderizar(
      <>
        <Home />
        <Rodape />
      </>,
    );
    await screen.findByText("Descoloração / Platinado (química)");

    const ordem = ["inicio", "servicos", "como-marcar", "barbeiro", "galeria", "trajetoria", "alcance", "onde-estamos"];
    const elementos = ordem.map((id) => container.querySelector(`#${id}`));
    ordem.forEach((id, i) => expect(elementos[i], `#${id} existe`).not.toBeNull());
    for (let i = 1; i < elementos.length; i++) {
      const antes = elementos[i - 1]!;
      const depois = elementos[i]!;
      expect(
        antes.compareDocumentPosition(depois) & Node.DOCUMENT_POSITION_FOLLOWING,
        `#${ordem[i - 1]} vem antes de #${ordem[i]}`,
      ).toBeTruthy();
    }

    expect(container.querySelector("#como-funciona")).toBeNull();
    expect(container.querySelector("#premios")).toBeNull();
    // Fatia 16, F3: a faixa "Como funciona" saiu; as dúvidas que ficaram moram dentro de "Como marcar"
    expect(screen.queryByRole("list", { name: "Como funciona" })).toBeNull();
    expect(elementos[2]!.contains(container.querySelector("#duvidas"))).toBe(true);
    await waitFor(() => expect(screen.getByRole("link", { name: /Agendar com Eduardo/ })).toBeInTheDocument());
  });
});
