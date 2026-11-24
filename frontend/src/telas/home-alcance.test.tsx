import { cleanup, screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { SEIS_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const respostas = {
  vazio: () =>
    HttpResponse.json({ atualizadoEm: "2026-11-18T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }),
  limite: () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "30" }),
  rede: () => HttpResponse.error(),
};

describe("home: de onde vêm nossos clientes (Fatia 12, E10)", () => {
  test("A15: regiões vazias, 429 ou rede fora: o bloco APARECE com o estado vazio 'Em breve', sem número e sem botão; com dados, mostra as faixas", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      const bloco = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;
      expect(await within(bloco).findByText(/Em breve/), nome).toBeInTheDocument();
      expect(bloco, nome).toHaveTextContent("ninguém ser identificado");
      expect(bloco.textContent, nome).not.toMatch(/\d/); // sem número, sem "0"
      expect(within(bloco).queryByRole("button"), nome).toBeNull();
      expect(within(bloco).queryByRole("link"), nome).toBeNull();
      expect(within(bloco).queryByRole("list"), nome).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      expect(screen.getAllByText(/Jardim Bonança, em Osasco/).length).toBeGreaterThan(0);
      cleanup();
    }

    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
      http.get(`${BFF}/alcance`, () =>
        HttpResponse.json({
          atualizadoEm: "2026-11-23T00:00:00Z",
          totalCidades: 1,
          totalBairros: 1,
          regioes: [{ cidade: "Osasco", uf: "SP", bairros: ["Centro"] }],
        }),
      ),
    );
    renderizar(<Home />);
    const comDados = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;
    expect(await within(comDados).findByText("Osasco - SP")).toBeInTheDocument();
    expect(comDados).toHaveTextContent("Centro");
    expect(comDados).not.toHaveTextContent("Em breve");
    expect(comDados.textContent).not.toMatch(/\d/);
  });

  test("A17: regiões vazias, 429 ou rede fora: sem número, sem erro de página inteira, e a história e os serviços continuam (desde a Fatia 12 o bloco aparece com o estado vazio, A15)", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await screen.findByText(/Em breve/);
      expect(screen.queryByText(/0 cidades/), nome).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      expect(screen.getByRole("heading", { level: 1 }), nome).toBeInTheDocument();
      expect(screen.getAllByText(/Jardim Bonança, em Osasco/).length).toBeGreaterThan(0);
      cleanup();
    }
  });
});
