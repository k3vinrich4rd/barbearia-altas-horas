import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";
import { Privacidade } from "./Privacidade";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

describe("política de privacidade", () => {
  test("A42: renderiza título e lista em ordem, mostra versão e data no fuso, remove script e onerror; falha mostra tentar de novo", async () => {
    servidor.use(
      http.get(`${BFF}/politica-privacidade`, () =>
        HttpResponse.json({
          versao: "1.0",
          textoConsentimento: "x",
          atualizadaEm: "2026-11-10T01:00:00Z", // 09/11 22:00 em São Paulo
          politica: "# Política de dados\n\n## Seus direitos\n\n- exportar\n- excluir\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(2)>\n\n[link](javascript:alert(3))",
        }),
      ),
    );
    const { container, unmount } = renderizar(<Privacidade />);
    expect(await screen.findByRole("heading", { level: 2, name: "Política de dados" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Política de privacidade");
    expect(screen.getByRole("heading", { level: 3, name: "Seus direitos" })).toBeInTheDocument();
    expect(within(container.querySelector("article")!).getAllByRole("listitem").map((i) => i.textContent)).toEqual(["exportar", "excluir"]);
    expect(screen.getByRole("link", { name: "Seus direitos" })).toHaveAttribute("href", "#seus-direitos");
    expect(screen.getByText(/Versão 1\.0 · atualizada em 09\/11\/2026/)).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(container.innerHTML).not.toMatch(/onerror|javascript:/i);
    unmount();

    servidor.use(http.get(`${BFF}/politica-privacidade`, () => erro(500, "ERRO_INTERNO")));
    renderizar(<Privacidade />);
    expect(await screen.findByText(/Algo deu errado do nosso lado/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });
});
