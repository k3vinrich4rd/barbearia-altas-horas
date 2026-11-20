import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { cobrancaAdmin } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelPendencias } from "./PainelPendencias";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

const PAGA_TARDE = cobrancaAdmin({
  id: "c2",
  status: "PAGA_APOS_EXPIRACAO",
  valorCentavos: 5500,
  contato: { nome: "Visitante", whatsapp: "11922223333" },
  reserva: {
    id: "r2",
    inicio: "2026-11-21T10:00:00-03:00",
    servico: "Corte Degradê",
    statusReserva: "EXPIRADO",
    motivoCancelamento: null,
  },
});
const EVENTO = {
  id: "e1",
  tipo: "PAYMENT_RECEIVED",
  recebidoEm: "2026-11-17T09:12:00-03:00",
  tentativas: 5,
  ultimoErro: "SEM_COBRANCA",
  idCobrancaProvedor: null,
  resultado: "REVISAO_MANUAL",
  processadoEm: null,
};

function pagina(itens: unknown[], total = itens.length) {
  return { itens, pagina: 0, tamanho: 10, total };
}

describe("pendências de pagamento", () => {
  test("A48: tabela com contato, serviço, data, valor, status e motivo; Registrar devolução confirma, chama a rota e tira a linha; repetição 422 mostra já registrada; eventos em revisão com Marcar como revisado; paginação e vazio", async () => {
    let cobrancas = [cobrancaAdmin(), PAGA_TARDE];
    let eventos = [EVENTO];
    const consultas: URLSearchParams[] = [];
    const devolvidas: string[] = [];
    servidor.use(
      http.get(`${BFF}/admin/pagamentos`, ({ request }) => {
        consultas.push(new URL(request.url).searchParams);
        return HttpResponse.json(pagina(cobrancas, 25));
      }),
      http.get(`${BFF}/admin/pagamentos/eventos`, () => HttpResponse.json(pagina(eventos))),
      http.post(`${BFF}/admin/pagamentos/:id/devolucao`, ({ params }) => {
        devolvidas.push(String(params.id));
        cobrancas = cobrancas.filter((c) => c.id !== params.id);
        return HttpResponse.json({ ...cobrancaAdmin(), status: "DEVOLVIDA" });
      }),
      http.post(`${BFF}/admin/pagamentos/eventos/:id/revisao`, () => {
        eventos = [];
        return HttpResponse.json({ ...EVENTO, resultado: "REVISADO" });
      }),
    );
    const { user } = renderizar(<PainelPendencias />, ADMIN);

    // sem status: a API entrega as pendências, as mais antigas primeiro; a tela não manda `status`
    const tabela = await screen.findByRole("table", { name: "Cobranças a devolver" });
    expect(consultas[0].has("status")).toBe(false);
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[0]).toHaveTextContent("Lucas P.");
    expect(linhas[0]).toHaveTextContent("(11) 98765-4321");
    expect(linhas[0]).toHaveTextContent("Corte Moderno / Visagismo (Tesoura)");
    expect(linhas[0]).toHaveTextContent("quinta, 19/11 às 13:00");
    expect(linhas[0]).toHaveTextContent("R$ 60,00");
    expect(linhas[0]).toHaveTextContent("Devolução pendente");
    expect(linhas[0]).toHaveTextContent("Cancelado pela barbearia: barbeiro em curso");
    expect(linhas[1]).toHaveTextContent("Paga depois do prazo");
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();

    // eventos em revisão: tipo, tentativas e último erro
    const eventosTabela = screen.getByRole("table", { name: "Avisos em revisão manual" });
    expect(eventosTabela).toHaveTextContent("PAYMENT_RECEIVED");
    expect(eventosTabela).toHaveTextContent("5");
    expect(eventosTabela).toHaveTextContent("SEM_COBRANCA");
    await user.click(within(eventosTabela).getByRole("button", { name: /Marcar como revisado/ }));
    await vi.waitFor(() => expect(screen.queryByRole("table", { name: "Avisos em revisão manual" })).toBeNull());
    expect(screen.getAllByText("Nada pendente.").length).toBeGreaterThan(0);

    // devolução: confirma no diálogo e a linha sai da fila
    await user.click(
      within(linhas[0]).getByRole("button", { name: /Registrar devolução de R\$ 60,00 para Lucas P\./ }),
    );
    const dialogo = screen.getByRole("dialog");
    expect(devolvidas).toHaveLength(0);
    await user.click(within(dialogo).getByRole("button", { name: "Registrar devolução" }));
    await vi.waitFor(() => expect(devolvidas).toEqual(["c0000000-0000-4000-8000-000000000001"]));
    await vi.waitFor(() => expect(screen.queryByText("Lucas P.")).toBeNull());
    expect(await screen.findByText("Devolução registrada.")).toBeInTheDocument();

    // a repetição (422) mostra "já registrada"
    servidor.use(http.post(`${BFF}/admin/pagamentos/:id/devolucao`, () => erro(422, "TRANSICAO_INVALIDA")));
    await user.click(screen.getByRole("button", { name: /Registrar devolução de R\$ 55,00/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Registrar devolução" }));
    expect(await screen.findByText("Esta devolução já foi registrada.")).toBeInTheDocument();
  });

  test("A48: sem nenhuma pendência mostra nada pendente", async () => {
    servidor.use(
      http.get(`${BFF}/admin/pagamentos`, () => HttpResponse.json(pagina([]))),
      http.get(`${BFF}/admin/pagamentos/eventos`, () => HttpResponse.json(pagina([]))),
    );
    renderizar(<PainelPendencias />, ADMIN);
    await vi.waitFor(() => expect(screen.getAllByText("Nada pendente.")).toHaveLength(2));
  });

  test("A7: o filtro de status vai para a API (A devolver sem status, Aguardando PENDENTE, Pagas PAGA, Expiradas ou canceladas CANCELADA), volta para a página 1, mostra a tabela daquele status e só A devolver tem o botão de devolução", async () => {
    const consultas: URLSearchParams[] = [];
    const paga = cobrancaAdmin({ id: "c9", status: "PAGA", contato: { nome: "Rafael Souza", whatsapp: "11988880001" } });
    servidor.use(
      http.get(`${BFF}/admin/pagamentos`, ({ request }) => {
        const q = new URL(request.url).searchParams;
        consultas.push(q);
        const status = q.get("status");
        if (status === "PAGA") return HttpResponse.json(pagina([paga], 25));
        if (status === "PENDENTE") return HttpResponse.json(pagina([]));
        return HttpResponse.json(pagina([cobrancaAdmin()], 25));
      }),
      http.get(`${BFF}/admin/pagamentos/eventos`, () => HttpResponse.json(pagina([]))),
    );
    const { user } = renderizar(<PainelPendencias />, ADMIN);

    const grupo = await screen.findByRole("group", { name: "Filtrar cobranças" });
    expect(within(grupo).getAllByRole("button").map((b) => b.textContent)).toEqual([
      "A devolver",
      "Aguardando",
      "Pagas",
      "Expiradas ou canceladas",
    ]);
    expect(within(grupo).getByRole("button", { name: "A devolver" })).toHaveAttribute("aria-pressed", "true");
    await screen.findByRole("table", { name: "Cobranças a devolver" });
    expect(consultas[0].has("status")).toBe(false);

    // vai para a página 2 e troca o filtro: volta para a 0
    await user.click(screen.getByRole("button", { name: "Próxima" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("pagina")).toBe("1"));
    await user.click(within(grupo).getByRole("button", { name: "Pagas" }));
    const tabela = await screen.findByRole("table", { name: "Cobranças pagas" });
    expect(consultas.at(-1)?.get("status")).toBe("PAGA");
    expect(consultas.at(-1)?.get("pagina")).toBe("0");
    expect(within(grupo).getByRole("button", { name: "Pagas" })).toHaveAttribute("aria-pressed", "true");
    expect(within(grupo).getByRole("button", { name: "A devolver" })).toHaveAttribute("aria-pressed", "false");
    expect(tabela).toHaveTextContent("Rafael Souza");
    expect(tabela).toHaveTextContent("Paga");
    expect(within(tabela).queryByRole("button")).toBeNull();
    expect(within(tabela).queryByRole("columnheader", { name: "Ação" })).toBeNull();

    await user.click(within(grupo).getByRole("button", { name: "Aguardando" }));
    expect(await screen.findByText("Nenhuma cobrança neste filtro.")).toBeInTheDocument();
    expect(consultas.at(-1)?.get("status")).toBe("PENDENTE");

    await user.click(within(grupo).getByRole("button", { name: "Expiradas ou canceladas" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("status")).toBe("CANCELADA"));

    await user.click(within(grupo).getByRole("button", { name: "A devolver" }));
    const devolver = await screen.findByRole("table", { name: "Cobranças a devolver" });
    expect(consultas.at(-1)?.has("status")).toBe(false);
    expect(within(devolver).getAllByRole("button", { name: /Registrar devolução/ }).length).toBeGreaterThan(0);
  });
});
