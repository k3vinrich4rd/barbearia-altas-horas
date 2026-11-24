import { cleanup, screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { CINCO_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const ALCANCE = {
  atualizadoEm: "2026-11-23T08:00:00-03:00",
  totalCidades: 2,
  totalBairros: 3,
  regioes: [
    {
      cidade: "Osasco",
      uf: "SP",
      codigoIbge: "3534401",
      comFaixas: true,
      bairros: ["Centro", "Presidente Altino", "Vila Yara"],
      bairrosComFaixa: [
        { bairro: "Centro", faixaPercentual: "65", outros: false },
        { bairro: "Presidente Altino", faixaPercentual: "20", outros: false },
        { bairro: "Vila Yara", faixaPercentual: "<5", outros: false },
        { bairro: "Outros bairros", faixaPercentual: null, outros: true },
      ],
    },
    {
      cidade: "Carapicuíba",
      uf: "SP",
      codigoIbge: "3510609",
      comFaixas: false,
      bairros: ["Centro"],
      bairrosComFaixa: [{ bairro: "Centro", faixaPercentual: null, outros: false }],
    },
  ],
};

describe("alcance na home", () => {
  test("A50: lista os municípios sem número; ao clicar mostra os bairros com \"cerca de 15%\", \"menos de 5%\" ou só o nome (município com menos de 20); nenhum número de pessoas", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/alcance`, () => HttpResponse.json(ALCANCE)),
    );
    const { user } = renderizar(<Home />);
    const bloco = (await screen.findByRole("heading", { name: "De onde vêm nossos clientes" })).closest("section")!;
    expect(within(bloco).getByRole("button", { name: "Osasco - SP" })).toHaveAttribute("aria-expanded", "false");
    expect(bloco.textContent).not.toMatch(/\d/);

    await user.click(within(bloco).getByRole("button", { name: "Osasco - SP" }));
    const osasco = within(bloco).getByRole("list", { name: "Bairros de Osasco" });
    const itens = within(osasco).getAllByRole("listitem");
    expect(itens[0]).toHaveTextContent("Centro · cerca de 65%");
    expect(itens[1]).toHaveTextContent("Presidente Altino · cerca de 20%");
    expect(itens[2]).toHaveTextContent("Vila Yara · menos de 5%");
    expect(itens[3].textContent).toBe("Outros bairros"); // sem faixa
    // só as faixas têm dígitos; pessoas nunca aparecem: nenhum "clientes", "pessoas" ou número solto
    expect(osasco.textContent).not.toMatch(/pessoas|\d+ clientes|atendimentos/i);

    // outro município: o primeiro fecha, e este (menos de 20) mostra só os nomes
    await user.click(within(bloco).getByRole("button", { name: "Carapicuíba - SP" }));
    expect(within(bloco).queryByRole("list", { name: "Bairros de Osasco" })).toBeNull();
    const carapicuiba = within(bloco).getByRole("list", { name: "Bairros de Carapicuíba" });
    expect(carapicuiba.textContent).toBe("Centro");
  });

  test("A50: vitrine vazia, 429 ou rede fora: o bloco some e a home segue sem erro de página inteira", async () => {
    const respostas = {
      vazio: () => HttpResponse.json({ ...ALCANCE, totalCidades: 0, totalBairros: 0, regioes: [] }),
      limite: () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "30" }),
      rede: () => HttpResponse.error(),
    };
    for (const [nome, resposta] of Object.entries(respostas)) {
      servidor.use(
        http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
        http.get(`${BFF}/alcance`, resposta),
      );
      renderizar(<Home />);
      expect(await screen.findByText("Corte Clássico / Social"), nome).toBeInTheDocument();
      await new Promise((r) => setTimeout(r, 50));
      expect(screen.queryByRole("heading", { name: "De onde vêm nossos clientes" }), nome).toBeNull();
      expect(screen.queryByRole("alert"), nome).toBeNull();
      cleanup();
    }
  });
});
