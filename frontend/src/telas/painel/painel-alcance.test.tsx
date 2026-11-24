import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { renderizar } from "@/testes/render";
import { PainelAlcance } from "./PainelAlcance";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const PERIODO = { de: "2026-10-25", ate: "2026-11-23" };

// O A53 da Fatia 7 (tabela acessível, "menos de 5" sem percentual, sem região, vazio) continua valendo; a tela agora conta
// clientes e tem o seletor de nível (Fatia 11: ver PainelAlcance.test.tsx, A51 a A54).
describe("alcance por região (Fatia 7, A53, atualizado na Fatia 11)", () => {
  test("A53: tabela acessível com caption e th scope, grupo abaixo de 5 mostra menos de 5 e nenhum percentual, e sem clientes diz que ainda não há o suficiente", async () => {
    servidor.use(
      http.get(`${BFF}/admin/alcance`, () =>
        HttpResponse.json({
          periodo: PERIODO,
          nivel: "MUNICIPIO",
          k: 5,
          totalVisivel: true,
          clientes: 9,
          semRegiao: { clientes: 1, abaixoDoLimite: false },
          grupos: [
            {
              tipo: "MUNICIPIO",
              codigo: "3534401",
              nome: "Osasco",
              uf: "SP",
              clientes: 6,
              percentual: 66.7,
              abaixoDoLimite: false,
              agrupadoEm: null,
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
        }),
      ),
    );
    const v = renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    const tabela = await screen.findByRole("table", { name: /9 clientes por município/ });
    expect(within(tabela).getByRole("rowheader", { name: "Osasco, SP" })).toHaveAttribute("scope", "row");
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[1]).toHaveTextContent("menos de 5");
    expect(linhas[1].textContent).not.toMatch(/%|NaN|null|\b0\b/);
    expect(screen.getByText(/Sem região informada: 1/)).toBeInTheDocument();
    v.unmount();

    servidor.use(
      http.get(`${BFF}/admin/alcance`, () =>
        HttpResponse.json({
          periodo: PERIODO,
          nivel: "MUNICIPIO",
          k: 5,
          totalVisivel: true,
          clientes: 0,
          semRegiao: { clientes: 0, abaixoDoLimite: false },
          grupos: [],
        }),
      ),
    );
    renderizar(<PainelAlcance />, SESSAO_BARBEIRO);
    expect(await screen.findByText("Ainda não há clientes suficientes para mostrar o alcance.")).toBeInTheDocument();
  });
});
