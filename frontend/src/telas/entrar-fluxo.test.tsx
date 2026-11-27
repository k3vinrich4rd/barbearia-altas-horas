import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { definirBusca } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { Entrar } from "./Entrar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);

// Fatia 16, F2: /entrar sem "Criar conta" repetido e com o atalho para quem só quer marcar.
describe("/entrar no fluxo sem repetição", () => {
  test('A16: "Criar conta" aparece só nas abas e há "Só quer marcar? Agende sem cadastro" levando ao agendamento', () => {
    definirBusca("");
    renderizar(<Entrar />);
    const criar = screen.getAllByRole("link", { name: "Criar conta" });
    expect(criar).toHaveLength(1);
    const abas = screen.getByRole("navigation", { name: "Entrar ou criar conta" });
    expect(within(abas).getByRole("link", { name: "Criar conta" })).toBe(criar[0]);
    expect(screen.getAllByText(/Criar conta/)).toHaveLength(1);

    const atalho = screen.getByRole("link", { name: "Agende sem cadastro" });
    expect(atalho).toHaveAttribute("href", "/agendar");
    expect(atalho).toHaveClass("link-solto");
    expect(atalho.closest("p")).toHaveTextContent("Só quer marcar? Agende sem cadastro");
  });
});
