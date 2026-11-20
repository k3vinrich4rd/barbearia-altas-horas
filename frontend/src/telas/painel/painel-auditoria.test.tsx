import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { ADMIN, renderizar } from "@/testes/render";
import { PainelAuditoria } from "./PainelAuditoria";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

describe("auditoria", () => {
  test("A52: tabela paginada com data no fuso, ação, recurso e detalhe técnico (de → para e ids); nenhuma coluna de IP, nome, motivo nem WhatsApp", async () => {
    const consultas: URLSearchParams[] = [];
    servidor.use(
      http.get(`${BFF}/admin/auditoria`, ({ request }) => {
        consultas.push(new URL(request.url).searchParams);
        return HttpResponse.json({
          pagina: 0,
          tamanho: 20,
          total: 45,
          itens: [
            {
              id: "1",
              atorId: "u1",
              acao: "AGENDAMENTO_CANCELADO",
              recurso: "agendamento",
              recursoId: "a0000000-0000-4000-8000-000000000001",
              detalhe: { de: "CONFIRMADO", para: "CANCELADO" },
              ocorridoEm: "2026-11-19T13:05:00-03:00",
            },
            {
              id: "2",
              atorId: "u1",
              acao: "CONFIGURACAO_ALTERADA",
              recurso: "configuracao",
              recursoId: null,
              detalhe: { campos: "expiracaoPagamentoMin" },
              ocorridoEm: "2026-11-19T10:58:00Z",
            },
            {
              id: "3",
              atorId: "u1",
              acao: "ACAO_NOVA_DA_API",
              recurso: "outro",
              recursoId: null,
              detalhe: null,
              ocorridoEm: "2026-11-18T09:00:00-03:00",
            },
          ],
        });
      }),
    );
    const { user } = renderizar(<PainelAuditoria />, ADMIN);
    const tabela = await screen.findByRole("table", { name: /Últimas ações · página 1 de 3/ });
    expect(consultas[0].get("pagina")).toBe("0");

    const colunas = within(tabela)
      .getAllByRole("columnheader")
      .map((c) => c.textContent);
    expect(colunas).toEqual(["Quando", "Ação", "Recurso", "Detalhe"]);
    expect(colunas.join(" ")).not.toMatch(/IP|nome|motivo|WhatsApp/i);

    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas).toHaveLength(3);
    expect(linhas[0]).toHaveTextContent("quinta, 19/11 às 13:05");
    expect(linhas[0]).toHaveTextContent("Reserva cancelada pela barbearia");
    expect(linhas[0]).toHaveTextContent("agendamento");
    expect(linhas[0]).toHaveTextContent("CONFIRMADO → CANCELADO");
    expect(linhas[1]).toHaveTextContent("quinta, 19/11 às 07:58"); // 10:58Z no fuso da barbearia (UTC-3)
    expect(linhas[1]).toHaveTextContent("campos: expiracaoPagamentoMin");
    expect(linhas[2]).toHaveTextContent("ACAO_NOVA_DA_API"); // ação que o front não conhece aparece como veio

    await user.click(screen.getByRole("button", { name: "Próxima" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("pagina")).toBe("1"));
  });

  test("A8: o filtro Ação (Todas e as 14 ações) manda acao para a API, volta para a página 1, mostra só o que a API devolveu e avisa quando não há nenhuma daquele tipo", async () => {
    const consultas: URLSearchParams[] = [];
    servidor.use(
      http.get(`${BFF}/admin/auditoria`, ({ request }) => {
        const q = new URL(request.url).searchParams;
        consultas.push(q);
        const acao = q.get("acao");
        const item = (id: string, a: string) => ({
          id,
          atorId: "u1",
          acao: a,
          recurso: "servico",
          recursoId: null,
          detalhe: { campos: "precoCentavos" },
          ocorridoEm: "2026-11-19T13:05:00-03:00",
        });
        if (acao === "EVENTO_REVISADO") return HttpResponse.json({ pagina: 0, tamanho: 20, total: 0, itens: [] });
        return HttpResponse.json({
          pagina: Number(q.get("pagina")),
          tamanho: 20,
          total: 45,
          itens: [item("1", acao ?? "SERVICO_CRIADO")],
        });
      }),
    );
    const { user } = renderizar(<PainelAuditoria />, ADMIN);
    const filtro = await screen.findByLabelText("Ação");
    expect(within(filtro).getAllByRole("option")).toHaveLength(15);
    expect(within(filtro).getAllByRole("option")[0]).toHaveTextContent("Todas");
    expect(consultas[0].has("acao")).toBe(false);

    await user.click(await screen.findByRole("button", { name: "Próxima" }));
    await vi.waitFor(() => expect(consultas.at(-1)?.get("pagina")).toBe("1"));

    await user.selectOptions(filtro, "SERVICO_ALTERADO");
    await vi.waitFor(() => expect(consultas.at(-1)?.get("acao")).toBe("SERVICO_ALTERADO"));
    expect(consultas.at(-1)?.get("pagina")).toBe("0");
    const tabela = await screen.findByRole("table", { name: /Últimas ações · página 1 de 3/ });
    expect(within(tabela).getAllByRole("row")[1]).toHaveTextContent("Serviço alterado");

    await user.selectOptions(filtro, "EVENTO_REVISADO");
    expect(await screen.findByText("Nenhuma ação deste tipo registrada.")).toBeInTheDocument();
    expect(screen.getByLabelText("Ação")).toHaveValue("EVENTO_REVISADO"); // o filtro continua na tela para voltar

    await user.selectOptions(screen.getByLabelText("Ação"), "");
    await vi.waitFor(() => expect(consultas.at(-1)?.has("acao")).toBe(false));
  });
});
