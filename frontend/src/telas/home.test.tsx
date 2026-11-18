import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

describe("home", () => {
  test("A16: um h1, a história, os serviços com nome, duração e preço da API, as regiões sem número e um link principal", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/alcance`, () =>
        HttpResponse.json({ atualizadoEm: "2026-11-18T00:00:00Z", totalCidades: 1, totalBairros: 1, regioes: [{ cidade: "Osasco", uf: "SP", bairros: ["Centro"] }] }),
      ),
    );
    renderizar(<Home />);

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Desde 1997");
    expect(screen.getByText(/Jardim Bonança, em Osasco, desde 1997/)).toBeInTheDocument();

    const servicos = await screen.findByRole("heading", { name: "Serviços" });
    const lista = within(servicos.closest("section")!).getAllByRole("listitem");
    expect(lista).toHaveLength(5);
    expect(lista[1]).toHaveTextContent("Corte Degradê / Fade / Navalhado");
    expect(lista[1]).toHaveTextContent("60 min");
    expect(lista[1]).toHaveTextContent("R$ 55,00");

    const bloco = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;
    expect(bloco).toHaveTextContent("Osasco - SP");
    expect(bloco).toHaveTextContent("Centro");
    expect(bloco.textContent).not.toMatch(/\d/);

    const principais = screen.getAllByRole("link", { name: "Agendar horário" });
    expect(principais).toHaveLength(1);
    expect(principais[0]).toHaveAttribute("href", "/agendar");
  });
});
