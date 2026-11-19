import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { MinhaConta } from "./MinhaConta";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const consentimento = (aceito: boolean, versaoTexto = "1.0") => ({
  finalidade: "MARKETING_CEP",
  aceito,
  versaoTexto,
  registradoEm: "2026-11-10T12:00:00-03:00",
});
const perfil = { id: "u", nome: "Cliente Teste", email: "cliente@exemplo.test", papeis: ["CLIENTE"] };

beforeEach(() => {
  zerarNavegacao();
  servidor.use(http.get(`${BFF}/me`, () => HttpResponse.json(perfil)));
});

describe("consentimento", () => {
  test("A39: mostra o estado atual (lista vazia = não aceito), manda a versão VIGENTE e repete uma vez se a versão mudou", async () => {
    // lista vazia = não aceito
    servidor.use(
      http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([])),
      http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json({ ...POLITICA_PRIVACIDADE, versao: "1.1" })),
    );
    const bodies: Record<string, unknown>[] = [];
    servidor.use(
      http.put(`${BFF}/me/consentimentos/marketing`, async ({ request }) => {
        const b = (await request.json()) as Record<string, unknown>;
        bodies.push(b);
        return HttpResponse.json(consentimento(b.aceito as boolean, b.versaoTexto as string));
      }),
    );
    let r = renderizar(<MinhaConta />, CLIENTE);
    const caixa = await screen.findByRole("checkbox", { name: /Aceito receber ofertas/ });
    await waitFor(() => expect(caixa).toBeEnabled());
    expect(caixa).not.toBeChecked();
    expect(screen.getByText("Marketing: não aceito.")).toBeInTheDocument();
    await r.user.click(caixa);
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toEqual({ aceito: true, versaoTexto: "1.1" }); // a vigente da política, não a antiga
    expect(await screen.findByText("Marketing: aceito.")).toBeInTheDocument();
    expect(screen.getAllByRole("status").some((s) => s.textContent === "Marketing: aceito.")).toBe(true);

    // revogar não pede nada além do clique
    await r.user.click(screen.getByRole("checkbox"));
    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies[1].aceito).toBe(false);
    expect(await screen.findByText("Marketing: não aceito.")).toBeInTheDocument();
    r.unmount();

    // estado atual aceito (versão 1.0) e a política mudou para 1.1 entre a leitura e o clique: 400 → busca de novo e tenta UMA vez
    let versaoDaPolitica = "1.0";
    let puts = 0;
    servidor.use(
      http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([consentimento(true)])),
      http.get(`${BFF}/politica-privacidade`, () =>
        HttpResponse.json({ ...POLITICA_PRIVACIDADE, versao: versaoDaPolitica }),
      ),
      http.put(`${BFF}/me/consentimentos/marketing`, async ({ request }) => {
        puts++;
        const b = (await request.json()) as { aceito: boolean; versaoTexto: string };
        return b.versaoTexto === "1.1"
          ? HttpResponse.json(consentimento(b.aceito, "1.1"))
          : erro(400, "VALIDACAO_INVALIDA", { errors: [{ field: "versaoTexto", message: "versão antiga" }] });
      }),
    );
    r = renderizar(<MinhaConta />, CLIENTE);
    const marcada = await screen.findByRole("checkbox", { name: /Aceito receber ofertas/ });
    await waitFor(() => expect(marcada).toBeChecked());
    expect(screen.getByText("Marketing: aceito.")).toBeInTheDocument();
    versaoDaPolitica = "1.1";
    await r.user.click(marcada);
    await waitFor(() => expect(puts).toBe(2)); // a 1ª com 1.0 (400) e UMA repetição com 1.1
    expect(await screen.findByText("Marketing: não aceito.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
