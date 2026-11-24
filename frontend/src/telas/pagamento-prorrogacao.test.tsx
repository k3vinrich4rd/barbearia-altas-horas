import { fireEvent, screen } from "@testing-library/react";
import QRCode from "qrcode";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PAGAMENTO, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { cobrancaPix, montar, preencher } from "@/testes/pagamento";
import { fixarRelogio } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("qrcode", () => ({
  default: { toDataURL: vi.fn(async (t: string) => `data:image/png;base64,GERADO-${t.length}`) },
}));
ligarServidor();

// Fatia 14A (E13): "Acrescentar 1 minuto" no último minuto do contador. Reserva criada às 10:00:00 de ter 01/12/2026,
// expiraEm 10:03:00; a janela (abreEm 10:02:00, fechaEm 10:03:00) vem da API, e quem decide é ela.
const PRAZO = "2026-12-01T10:03:00-03:00";
const NOVO_PRAZO = "2026-12-01T10:04:00-03:00";
const janela = (extra: Record<string, unknown> = {}) => ({
  usada: false,
  segundos: 60,
  abreEm: "2026-12-01T10:02:00-03:00",
  fechaEm: PRAZO,
  ...extra,
});
const depoisDeProrrogar = () =>
  reserva({
    expiraEm: NOVO_PRAZO,
    prorrogacao: janela({ usada: true, abreEm: PRAZO, fechaEm: NOVO_PRAZO }),
  });

beforeEach(() => zerarNavegacao());
afterEach(() => vi.useRealTimers());

/** A API da reserva do visitante; `posts` conta os "Acrescentar 1 minuto" que chegaram. */
function servir(opcoes: { prorrogacao: object | null; resposta?: () => Response | Promise<Response> }) {
  const chamadas = { posts: 0 };
  servidor.use(
    http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
    http.get(`${BFF}/agendamentos/guest/:id`, () =>
      HttpResponse.json(reserva({ expiraEm: PRAZO, prorrogacao: opcoes.prorrogacao })),
    ),
    http.post(`${BFF}/agendamentos/guest/:id/prorrogacao`, () => {
      chamadas.posts++;
      return opcoes.resposta?.() ?? HttpResponse.json(depoisDeProrrogar(), { status: 201 });
    }),
    http.post(`${BFF}/pagamentos/cobrancas`, () => HttpResponse.json(cobrancaPix(), { status: 201 })),
  );
  return chamadas;
}

const botao = () => screen.queryByRole("button", { name: "Acrescentar 1 minuto" });

describe("acrescentar 1 minuto", () => {
  test("A22: o botão só aparece no último minuto do contador e só enquanto a prorrogação não foi usada", async () => {
    const casos: [string, object, boolean][] = [
      ["2026-12-01T10:01:30-03:00", janela(), false],
      ["2026-12-01T10:02:10-03:00", janela(), true],
      ["2026-12-01T10:03:00-03:00", janela(), false],
      ["2026-12-01T10:02:10-03:00", janela({ usada: true }), false],
    ];
    for (const [agora, prorrogacao, aparece] of casos) {
      fixarRelogio(agora);
      servir({ prorrogacao });
      const { unmount } = montar();
      await screen.findByRole("timer");
      if (aparece) expect(await screen.findByRole("button", { name: "Acrescentar 1 minuto" })).toBeInTheDocument();
      else expect(botao()).toBeNull();
      unmount();
      vi.useRealTimers();
    }
  });

  test("A23: um só POST (o botão fica desabilitado enquanto envia); a mensagem 'Pronto, mais 1 minuto no contador.' recebe o foco; o botão some; o Pix na tela não muda", async () => {
    fixarRelogio("2026-12-01T10:02:10-03:00");
    let liberar!: () => void;
    const portao = new Promise<void>((r) => (liberar = r));
    const chamadas = servir({
      prorrogacao: janela(),
      resposta: async () => {
        await portao;
        return HttpResponse.json(depoisDeProrrogar(), { status: 201 });
      },
    });
    const { user } = montar();
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    const pix = await screen.findByLabelText("Pix copia e cola");
    expect(pix).toHaveValue("SIMULADO-0001-ABCDEF");
    const imagemAntes = (await screen.findByRole("img", { name: /QR Code do Pix/ })).getAttribute("src");

    await user.click(await screen.findByRole("button", { name: "Acrescentar 1 minuto" }));
    const enviando = await screen.findByRole("button", { name: /Acrescentando/ });
    expect(enviando).toBeDisabled();
    fireEvent.click(enviando);
    await user.click(enviando);
    expect(chamadas.posts).toBe(1);

    liberar();
    const mensagem = await screen.findByText("Pronto, mais 1 minuto no contador.");
    expect(mensagem).toHaveAttribute("role", "status");
    expect(mensagem).toHaveFocus();
    expect(botao()).toBeNull();
    expect(chamadas.posts).toBe(1);
    expect(screen.getByLabelText("Pix copia e cola")).toHaveValue("SIMULADO-0001-ABCDEF");
    expect(screen.getByRole("img", { name: /QR Code do Pix/ })).toHaveAttribute("src", imagemAntes);
    expect(QRCode.toDataURL).toHaveBeenCalledTimes(1);
  });

  test("A24: com o contador em 0:30 o toque leva para 1:30 e o contador continua correndo (e o leitor de tela ouve o tempo novo)", async () => {
    fixarRelogio("2026-12-01T10:02:30-03:00");
    servir({ prorrogacao: janela() });
    const { user } = montar();
    const contador = await screen.findByRole("timer");
    expect(contador).toHaveTextContent("00:30");

    await user.click(await screen.findByRole("button", { name: "Acrescentar 1 minuto" }));

    await vi.waitFor(() => expect(screen.getByRole("timer")).toHaveTextContent("01:30"));
    expect(screen.getByText("Faltam 2 minutos para pagar.")).toBeInTheDocument();
    expect(screen.getByText("Faltam 2 minutos para pagar.")).toHaveAttribute("aria-live", "polite");
    // continua correndo: um segundo depois do relógio do aparelho, o contador anda
    vi.setSystemTime(new Date("2026-12-01T10:02:35-03:00"));
    await vi.waitFor(() => expect(screen.getByRole("timer")).toHaveTextContent("01:25"), { timeout: 3000 });
  });

  test("A25: 422 RESERVA_EXPIRADA mostra o texto de expirado com 'Escolher outro horário'; 422 PRORROGACAO_FORA_DA_JANELA faz o botão sumir", async () => {
    fixarRelogio("2026-12-01T10:02:59-03:00");
    servir({ prorrogacao: janela(), resposta: () => erro(422, "RESERVA_EXPIRADA") });
    let tela = montar();
    await tela.user.click(await screen.findByRole("button", { name: "Acrescentar 1 minuto" }));
    expect(await screen.findByText(/O tempo acabou e o horário voltou para a agenda/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Escolher outro horário" })).toHaveAttribute("href", "/agendar");
    expect(botao()).toBeNull();
    tela.unmount();

    fixarRelogio("2026-12-01T10:02:10-03:00");
    servir({ prorrogacao: janela(), resposta: () => erro(422, "PRORROGACAO_FORA_DA_JANELA") });
    tela = montar();
    await tela.user.click(await screen.findByRole("button", { name: "Acrescentar 1 minuto" }));
    await vi.waitFor(() => expect(botao()).toBeNull());
    // o contador segue e a página não mostra erro: o botão volta quando a janela da API valer (aqui, depois de um tempo)
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
