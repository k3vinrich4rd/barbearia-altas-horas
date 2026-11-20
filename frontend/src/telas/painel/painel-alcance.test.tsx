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
      bairros: [{ bairro: "Centro", atendimentos: 6, percentual: 66.7 }],
    },
    {
      cidade: "São Paulo",
      uf: "SP",
      atendimentos: 2,
      percentual: 22.2,
      bairros: [{ bairro: "Sé", atendimentos: 1, percentual: 11.1 }],
    },
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
  test("A53: sem período a chamada não manda de nem ate; todo grupo com o número exato e o percentual; sem região informada; de > ate e mais de 366 dias são barrados antes da chamada; ADMIN filtra por barbeiro e BARBEIRO não vê o filtro; tabela acessível", async () => {
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
    ).toEqual([
      "Cidade e bairro",
      "Atendimentos",
      "Parcela",
      "Barra da parcela (só desenho; o número está na coluna Parcela)",
    ]);
    expect(within(tabela).getByRole("rowheader", { name: "Osasco, SP" })).toHaveAttribute("scope", "row");

    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[0]).toHaveTextContent("Osasco, SP");
    expect(linhas[0]).toHaveTextContent("6");
    expect(linhas[0]).toHaveTextContent("66,7%");
    expect(linhas[1]).toHaveTextContent("Centro");
    // sem limite mínimo (D149): o bairro de 1 atendimento aparece com o número exato e o percentual
    expect(linhas[2]).toHaveTextContent("São Paulo, SP");
    expect(linhas[2]).toHaveTextContent("22,2%");
    expect(linhas[3]).toHaveTextContent("Sé");
    expect(linhas[3]).toHaveTextContent("1");
    expect(linhas[3]).toHaveTextContent("11,1%");
    expect(tabela.textContent).not.toMatch(/menos de 5|Outras cidades|Outros bairros|NaN|null/);
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

  test("A9: cada linha tem uma barra com o mesmo percentual da coluna Parcela, só tokens e sem número a mais; as barras ficam fora da árvore de acessibilidade e a tabela continua completa para o leitor de tela", async () => {
    api([]);
    renderizar(<PainelAlcance />, ADMIN);
    const tabela = await screen.findByRole("table", { name: /9 atendimentos/ });
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas).toHaveLength(4);

    const valores = linhas.map((l) => {
      const barra = l.querySelector("progress") as HTMLProgressElement;
      expect(barra, l.textContent ?? "").not.toBeNull();
      expect(barra.max).toBe(100);
      // o texto da barra é o da própria tabela: nenhum número que a coluna Parcela não tenha
      expect(barra.closest("td")).toHaveAttribute("aria-hidden", "true");
      expect(barra.textContent).toBe("");
      expect(barra).not.toHaveAttribute("style");
      expect(l).toHaveTextContent(`${String(barra.value).replace(".", ",")}%`);
      return barra.value;
    });
    expect(valores).toEqual([66.7, 66.7, 22.2, 11.1]);
    // fora da árvore de acessibilidade: nenhum "progressbar" e nenhum meter
    expect(screen.queryAllByRole("progressbar")).toHaveLength(0);
    expect(document.querySelectorAll("[style]")).toHaveLength(0);
  });

  test("A10: tabela acessível a leitor de tela: caption, cabeçalhos de coluna com escopo, cidade e bairro como cabeçalho de linha (o bairro leva a cidade em texto só para leitor), região rolável focável e sem barra no cabeçalho visível", async () => {
    api([]);
    renderizar(<PainelAlcance />, ADMIN);
    const tabela = await screen.findByRole("table", { name: /9 atendimentos · de 20\/10\/2026 a 18\/11\/2026/ });
    for (const c of within(tabela).getAllByRole("columnheader")) expect(c).toHaveAttribute("scope", "col");
    const regiao = screen.getByRole("region", { name: "Tabela do alcance por região" });
    expect(regiao).toHaveAttribute("tabindex", "0");
    expect(within(tabela).getByRole("rowheader", { name: /São Paulo:s*Sé/ })).toHaveAttribute("scope", "row");
    expect(within(tabela).getByRole("rowheader", { name: /Osasco:s*Centro/ })).toBeInTheDocument();
    const cabecalhoDaBarra = within(tabela).getAllByRole("columnheader")[3];
    expect(cabecalhoDaBarra.querySelector(".so-leitor")).not.toBeNull();
    expect(cabecalhoDaBarra.textContent).toMatch(/Barra da parcela/);
    expect(screen.getByText(/Sem região informada: 1/)).toBeInTheDocument();
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
