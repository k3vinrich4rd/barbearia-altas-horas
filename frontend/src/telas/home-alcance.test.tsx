import { cleanup, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { CINCO_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const respostas = {
  vazio: () => HttpResponse.json({ atualizadoEm: "2026-11-18T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }),
  limite: () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "30" }),
  rede: () => HttpResponse.error(),
};

describe("home sem alcance", () => {
  test("A17: regiões vazias, 429 ou rede fora: o bloco some e a home continua sem erro de página inteira", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)), http.get(`${BFF}/alcance`, resposta));
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await new Promise((r) => setTimeout(r, 50));
      expect(screen.queryByRole("heading", { name: "De onde vêm nossos clientes" }), nome).toBeNull();
      expect(screen.queryByText(/0 cidades/)).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      expect(screen.getByText(/Jardim Bonança, em Osasco/)).toBeInTheDocument();
      cleanup();
    }
  });
});
