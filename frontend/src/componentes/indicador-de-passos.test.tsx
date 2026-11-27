import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS, POLITICA_PAGAMENTO, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { api, montar } from "@/testes/pagamento";
import { ANONIMO, fixarRelogio, renderizar } from "@/testes/render";
import { IndicadorDePassos } from "./IndicadorDePassos";
import { Agendar } from "@/telas/Agendar";
import { Confirmacao } from "@/telas/Confirmacao";
import { ReservaDados } from "@/telas/ReservaDados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("qrcode", () => ({ default: { toDataURL: vi.fn(async () => "data:image/png;base64,QR") } }));
ligarServidor();

const INICIO = "2026-11-24T10:00:00-03:00";

function conferirPassos(atual: 1 | 2 | 3) {
  const nav = screen.getByRole("navigation", { name: "Etapas do agendamento" });
  const itens = within(nav).getAllByRole("listitem");
  expect(itens).toHaveLength(3);
  expect(itens[0]).toHaveTextContent("1 · Horário");
  expect(itens[1]).toHaveTextContent("2 · Pagamento");
  expect(itens[2]).toHaveTextContent("3 · Confirmação");
  expect(itens[atual - 1]).toHaveAttribute("aria-current", "step");
  itens.forEach((item, i) => {
    if (i === atual - 1) {
      expect(item).toHaveTextContent("etapa atual");
      expect(item.textContent).not.toContain("concluído");
    } else {
      expect(item).not.toHaveAttribute("aria-current");
    }
    if (i < atual - 1) {
      expect(item).toHaveTextContent("✓");
      expect(item).toHaveTextContent("concluído");
    }
  });
}

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

describe("indicador de passos do agendamento", () => {
  test("IP-1: em /agendar os três passos aparecem e 1 · Horário é o atual", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    );
    renderizar(<Agendar />);
    await screen.findByRole("heading", { level: 1, name: "Agende seu horário" });
    conferirPassos(1);
    expect(screen.getByText("Agenda · Eduardo Candido")).toBeInTheDocument();
  });

  test("IP-2: em /agendar/dados o passo atual continua 1 · Horário", async () => {
    definirBusca(
      new URLSearchParams({ servicoId: CINCO_SERVICOS[1].id, barbeiroId: BARBEIRO.id, inicio: INICIO }).toString(),
    );
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    );
    renderizar(<ReservaDados />, ANONIMO);
    expect(await screen.findByRole("heading", { level: 1, name: "Seus dados" })).toBeInTheDocument();
    conferirPassos(1);
  });

  test("IP-3: no pagamento o passo 1 está concluído, 2 é o atual e o sobretítulo Pagamento não aparece", async () => {
    api({ politica: { versao: 7, texto: POLITICA_PAGAMENTO.texto } });
    montar();
    expect(
      await screen.findByRole("heading", { level: 1, name: "Pague para garantir seu horário" }),
    ).toBeInTheDocument();
    conferirPassos(2);
    expect(screen.queryByText(/^Pagamento$/)).toBeNull();
    expect(screen.queryByText("Etapa 3 · Confirmação")).toBeNull();
  });

  test("IP-4: na confirmação os passos 1 e 2 estão concluídos, 3 é o atual e Etapa 3 · Confirmação não aparece", async () => {
    const confirmada = reserva({ status: "CONFIRMADO", expiraEm: null });
    servidor.use(
      http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
      http.get(`${BFF}/agendamentos/guest/${confirmada.id}`, () => HttpResponse.json(confirmada)),
      http.get(`${BFF}/visitante`, () => HttpResponse.json({ reservaId: confirmada.id, codigo: "CODIGO-ABC-123" })),
    );
    renderizar(<Confirmacao reservaId={confirmada.id} />, ANONIMO);
    expect(await screen.findByLabelText("Código")).toBeInTheDocument();
    conferirPassos(3);
    expect(screen.queryByText("Etapa 3 · Confirmação")).toBeNull();
  });

  test("IP-4b: confirmação sem reserva encontrada não mostra o indicador", async () => {
    const id = reserva().id;
    servidor.use(
      http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
      http.get(`${BFF}/agendamentos/guest/${id}`, () => erro(404, "RECURSO_NAO_ENCONTRADO")),
    );
    renderizar(<Confirmacao reservaId={id} />, ANONIMO);
    expect(await screen.findByText("Não encontramos esta reserva.")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Etapas do agendamento" })).toBeNull();
  });

  test("IP-5: o nav é uma lista ordenada de 3 itens e declara a etapa atual", () => {
    renderizar(<IndicadorDePassos passoAtual={2} />);
    const nav = screen.getByRole("navigation", { name: "Etapas do agendamento" });
    expect(nav.querySelector("ol")).not.toBeNull();
    conferirPassos(2);
  });
});
