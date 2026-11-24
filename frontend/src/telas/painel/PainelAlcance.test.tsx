import { screen, waitFor, within } from "@testing-library/react";
import axe from "axe-core";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, renderizar } from "@/testes/render";
import { PainelAlcance } from "./PainelAlcance";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const ALCANCE = {
  periodo: { de: "2026-10-25", ate: "2026-11-23" },
  nivel: "MUNICIPIO",
  k: 5,
  totalVisivel: true,
  clientes: 27,
  semRegiao: { clientes: 0, abaixoDoLimite: false },
  grupos: [
    {
      tipo: "MUNICIPIO",
      codigo: "3534401",
      nome: "Osasco",
      uf: "SP",
      clientes: 18,
      percentual: 66.7,
      abaixoDoLimite: false,
      agrupadoEm: null,
      filhos: [
        { tipo: "BAIRRO", codigo: "3534401", nome: "Centro", clientes: 12, percentual: 44.4, abaixoDoLimite: false },
        { tipo: "OUTROS", codigo: "3534401", nome: "Outros bairros", clientes: 6, percentual: 22.2, abaixoDoLimite: false },
      ],
    },
    {
      tipo: "OUTROS",
      codigo: "350001",
      nome: "Outros municípios da região imediata de São Paulo",
      uf: "SP",
      clientes: 9,
      percentual: 33.3,
      abaixoDoLimite: false,
      agrupadoEm: "REGIAO",
      filhos: [],
    },
    {
      tipo: "OUTROS",
      codigo: null,
      nome: "Outros estados",
      uf: null,
      clientes: null,
      percentual: null,
      abaixoDoLimite: true,
      agrupadoEm: "PAIS",
      filhos: [],
    },
  ],
  total: 27,
  cidades: [],
};

function api(consultas: URLSearchParams[], corpo: unknown = ALCANCE) {
  servidor.use(
    http.get(`${BFF}/admin/alcance`, ({ request }) => {
      consultas.push(new URL(request.url).searchParams);
      return HttpResponse.json(corpo as object);
    }),
  );
}

describe("painel de alcance", () => {
  test("A51: o Dono/Admin vê o seletor de nível, o filtro de barbeiro, de UF e de período; a tabela tem caption, linhas e filhos, e \"menos de 5\" em texto, sem barra", async () => {
    const consultas: URLSearchParams[] = [];
    api(consultas);
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])));
    const { user } = renderizar(<PainelAlcance />, ADMIN);

    const tabela = await screen.findByRole("table", { name: /27 clientes por município · de 25\/10\/2026 a 23\/11\/2026/ });
    expect(consultas[0].get("nivel")).toBe("MUNICIPIO");
    expect(consultas[0].get("periodo")).toBe("ULTIMOS_30_DIAS");
    expect(consultas[0].has("uf")).toBe(false);
    expect(consultas[0].has("barbeiroId")).toBe(false);
    expect(
      within(tabela)
        .getAllByRole("columnheader")
        .map((c) => c.textContent),
    ).toEqual(["Município e bairro", "Clientes", "Parcela"]);
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[0]).toHaveTextContent("Osasco, SP");
    expect(linhas[0]).toHaveTextContent("18");
    expect(linhas[0]).toHaveTextContent("66,7%");
    expect(linhas[1]).toHaveTextContent("Centro");
    expect(linhas[1]).toHaveTextContent("12");
    expect(linhas[3]).toHaveTextContent("somados na região");
    // "Outros estados": menos de 5 em texto, sem percentual, sem número, sem barra
    expect(linhas[4]).toHaveTextContent("Outros estados");
    expect(linhas[4]).toHaveTextContent("menos de 5");
    expect(linhas[4].textContent).not.toMatch(/%|NaN|null|\b0\b/);
    expect(within(tabela).queryByRole("progressbar")).toBeNull();
    expect(tabela.querySelector("meter, progress, [role='img']")).toBeNull();

    // o seletor de nível, a UF, o período e o barbeiro mudam a chamada
    await user.selectOptions(screen.getByLabelText("Nível"), "REGIAO");
    await waitFor(() => expect(consultas.at(-1)!.get("nivel")).toBe("REGIAO"));
    await user.selectOptions(screen.getByLabelText("Estado (UF)"), "SP");
    await waitFor(() => expect(consultas.at(-1)!.get("uf")).toBe("SP"));
    await user.selectOptions(screen.getByLabelText("Período"), "ULTIMOS_90_DIAS");
    await waitFor(() => expect(consultas.at(-1)!.get("periodo")).toBe("ULTIMOS_90_DIAS"));
    await user.selectOptions(screen.getByLabelText("Barbeiro"), BARBEIRO.id);
    await waitFor(() => expect(consultas.at(-1)!.get("barbeiroId")).toBe(BARBEIRO.id));
    expect(consultas.at(-1)!.get("nivel")).toBe("REGIAO");
    expect(await screen.findByRole("table", { name: /por região/ })).toBeInTheDocument();
  });

  test("A52: o barbeiro convidado vê o mesmo seletor e período, SEM filtro de barbeiro e com o aviso \"Você vê só os seus clientes\"; a chamada nunca leva barbeiroId e a tela nunca mostra bairro com menos de k", async () => {
    const consultas: URLSearchParams[] = [];
    api(consultas, {
      ...ALCANCE,
      totalVisivel: true,
      clientes: 6,
      grupos: [
        {
          tipo: "MUNICIPIO",
          codigo: "3534401",
          nome: "Osasco",
          uf: "SP",
          clientes: 6,
          percentual: 100,
          abaixoDoLimite: false,
          agrupadoEm: null,
          filhos: [{ tipo: "OUTROS", codigo: "3534401", nome: "Outros bairros", clientes: 6, percentual: 100, abaixoDoLimite: false }],
        },
      ],
    });
    const { user } = renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    const tabela = await screen.findByRole("table");
    expect(screen.getByText("Você vê só os seus clientes.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Barbeiro")).toBeNull();
    expect(screen.getByLabelText("Nível")).toBeInTheDocument();
    expect(screen.getByLabelText("Período")).toBeInTheDocument();
    expect(within(tabela).queryByText("Centro")).toBeNull();
    expect(consultas[0].has("barbeiroId")).toBe(false);
    await user.selectOptions(screen.getByLabelText("Nível"), "ESTADO");
    await waitFor(() => expect(consultas.at(-1)!.get("nivel")).toBe("ESTADO"));
    expect(consultas.every((c) => !c.has("barbeiroId"))).toBe(true);
  });

  test("A53: com totalVisivel = false o total e as porcentagens somem e a tela explica em uma frase; sem clientes diz que ainda não há o suficiente", async () => {
    api([], {
      ...ALCANCE,
      totalVisivel: false,
      clientes: null,
      semRegiao: { clientes: null, abaixoDoLimite: true },
      grupos: [{ ...ALCANCE.grupos[0], percentual: null, filhos: ALCANCE.grupos[0].filhos.map((f) => ({ ...f, percentual: null })) }],
    });
    const v = renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    const tabela = await screen.findByRole("table", { name: /^Clientes por município/ });
    expect(screen.getByText(/A soma ficou escondida para proteger os clientes/)).toBeInTheDocument();
    expect(tabela.textContent).not.toMatch(/\d+,\d%|\d clientes/);
    expect(screen.getByText("Sem região informada: menos de 5")).toBeInTheDocument();
    v.unmount();

    api([], { ...ALCANCE, clientes: 0, grupos: [], semRegiao: { clientes: 0, abaixoDoLimite: false } });
    renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    expect(await screen.findByText("Ainda não há clientes suficientes para mostrar o alcance.")).toBeInTheDocument();
  });

  test("A54: rótulos em todos os campos, tabela sem violação do axe e o erro da API como alerta", async () => {
    api([]);
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])));
    const v = renderizar(<PainelAlcance />, ADMIN);
    await screen.findByRole("table");
    for (const rotulo of ["Nível", "Estado (UF)", "Período", "Barbeiro"]) expect(screen.getByLabelText(rotulo)).toBeInTheDocument();
    const resultado = await axe.run(v.container, { rules: { "color-contrast": { enabled: false } } });
    expect(resultado.violations.map((x) => `${x.id}: ${x.help}`)).toEqual([]);
    v.unmount();

    servidor.use(http.get(`${BFF}/admin/alcance`, () => erro(500, "ERRO_INTERNO")));
    renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
