import { screen } from "@testing-library/react";
import QRCode from "qrcode";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio } from "@/testes/render";
import { api, cobrancaPix, montar, preencher } from "@/testes/pagamento";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("qrcode", () => ({ default: { toDataURL: vi.fn(async (t: string) => `data:image/png;base64,GERADO-${t.length}`) } }));
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  vi.mocked(QRCode.toDataURL).mockClear();
  api();
});
afterEach(() => vi.useRealTimers());

const responder = (cobranca: object) => servidor.use(http.post(`${BFF}/pagamentos/cobrancas`, () => HttpResponse.json(cobranca, { status: 201 })));

describe("Pix e cartão", () => {
  test("A28: Pix gera o QR no navegador a partir do copia e cola, usa a imagem da API quando vier e o cartão só leva ao checkout, sem campo de cartão", async () => {
    // Pix simulado: qrCodeBase64 nulo → QR gerado no navegador
    responder(cobrancaPix());
    let { user, unmount } = montar();
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    const texto = await screen.findByLabelText("Pix copia e cola");
    expect(texto).toHaveValue("SIMULADO-0001-ABCDEF");
    const qr = await screen.findByRole("img", { name: /QR Code do Pix/ });
    expect(qr).toHaveAttribute("src", expect.stringMatching(/^data:image\/png;base64,GERADO-/));
    expect(QRCode.toDataURL).toHaveBeenCalledWith("SIMULADO-0001-ABCDEF", expect.anything());
    await user.click(screen.getByRole("button", { name: "Copiar" }));
    expect(await screen.findByText("Copiado")).toBeInTheDocument();
    expect(await navigator.clipboard.readText()).toBe("SIMULADO-0001-ABCDEF");
    unmount();

    // API mandou a imagem: usa a imagem e não gera outra
    vi.mocked(QRCode.toDataURL).mockClear();
    responder(cobrancaPix({ pix: { copiaECola: "000201-REAL", qrCodeBase64: "AAAA", expiraEm: "2026-11-18T08:03:00-03:00" } }));
    ({ user, unmount } = montar());
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    const imagem = await screen.findByRole("img", { name: /QR Code do Pix/ });
    expect(imagem).toHaveAttribute("src", "data:image/png;base64,AAAA");
    expect(QRCode.toDataURL).not.toHaveBeenCalled();
    unmount();

    // cartão: um único botão para o checkout hospedado; nenhum campo de cartão em lugar nenhum
    responder(cobrancaPix({ metodo: "CARTAO_CREDITO", pix: null, cartao: { urlCheckout: "https://checkout.exemplo.test/pagar/abc", expiraEm: "2026-11-18T08:03:00-03:00" } }));
    ({ user, unmount } = montar());
    await preencher(user);
    await user.click(screen.getByLabelText("Cartão de crédito"));
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    const ir = await screen.findByRole("link", { name: /pagamento com cartão/ });
    expect(ir).toHaveAttribute("href", "https://checkout.exemplo.test/pagar/abc");
    expect(screen.queryByLabelText(/número do cartão|cvv|validade/i)).toBeNull();
    expect(document.querySelector('input[autocomplete^="cc-"]')).toBeNull();
    expect(screen.queryByRole("img", { name: /QR Code/ })).toBeNull();
    unmount();
  });
});
