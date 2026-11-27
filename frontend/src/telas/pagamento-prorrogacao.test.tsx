import { act, fireEvent, screen } from "@testing-library/react";
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

// Fatia 14A (E13) com a regra da Fatia 15 (ADR 0015, E20 a E23): "Acrescentar 2 minutos" no último minuto do contador, até
// 10 pedidos. Reserva criada às 10:00:00 de ter 01/12/2026, expiraEm 10:03:00; a janela (abreEm 10:02:00, fechaEm 10:03:00)
// vem da API, e quem decide é ela. Depois de um pedido, expiraEm 10:05:00 e a janela passa a ser 10:04:00 a 10:05:00.
const PRAZO = "2026-12-01T10:03:00-03:00";
const NOVO_PRAZO = "2026-12-01T10:05:00-03:00";
const janela = (extra: Record<string, unknown> = {}) => ({
  usada: false,
  usadas: 0,
  maximo: 10,
  restantes: 10,
  segundos: 120,
  abreEm: "2026-12-01T10:02:00-03:00",
  fechaEm: PRAZO,
  ...extra,
});
/** Como a API responde depois do pedido número `usadas`. */
const usadasAte = (usadas: number) => ({ usada: usadas > 0, usadas, restantes: 10 - usadas });
const depoisDeProrrogar = (usadas = 1) =>
  reserva({
    expiraEm: NOVO_PRAZO,
    prorrogacao: janela({ ...usadasAte(usadas), abreEm: "2026-12-01T10:04:00-03:00", fechaEm: NOVO_PRAZO }),
  });

beforeEach(() => zerarNavegacao());
afterEach(() => vi.useRealTimers());

/** A API da reserva do visitante; `posts` conta os "Acrescentar 2 minutos" que chegaram e `chaves` guarda a X-Idempotency-Key de cada um. */
function servir(opcoes: { prorrogacao: object | null; resposta?: () => Response | Promise<Response> }) {
  const chamadas = { posts: 0, chaves: [] as (string | null)[] };
  servidor.use(
    http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(POLITICA_PAGAMENTO)),
    http.get(`${BFF}/agendamentos/guest/:id`, () =>
      HttpResponse.json(reserva({ expiraEm: PRAZO, prorrogacao: opcoes.prorrogacao })),
    ),
    http.post(`${BFF}/agendamentos/guest/:id/prorrogacao`, ({ request }) => {
      chamadas.posts++;
      chamadas.chaves.push(request.headers.get("x-idempotency-key"));
      return opcoes.resposta?.() ?? HttpResponse.json(depoisDeProrrogar(), { status: 201 });
    }),
    http.post(`${BFF}/pagamentos/cobrancas`, () => HttpResponse.json(cobrancaPix(), { status: 201 })),
  );
  return chamadas;
}

const ACRESCENTAR = "Acrescentar 2 minutos";
const botao = () => screen.queryByRole("button", { name: ACRESCENTAR });
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("acrescentar 2 minutos", () => {
  test("A22: o botão só aparece no último minuto do contador e só enquanto restam pedidos (restantes > 0)", async () => {
    const casos: [string, object, boolean][] = [
      ["2026-12-01T10:01:30-03:00", janela(), false],
      ["2026-12-01T10:02:10-03:00", janela(), true],
      ["2026-12-01T10:03:00-03:00", janela(), false],
      ["2026-12-01T10:02:10-03:00", janela(usadasAte(9)), true],
      ["2026-12-01T10:02:10-03:00", janela(usadasAte(10)), false],
      // API antiga, sem `restantes`: vale o `usada` de antes
      ["2026-12-01T10:02:10-03:00", { ...janela({ usada: true }), usadas: undefined, restantes: undefined }, false],
    ];
    for (const [agora, prorrogacao, aparece] of casos) {
      fixarRelogio(agora);
      servir({ prorrogacao });
      const { unmount } = montar();
      await screen.findByRole("timer");
      if (aparece) expect(await screen.findByRole("button", { name: ACRESCENTAR })).toBeInTheDocument();
      else expect(botao()).toBeNull();
      unmount();
      vi.useRealTimers();
    }
  });

  test("A23: um só POST (o botão fica desabilitado enquanto envia); a mensagem 'Pronto, mais 2 minutos no contador.' recebe o foco; o botão some; o Pix na tela não muda", async () => {
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

    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    const enviando = await screen.findByRole("button", { name: /Acrescentando/ });
    expect(enviando).toBeDisabled();
    fireEvent.click(enviando);
    await user.click(enviando);
    expect(chamadas.posts).toBe(1);

    liberar();
    const mensagem = await screen.findByText("Pronto, mais 2 minutos no contador.");
    expect(mensagem).toHaveAttribute("role", "status");
    // o foco vai num efeito depois de a mensagem aparecer: espera esse efeito (sob carga no CI ele vinha um instante depois)
    await vi.waitFor(() => expect(mensagem).toHaveFocus());
    expect(botao()).toBeNull();
    expect(chamadas.posts).toBe(1);
    expect(screen.getByLabelText("Pix copia e cola")).toHaveValue("SIMULADO-0001-ABCDEF");
    expect(screen.getByRole("img", { name: /QR Code do Pix/ })).toHaveAttribute("src", imagemAntes);
    expect(QRCode.toDataURL).toHaveBeenCalledTimes(1);
  });

  test("A24: com o contador em 0:30 o toque leva para 2:30 e o contador continua correndo (e o leitor de tela ouve o tempo novo)", async () => {
    // relógio e setInterval falsos: o contador só anda quando o teste manda, então o valor é exato em qualquer máquina
    vi.useFakeTimers({ toFake: ["Date", "setInterval", "clearInterval"] });
    vi.setSystemTime(new Date("2026-12-01T10:02:30-03:00"));
    servir({ prorrogacao: janela() });
    const { user } = montar();
    const contador = await screen.findByRole("timer");
    expect(contador).toHaveTextContent("00:30");

    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }));

    // o prazo novo vale na hora (10:05:00 − 10:02:30), sem esperar o próximo tique
    await vi.waitFor(() => expect(screen.getByRole("timer")).toHaveTextContent("02:30"));
    expect(screen.getByText("Faltam 3 minutos para pagar.")).toBeInTheDocument();
    expect(screen.getByText("Faltam 3 minutos para pagar.")).toHaveAttribute("aria-live", "polite");
    // continua correndo: 5 tiques de 1 s levam o relógio a 10:02:35 e o contador a 02:25, exatamente
    act(() => vi.advanceTimersByTime(5000));
    expect(screen.getByRole("timer")).toHaveTextContent("02:25");
  });

  test("A25: 422 RESERVA_EXPIRADA mostra o texto de expirado com 'Escolher outro horário'; 422 PRORROGACAO_FORA_DA_JANELA faz o botão sumir", async () => {
    fixarRelogio("2026-12-01T10:02:59-03:00");
    servir({ prorrogacao: janela(), resposta: () => erro(422, "RESERVA_EXPIRADA") });
    let tela = montar();
    await tela.user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    expect(await screen.findByText(/O tempo para pagar acabou e o horário voltou para a agenda/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Escolher outro horário" })).toHaveAttribute("href", "/agendar");
    expect(botao()).toBeNull();
    tela.unmount();

    fixarRelogio("2026-12-01T10:02:10-03:00");
    servir({ prorrogacao: janela(), resposta: () => erro(422, "PRORROGACAO_FORA_DA_JANELA") });
    tela = montar();
    await tela.user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    await vi.waitFor(() => expect(botao()).toBeNull());
    // o contador segue e a página não mostra erro: o botão volta quando a janela da API valer (aqui, depois de um tempo)
    expect(screen.queryByRole("alert")).toBeNull();
  });
});

// C1 (spec de UX "Mais tempo para pagar", MT-1 a MT-7): "Restam N pedidos." discreto a partir do 1º pedido, a chave de
// idempotência por toque e o 422 PRORROGACAO_ESGOTADA com o texto final (D214).
describe("até 10 pedidos de mais tempo (Fatia 15)", () => {
  const segundaJanela = "2026-12-01T10:04:10-03:00";

  test("MT-1 e MT-1b: sem pedido usado não aparece 'Restam N pedidos.'; depois do 1º aparece, discreto, com o número da API", async () => {
    const casos: [object, string | null][] = [
      [janela(), null],
      [janela(usadasAte(1)), "Restam 9 pedidos."],
      [janela(usadasAte(9)), "Resta 1 pedido."],
    ];
    for (const [prorrogacao, linha] of casos) {
      fixarRelogio("2026-12-01T10:02:10-03:00");
      servir({ prorrogacao });
      const { unmount } = montar();
      expect(await screen.findByRole("button", { name: ACRESCENTAR })).toBeInTheDocument();
      if (linha === null) expect(screen.queryByText(/^Resta(m)? \d+ pedidos?\.$/)).toBeNull();
      else expect(screen.getByText(linha)).toHaveClass("nota");
      unmount();
      vi.useRealTimers();
    }
  });

  test("MT-3: com 9 usados o 10º funciona; depois o botão e a linha de pedidos não voltam no último minuto seguinte", async () => {
    fixarRelogio("2026-12-01T10:02:10-03:00");
    const chamadas = servir({
      prorrogacao: janela(usadasAte(9)),
      resposta: () => HttpResponse.json(depoisDeProrrogar(10), { status: 201 }),
    });
    const { user } = montar();
    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    expect(await screen.findByText("Pronto, mais 2 minutos no contador.")).toBeInTheDocument();
    expect(chamadas.posts).toBe(1);

    vi.setSystemTime(new Date(segundaJanela));
    expect(await screen.findByText("Menos de 1 minuto para pagar.", {}, { timeout: 3000 })).toBeInTheDocument();
    expect(botao()).toBeNull();
    expect(screen.queryByText(/pedidos?\.$/)).toBeNull();
  });

  test("MT-4c: 422 PRORROGACAO_ESGOTADA mostra 'Você já usou todo o tempo extra. Conclua o pagamento agora ou escolha outro horário.' e o botão some", async () => {
    fixarRelogio("2026-12-01T10:02:10-03:00");
    servir({ prorrogacao: janela(usadasAte(9)), resposta: () => erro(422, "PRORROGACAO_ESGOTADA") });
    const { user } = montar();
    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    expect(
      await screen.findByText("Você já usou todo o tempo extra. Conclua o pagamento agora ou escolha outro horário."),
    ).toBeInTheDocument();
    expect(botao()).toBeNull();
    expect(screen.getByRole("timer")).toBeInTheDocument();
  });

  test("MT-4d: cada toque manda uma X-Idempotency-Key nova (UUID); se a rede cai, o front repete sozinho com a MESMA chave", async () => {
    fixarRelogio("2026-12-01T10:02:10-03:00");
    let primeiro = true;
    const chamadas = servir({
      prorrogacao: janela(),
      resposta: () => {
        if (primeiro) {
          primeiro = false;
          return HttpResponse.error();
        }
        return HttpResponse.json(depoisDeProrrogar(chamadas.posts > 2 ? 2 : 1), { status: 201 });
      },
    });
    const { user } = montar();
    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }));
    expect(await screen.findByText("Pronto, mais 2 minutos no contador.")).toBeInTheDocument();
    expect(chamadas.posts).toBe(2);
    expect(chamadas.chaves[0]).toMatch(UUID);
    expect(chamadas.chaves[1]).toBe(chamadas.chaves[0]);

    // um toque novo, no último minuto seguinte, manda outra chave
    vi.setSystemTime(new Date(segundaJanela));
    await user.click(await screen.findByRole("button", { name: ACRESCENTAR }, { timeout: 3000 }));
    await vi.waitFor(() => expect(chamadas.posts).toBe(3));
    expect(chamadas.chaves[2]).toMatch(UUID);
    expect(chamadas.chaves[2]).not.toBe(chamadas.chaves[0]);
  });
});
