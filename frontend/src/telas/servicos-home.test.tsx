import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { ServicosDaHome } from "@/componentes/ListaDeServicos";
import { SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

describe("serviços da home (Fatia 12)", () => {
  test("A7: um cartão por serviço da API (6, com o novo), com nome, duração em minutos, preço em reais e o botão que leva ao agendamento", async () => {
    servidor.use(http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)));
    renderizar(<ServicosDaHome />);

    const bloco = (await screen.findByRole("heading", { name: "Um barbeiro, todos os serviços" })).closest("section")!;
    expect(bloco).toHaveAttribute("id", "servicos");
    const cartoes = await within(bloco).findAllByRole("listitem");
    expect(cartoes).toHaveLength(6);
    SEIS_SERVICOS.forEach((s, i) => {
      expect(cartoes[i]).toHaveTextContent(s.nome);
      expect(cartoes[i]).toHaveTextContent("60 min");
      expect(cartoes[i]).toHaveTextContent(s.precoFormatado);
      const acao = within(cartoes[i]).getByRole("link", { name: /^Agendar este serviço/ });
      expect(acao).toHaveTextContent("Agendar este serviço");
      expect(acao).toHaveAttribute("href", `/agendar?servicoId=${s.id}`);
    });
    expect(cartoes[5]).toHaveTextContent("Descoloração / Platinado (química)");
    expect(cartoes[5]).toHaveTextContent("R$ 80,00");
    // "Ver todos" leva à página completa; nela mesma o link não aparece
    expect(within(bloco).getByRole("link", { name: /Ver todos/ })).toHaveAttribute("href", "/servicos");
  });
});
