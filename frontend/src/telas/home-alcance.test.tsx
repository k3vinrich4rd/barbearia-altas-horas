import { cleanup, screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { CINCO_SERVICOS } from "@/testes/dados";
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

describe("home sem alcance", () => {
  test("A17: regiões vazias, 429 ou rede fora: o bloco some e a home continua sem erro de página inteira", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await new Promise((r) => setTimeout(r, 50));
      expect(screen.queryByRole("heading", { name: "De onde vêm nossos clientes" }), nome).toBeNull();
      expect(screen.queryByText(/0 cidades/)).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      expect(screen.getAllByText(/Jardim Bonança, em Osasco/).length).toBeGreaterThan(0);
      cleanup();
    }
  });
});

describe("home com alcance (7C)", () => {
  test("A13: cada cidade com a quantidade em destaque e os bairros com o número, do maior para o menor como a API manda, até o de 1 atendimento, sem percentual e sem nada de pessoa", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/alcance`, () =>
        HttpResponse.json({
          atualizadoEm: "2026-11-18T00:00:00Z",
          totalCidades: 2,
          totalBairros: 3,
          regioes: [
            {
              cidade: "Osasco",
              uf: "SP",
              atendimentos: 42,
              bairros: [
                { bairro: "Jardim Bonança", atendimentos: 31 },
                { bairro: "Km 18", atendimentos: 10 },
                { bairro: "Vila Yolanda", atendimentos: 1 },
              ],
            },
            { cidade: "Carapicuíba", uf: "SP", atendimentos: 1, bairros: [] },
          ],
        }),
      ),
    );
    renderizar(<Home />);
    const bloco = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;

    const cidades = bloco.querySelectorAll(".regiao-quantidade");
    expect([...cidades].map((c) => c.textContent)).toEqual(["42 atendimentos", "1 atendimento"]);
    expect(bloco).toHaveTextContent("Osasco · 42 atendimentos");
    expect(bloco).toHaveTextContent("Carapicuíba · 1 atendimento");
    const bairros = within(within(bloco).getByRole("list", { name: "Bairros de Osasco" })).getAllByRole("listitem");
    expect(bairros.map((b) => b.textContent?.replace(/\s+/g, " ").trim())).toEqual([
      "Jardim Bonança31 atendimentos",
      "Km 1810 atendimentos",
      "Vila Yolanda1 atendimento",
    ]);
    expect(within(bloco).queryByRole("list", { name: "Bairros de Carapicuíba" })).toBeNull();
    expect(bloco.textContent).not.toMatch(/%|menos de 5|Outros bairros|@/);
  });

  test("A14: sem regiões (lista vazia) o bloco inteiro some, sem título e sem aviso", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/alcance`, respostas.vazio),
    );
    renderizar(<Home />);
    expect(await screen.findByText("Corte Clássico / Social")).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole("heading", { name: "De onde vêm nossos clientes" })).toBeNull();
    expect(screen.queryByText(/Clientes da região/)).toBeNull();
  });
});
