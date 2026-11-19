import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, renderizar } from "@/testes/render";
import { PainelAlcance } from "./PainelAlcance";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const ALCANCE = {
  periodo: { de: "2026-10-20", ate: "2026-11-18" },
  total: 9,
  semRegiao: 1,
  cidades: [
    {
      cidade: "Osasco",
      uf: "SP",
      atendimentos: 6,
      percentual: 66.7,
      abaixoDoLimite: false,
      bairros: [{ bairro: "Centro", atendimentos: 6, percentual: 66.7, abaixoDoLimite: false }],
    },
    { cidade: "Outras cidades", uf: null, atendimentos: null, percentual: null, abaixoDoLimite: true, bairros: [] },
  ],
};

function api(consultas: URLSearchParams[]) {
  servidor.use(
    http.get(`${BFF}/admin/alcance`, ({ request }) => {
      consultas.push(new URL(request.url).searchParams);
      return HttpResponse.json(ALCANCE);
    }),
  );
}

describe("alcance por região", () => {
  test("A53: sem período a chamada não manda de nem ate; menos de 5 sem percentual; sem região informada; de > ate e mais de 366 dias são barrados antes da chamada; ADMIN filtra por barbeiro e BARBEIRO não vê o filtro; tabela acessível", async () => {
    const consultas: URLSearchParams[] = [];
    api(consultas);
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])));
    const { user, unmount } = renderizar(<PainelAlcance />, ADMIN);

    const tabela = await screen.findByRole("table", { name: /9 atendimentos · de 20\/10\/2026 a 18\/11\/2026/ });
    expect(consultas[0].has("de")).toBe(false);
    expect(consultas[0].has("ate")).toBe(false);
    expect(consultas[0].has("barbeiroId")).toBe(false);
    // tabela acessível: caption, th scope=col e th scope=row
    expect(
      within(tabela)
        .getAllByRole("columnheader")
        .map((c) => c.textContent),
    ).toEqual(["Cidade e bairro", "Atendimentos", "Parcela"]);
    expect(within(tabela).getByRole("rowheader", { name: "Osasco, SP" })).toHaveAttribute("scope", "row");

    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[0]).toHaveTextContent("Osasco, SP");
    expect(linhas[0]).toHaveTextContent("6");
    expect(linhas[0]).toHaveTextContent("66,7%");
    expect(linhas[1]).toHaveTextContent("Centro");
    // "Outras cidades": menos de 5 e nenhum percentual (nem 0, nem null, nem NaN)
    expect(linhas[2]).toHaveTextContent("Outras cidades");
    expect(linhas[2]).toHaveTextContent("menos de 5");
    expect(linhas[2].textContent).not.toMatch(/%|NaN|null|\b0\b/);
    expect(screen.getByText(/Sem região informada: 1/)).toBeInTheDocument();

    // período invertido e acima de 366 dias: barrados no formulário
    await user.type(screen.getByLabelText("De"), "2026-11-10");
    await user.type(screen.getByLabelText("Até"), "2026-11-01");
    await user.click(screen.getByRole("button", { name: "Aplicar" }));
    expect(screen.getByText("A data inicial deve ser antes da final.")).toBeInTheDocument();
    await user.clear(screen.getByLabelText("De"));
    await user.type(screen.getByLabelText("De"), "2025-10-01");
    await user.clear(screen.getByLabelText("Até"));
    await user.type(screen.getByLabelText("Até"), "2026-11-18");
    await user.click(screen.getByRole("button", { name: "Aplicar" }));
    expect(screen.getByText("O período pode ter no máximo 366 dias.")).toBeInTheDocument();
    expect(consultas).toHaveLength(1);

    // período válido e barbeiro: a chamada leva de, ate e barbeiroId
    await user.clear(screen.getByLabelText("De"));
    await user.type(screen.getByLabelText("De"), "2026-11-01");
    await user.selectOptions(screen.getByLabelText("Barbeiro"), BARBEIRO.id);
    await user.click(screen.getByRole("button", { name: "Aplicar" }));
    await vi.waitFor(() => expect(consultas).toHaveLength(2));
    expect(consultas[1].get("de")).toBe("2026-11-01");
    expect(consultas[1].get("ate")).toBe("2026-11-18");
    expect(consultas[1].get("barbeiroId")).toBe(BARBEIRO.id);
    unmount();

    // BARBEIRO: sem filtro de barbeiro e a chamada não leva barbeiroId
    const outras: URLSearchParams[] = [];
    api(outras);
    renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    await screen.findByRole("table");
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(outras[0].has("barbeiroId")).toBe(false);
  });

  test("A53: com total zero mostra que ainda não há atendimentos suficientes", async () => {
    servidor.use(
      http.get(`${BFF}/admin/alcance`, () =>
        HttpResponse.json({ periodo: ALCANCE.periodo, total: 0, semRegiao: 0, cidades: [] }),
      ),
    );
    renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    expect(
      await screen.findByText("Ainda não há atendimentos suficientes para mostrar o alcance."),
    ).toBeInTheDocument();
  });
});
