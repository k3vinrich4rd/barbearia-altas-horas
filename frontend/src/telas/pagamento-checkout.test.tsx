import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { CPF_DE_TESTE, POLITICA_PAGAMENTO } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio } from "@/testes/render";
import { api, cobrancaPix, montar, preencher } from "@/testes/pagamento";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
vi.mock("qrcode", () => ({ default: { toDataURL: vi.fn(async () => "data:image/png;base64,QR") } }));
ligarServidor();

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

describe("checkout", () => {
  test("A26: política inteira, caixa desmarcada, Pagar só depois de aceitar, versão vinda da API, CPF validado antes de enviar, sem cancelar nem remarcar", async () => {
    api({ politica: { versao: 7, texto: POLITICA_PAGAMENTO.texto } });
    let corpo: Record<string, unknown> | undefined;
    servidor.use(
      http.post(`${BFF}/pagamentos/cobrancas`, async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(cobrancaPix(), { status: 201 });
      }),
    );
    const { user } = montar();

    expect(await screen.findByText(POLITICA_PAGAMENTO.texto)).toBeInTheDocument();
    const caixa = screen.getByLabelText(/Li e aceito/);
    expect(caixa).not.toBeChecked();
    const pagar = screen.getByRole("button", { name: "Pagar" });
    expect(pagar).toBeDisabled();
    await user.click(caixa);
    expect(pagar).toBeEnabled();

    await user.type(screen.getByLabelText("CPF"), "111.111.111-11");
    await user.click(pagar);
    expect(await screen.findByText(/CPF inválido/)).toBeInTheDocument();
    expect(screen.getByLabelText("CPF")).toHaveAttribute("aria-invalid", "true");
    expect(corpo).toBeUndefined();

    await user.clear(screen.getByLabelText("CPF"));
    await user.type(screen.getByLabelText("CPF"), CPF_DE_TESTE);
    await user.click(pagar);
    await waitFor(() => expect(corpo).toBeDefined());
    expect(corpo).toMatchObject({ aceitePolitica: true, versaoPolitica: 7, metodo: "PIX", cpf: CPF_DE_TESTE });
    expect(screen.queryByRole("button", { name: /cancelar|remarcar/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /cancelar|remarcar/i })).toBeNull();
  });

  test("A27: mesma chave de idempotência na repetição, 200 vale como sucesso, em andamento leva ao acompanhamento, erro definitivo troca a chave e o CPF não sobra", async () => {
    api();
    const chaves: string[] = [];
    let modo: "rede" | "ok" | "andamento" | "200" | "conflito" = "rede";
    servidor.use(
      http.post(`${BFF}/pagamentos/cobrancas`, ({ request }) => {
        chaves.push(request.headers.get("x-idempotency-key") ?? "");
        if (modo === "rede") return HttpResponse.error();
        if (modo === "andamento") return erro(409, "COBRANCA_EM_ANDAMENTO");
        if (modo === "conflito") return erro(422, "IDEMPOTENCIA_CONFLITO");
        if (modo === "200") return HttpResponse.json(cobrancaPix({ cpfMascarado: undefined }), { status: 200 });
        return HttpResponse.json(cobrancaPix(), { status: 201 });
      }),
    );

    // falha de rede e novo clique: a MESMA chave; depois sai e o CPF some de tudo
    let { user, unmount } = montar();
    await preencher(user);
    const pagar = screen.getByRole("button", { name: "Pagar" });
    await user.dblClick(pagar); // duplo clique: um envio só
    await waitFor(() => expect(chaves.length).toBeGreaterThanOrEqual(1));
    expect(await screen.findByText(/servidor está acordando/i)).toBeInTheDocument();
    modo = "200"; // a repetição devolve 200 (sem cpfMascarado) e é tratada como sucesso
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    expect(await screen.findByLabelText("Pix copia e cola")).toHaveValue("SIMULADO-0001-ABCDEF");
    expect(chaves.length).toBeGreaterThanOrEqual(2);
    expect(chaves[0]).toMatch(UUID_V4);
    expect(new Set(chaves).size).toBe(1); // duplo clique, falha de rede e repetição: sempre a MESMA chave
    expect(document.body.innerHTML.replace(/\D/g, "")).not.toContain("52998224725");
    expect(localStorage.length + sessionStorage.length).toBe(0);
    expect(window.location.href).not.toContain("52998224725");
    unmount();

    // já existe pagamento em andamento
    modo = "andamento";
    ({ user, unmount } = montar());
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    expect(await screen.findByText(/já existe um pagamento em andamento/i)).toBeInTheDocument();
    unmount();

    // erro definitivo: a próxima tentativa usa chave NOVA
    chaves.length = 0;
    modo = "conflito";
    ({ user, unmount } = montar());
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    expect(await screen.findByText(/Comece de novo/)).toBeInTheDocument();
    modo = "ok";
    await user.click(screen.getByRole("button", { name: "Pagar" }));
    await screen.findByLabelText("Pix copia e cola");
    expect(chaves).toHaveLength(2);
    expect(chaves[1]).not.toBe(chaves[0]);
    expect(chaves[1]).toMatch(UUID_V4);
    unmount();
  });
});
