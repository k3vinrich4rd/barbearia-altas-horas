import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { ServicosDaHome } from "@/componentes/ListaDeServicos";
import { SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

async function renderizarLista(servicos: typeof SEIS_SERVICOS) {
  servidor.use(http.get(`${BFF}/servicos`, () => HttpResponse.json(servicos)));
  renderizar(<ServicosDaHome />);
  const bloco = (await screen.findByRole("heading", { name: "Um barbeiro, todos os serviços" })).closest("section")!;
  await within(bloco).findAllByRole("listitem");
  return bloco;
}

describe('"02 · O que fazemos" na home (Fatia 16, F5)', () => {
  test("A10: com todos os serviços da mesma duração, ela aparece 1× no apoio; nome e preço em cada linha, e a própria linha leva ao agendamento", async () => {
    const bloco = await renderizarLista(SEIS_SERVICOS);
    expect(bloco).toHaveAttribute("id", "servicos");
    expect(within(bloco).getByText("02 · O que fazemos")).toBeInTheDocument();
    expect(within(bloco).getByText("Cada serviço dura 60 min. Toque em um para ver os horários.")).toBeInTheDocument();
    expect(bloco.textContent!.split("60 min").length - 1).toBe(1);

    const linhas = within(bloco).getAllByRole("listitem");
    expect(linhas).toHaveLength(6);
    SEIS_SERVICOS.forEach((s, i) => {
      const link = within(linhas[i]).getByRole("link");
      expect(link).toHaveTextContent(s.nome);
      expect(link).toHaveTextContent(s.precoFormatado);
      expect(link).toHaveAttribute("href", `/agendar?servicoId=${s.id}`);
    });
    expect(linhas[5]).toHaveTextContent("Descoloração / Platinado (química)");
    expect(linhas[5]).toHaveTextContent("R$ 80,00");
    // a lista completa é esta: sem "Agendar este serviço" e sem "Ver todos"
    expect(within(bloco).queryByText(/Agendar este serviço/)).toBeNull();
    expect(within(bloco).queryByRole("link", { name: /Ver todos/ })).toBeNull();
    expect(within(bloco).getAllByRole("link")).toHaveLength(6);
  });

  test("A10: com durações diferentes, a duração aparece em cada linha e o apoio não cita minutos", async () => {
    const variados = SEIS_SERVICOS.map((s, i) => ({ ...s, duracaoMin: i === 0 ? 30 : 60 }));
    const bloco = await renderizarLista(variados);
    expect(within(bloco).getByText("Toque em um serviço para ver os horários.")).toBeInTheDocument();
    const linhas = within(bloco).getAllByRole("listitem");
    variados.forEach((s, i) => {
      expect(linhas[i]).toHaveTextContent(`${s.duracaoMin} min`);
      expect(linhas[i]).toHaveTextContent(s.nome);
      expect(linhas[i]).toHaveTextContent(s.precoFormatado);
    });
    expect(within(bloco).queryByText(/Cada serviço dura/)).toBeNull();
  });
});
