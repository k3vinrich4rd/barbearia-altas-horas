import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { MinhaConta } from "./MinhaConta";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const PERFIL = {
  id: "u1",
  nome: "Cliente Teste",
  email: "c@exemplo.test",
  papeis: ["CLIENTE"],
  regiaoAutorizada: true,
  endereco: { cep: null, bairro: "Centro", cidade: "Osasco", uf: "SP", completo: true },
};

describe("meus dados", () => {
  test("A49: o consentimento de região aparece ao lado do de marketing, com a localidade, e a revogação pede uma confirmação em uma linha e manda a versão vigente", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/me`, () => HttpResponse.json(PERFIL)),
      http.get(`${BFF}/me/consentimentos`, () =>
        HttpResponse.json([
          { finalidade: "MARKETING_CEP", aceito: false, versaoTexto: "1.0", registradoEm: "2026-11-20T09:00:00-03:00" },
          { finalidade: "REGIAO_ALCANCE", aceito: true, versaoTexto: "1.0", registradoEm: "2026-11-23T09:00:00-03:00" },
        ]),
      ),
      http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
      http.put(`${BFF}/me/consentimentos/regiao`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        corpos.push(corpo);
        return HttpResponse.json({
          finalidade: "REGIAO_ALCANCE",
          aceito: false,
          versaoTexto: corpo.versaoTexto,
          registradoEm: "2026-11-23T10:00:00-03:00",
        });
      }),
    );
    const { user } = renderizar(<MinhaConta />, CLIENTE);
    expect(await screen.findByRole("heading", { name: "Ofertas e novidades" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Minha região" })).toBeInTheDocument();
    expect(screen.getByText("Região: autorizada (Centro, Osasco - SP).")).toBeInTheDocument();
    expect(await screen.findByText("Marketing: não aceito.")).toBeInTheDocument();

    // a revogação pede uma linha de confirmação; "Manter" volta atrás sem chamar a API
    await user.click(screen.getByRole("button", { name: "Retirar minha autorização de região" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Retirar apaga a sua região do perfil e das suas reservas.");
    await user.click(screen.getByRole("button", { name: "Manter" }));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(corpos).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Retirar minha autorização de região" }));
    await user.click(screen.getByRole("button", { name: "Retirar" }));
    await waitFor(() => expect(corpos).toHaveLength(1));
    expect(corpos[0]).toEqual({ aceito: false, versaoTexto: POLITICA_PRIVACIDADE.versao });
    expect(await screen.findByText("Região: não autorizada.")).toBeInTheDocument();
    // sem região, a caixinha volta desmarcada para quem quiser autorizar de novo
    expect(screen.getByRole("checkbox", { name: /Autorizo usar só meu bairro/ })).not.toBeChecked();
  });

  test("A49: erro ao retirar aparece como alerta, sem mudar o estado", async () => {
    servidor.use(
      http.get(`${BFF}/me`, () => HttpResponse.json(PERFIL)),
      http.get(`${BFF}/me/consentimentos`, () => HttpResponse.json([])),
      http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
      http.put(`${BFF}/me/consentimentos/regiao`, () => new HttpResponse(null, { status: 500 })),
    );
    const { user } = renderizar(<MinhaConta />, CLIENTE);
    await user.click(await screen.findByRole("button", { name: "Retirar minha autorização de região" }));
    await user.click(screen.getByRole("button", { name: "Retirar" }));
    await waitFor(() => expect(screen.getAllByRole("alert").length).toBeGreaterThan(1));
    expect(screen.getByText(/Região: autorizada/)).toBeInTheDocument();
  });
});
