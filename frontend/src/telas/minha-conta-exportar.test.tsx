import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { MinhaConta } from "./MinhaConta";

const baixar = vi.fn();
vi.mock("@/api/arquivo", () => ({ baixarArquivo: (...a: unknown[]) => baixar(...a) }));
vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const DADOS = {
  geradoEm: "2026-11-18T11:00:00-03:00",
  conta: { nome: "Cliente Teste", email: "cliente@exemplo.test", whatsapp: "11900000001", endereco: null },
  consentimentos: [],
  agendamentos: [
    {
      id: "a1",
      inicio: "2026-11-24T10:00:00-03:00",
      fim: "2026-11-24T11:00:00-03:00",
      status: "CONFIRMADO",
      servico: "Corte",
      precoCentavos: 5500,
      motivoCancelamento: null,
    },
  ],
};

beforeEach(() => {
  zerarNavegacao();
  baixar.mockReset();
  servidor.use(
    http.get(`${BFF}/me`, () =>
      HttpResponse.json({ id: "u", nome: "Cliente Teste", email: "cliente@exemplo.test", papeis: ["CLIENTE"] }),
    ),
    http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([])),
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
  );
});

describe("exportar dados", () => {
  test("A40: entrega um .json com o corpo da API, sem deixar o conteúdo na tela; 429 mostra a espera", async () => {
    servidor.use(http.get(`${BFF}/me/dados`, () => HttpResponse.json(DADOS)));
    let r = renderizar(<MinhaConta />, CLIENTE);
    await r.user.click(await screen.findByRole("button", { name: "Baixar meus dados" }));
    await waitFor(() => expect(baixar).toHaveBeenCalledTimes(1));
    const [nome, texto] = baixar.mock.calls[0];
    expect(nome).toBe("dados-altas-horas-2026-11-18.json");
    expect(JSON.parse(texto)).toEqual(DADOS);
    expect(document.body.textContent).not.toContain("11900000001"); // o conteúdo não fica na tela
    expect(document.body.innerHTML).not.toContain("Corte</");
    r.unmount();

    servidor.use(http.get(`${BFF}/me/dados`, () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "12" })));
    r = renderizar(<MinhaConta />, CLIENTE);
    await r.user.click(await screen.findByRole("button", { name: "Baixar meus dados" }));
    expect((await screen.findAllByText(/12 segundos|12 s/)).length).toBeGreaterThan(0);
    expect(baixar).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Baixar meus dados" })).toBeDisabled();
  });
});
