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

describe("home: de onde vêm nossos clientes (Fatia 16: só com dado)", () => {
  test("A8: regiões vazias, 429 ou rede fora: a seção de alcance NÃO aparece (nem 'Em breve', nem número, nem botão); com dados, mostra as faixas", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      const { container } = renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await new Promise((r) => setTimeout(r, 50));
      expect(screen.queryByRole("heading", { name: "De onde vêm nossos clientes" }), nome).toBeNull();
      expect(container.querySelector("#alcance"), nome).toBeNull();
      expect(screen.queryByText(/Em breve/), nome).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      cleanup();
    }

    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
      http.get(`${BFF}/alcance`, () =>
        HttpResponse.json({
          atualizadoEm: "2026-11-23T00:00:00Z",
          totalCidades: 1,
          totalBairros: 1,
          regioes: [
            {
              cidade: "Osasco",
              uf: "SP",
              codigoIbge: "3534401",
              comFaixas: false,
              bairros: ["Centro"],
              bairrosComFaixa: [{ bairro: "Centro", faixaPercentual: null, outros: false }],
            },
          ],
        }),
      ),
    );
    const { user } = renderizar(<Home />);
    const comDados = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;
    await user.click(await within(comDados).findByRole("button", { name: "Osasco - SP" })); // o município abre os bairros (Fatia 11)
    expect(comDados).toHaveTextContent("Centro");
    expect(comDados).not.toHaveTextContent("Em breve");
    expect(comDados.textContent).not.toMatch(/\d/);
  });

  test("A17: regiões vazias, 429 ou rede fora: sem número, sem erro de página inteira, e a história e os serviços continuam (desde a Fatia 16 a seção some, A8)", async () => {
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await new Promise((r) => setTimeout(r, 50));
      expect(screen.queryByText(/Em breve/), nome).toBeNull();
      expect(screen.queryByText(/0 cidades/), nome).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      expect(screen.getByRole("heading", { level: 1 }), nome).toBeInTheDocument();
      expect(screen.getAllByText(/Jardim Bonança, em Osasco/).length).toBeGreaterThan(0);
      cleanup();
    }
  });
});
