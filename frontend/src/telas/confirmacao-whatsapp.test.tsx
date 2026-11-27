import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, POLITICA_PAGAMENTO, reserva, servico } from "@/testes/dados";
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
afterEach(() => vi.useRealTimers());

describe("mandar no WhatsApp (Fatia 14D, M7)", () => {
  test("A60: o href começa com https://wa.me/?text= e o texto tem serviço, dia, hora e endereço, sem código do visitante nem nome; abre em nova aba com noopener noreferrer", async () => {
    renderizar(<Confirmacao reservaId={CONFIRMADA.id} />, ANONIMO);
    await screen.findByLabelText("Código");
    const link = await screen.findByRole("link", { name: "Mandar no WhatsApp" });
    const href = link.getAttribute("href")!;
    expect(href.startsWith("https://wa.me/?text=")).toBe(true);
    const texto = decodeURIComponent(href.slice("https://wa.me/?text=".length));
    expect(texto).toContain("Corte Degradê");
    expect(texto).toMatch(/01\/12/);
    expect(texto).toContain("09:00");
    expect(texto).toContain("R. São Judas Tadeu, 313");
    for (const pessoal of ["CODIGO-SECRETO-99", "Fulana", "fulana@exemplo.test", "11977776655"])
      expect(texto, pessoal).not.toContain(pessoal);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });
});

const NUMERO_TESTE = "11900000001";

describe("avisar atraso pelo WhatsApp", () => {
  test("CA-1 e CA-4: com WhatsApp do barbeiro, o texto usa o primeiro nome, o link é wa.me/55 sem ?text= e o nome acessível avisa que abre o WhatsApp", async () => {
    servidor.use(
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido", whatsapp: NUMERO_TESTE }]),
      ),
    );
    renderizar(<Confirmacao reservaId={CONFIRMADA.id} />, ANONIMO);
    expect(await screen.findByText("Vai se atrasar? Avise o Eduardo pelo WhatsApp.")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Avisar pelo WhatsApp (abre o WhatsApp)" });
    expect(link).toHaveTextContent("Avisar pelo WhatsApp");
    expect(link.getAttribute("href")).toBe(`https://wa.me/55${NUMERO_TESTE}`);
    expect(link.getAttribute("href")).not.toContain("text=");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  test('CA-2: "Prefere combinar pelo WhatsApp?" não aparece na confirmação', async () => {
    servidor.use(
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido", whatsapp: NUMERO_TESTE }]),
      ),
    );
    renderizar(<Confirmacao reservaId={CONFIRMADA.id} />, ANONIMO);
    await screen.findByRole("link", { name: "Avisar pelo WhatsApp (abre o WhatsApp)" });
    expect(screen.queryByText(/Prefere combinar pelo WhatsApp/)).toBeNull();
  });

  test("CA-3: sem WhatsApp, fica o texto genérico e não há o link de aviso", async () => {
    renderizar(<Confirmacao reservaId={CONFIRMADA.id} />, ANONIMO);
    expect(await screen.findByText("Vai se atrasar? Avise a barbearia.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Avisar pelo WhatsApp (abre o WhatsApp)" })).toBeNull();
    expect(screen.queryByText(/Avise o/)).toBeNull();
  });
});
