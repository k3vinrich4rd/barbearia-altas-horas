import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PAGAMENTO, reserva, servico } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, fixarRelogio, renderizar } from "@/testes/render";
import { Confirmacao } from "./Confirmacao";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const CONFIRMADA = reserva({
  status: "CONFIRMADO",
  expiraEm: null,
  servico: { id: servico(2).id, nome: "Corte Degradê", duracaoMin: 60 },
  inicio: "2026-12-01T09:00:00-03:00",
  fim: "2026-12-01T10:00:00-03:00",
  cliente: { nome: "Fulana Visitante", email: "fulana@exemplo.test", whatsapp: "11977776655" },
});

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio("2026-11-30T10:00:00-03:00");
  servidor.use(
    http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
    http.get(`${BFF}/agendamentos/guest/${CONFIRMADA.id}`, () => HttpResponse.json(CONFIRMADA)),
    http.get(`${BFF}/visitante`, () => HttpResponse.json({ reservaId: CONFIRMADA.id, codigo: "CODIGO-SECRETO-99" })),
  );
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("adicionar à minha agenda (Fatia 14D, M6)", () => {
  test("A59: Corte Degradê em 01/12/2026 às 09:00 (60 min) baixa um .ics com DTSTART 20261201T120000Z, DTEND 20261201T130000Z, o SUMMARY e o endereço, sem nome, e-mail, WhatsApp nem código do visitante", async () => {
    const arquivos: Blob[] = [];
    URL.createObjectURL = vi.fn((b: Blob) => {
      arquivos.push(b);
      return "blob:teste";
    }) as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    const baixou = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

    const { user } = renderizar(<Confirmacao reservaId={CONFIRMADA.id} />, ANONIMO);
    await screen.findByLabelText("Código"); // o código do visitante já está na tela
    await user.click(await screen.findByRole("button", { name: "Adicionar à minha agenda" }));

    expect(baixou).toHaveBeenCalledTimes(1);
    expect(arquivos).toHaveLength(1);
    expect(arquivos[0].type).toContain("text/calendar");
    const ics = await arquivos[0].text();
    expect(ics).toContain("DTSTART:20261201T120000Z");
    expect(ics).toContain("DTEND:20261201T130000Z");
    expect(ics).toContain("SUMMARY:Corte Degradê · Barbearia Altas Horas");
    expect(ics).toMatch(/LOCATION:R\. São Judas Tadeu\\, 313/);
    expect(ics).toContain("\r\n");
    for (const pessoal of ["Fulana", "fulana@exemplo.test", "11977776655", "CODIGO-SECRETO-99", "Carlos Eduardo"])
      expect(ics, pessoal).not.toContain(pessoal);
  });
});
