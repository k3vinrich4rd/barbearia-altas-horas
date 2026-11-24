import { screen, waitFor } from "@testing-library/react";
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { api, chamar } from "@/api/cliente";
import { proxy } from "@/proxy";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { CLIENTE, renderizar } from "@/testes/render";
import { TrocarSenha } from "./TrocarSenha";

const irPara = vi.fn();
vi.mock("@/api/navegacao", () => ({ irPara: (d: string) => irPara(d) }));
vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  irPara.mockReset();
  servidor.use(
    http.get(`${BFF}/me`, () =>
      HttpResponse.json({ id: "u", nome: "Admin", email: "a@exemplo.test", papeis: ["ADMIN"], deveTrocarSenha: true }),
    ),
    http.get(`${BFF}/sessao`, () => HttpResponse.json({ autenticado: false })),
  );
});

describe("troca da senha inicial", () => {
  test("A43: bloqueia toda navegação até trocar; valida 10 a 72 bytes; 204 pede novo login; 403 de outra rota leva à troca", async () => {
    // toda navegação (menos a troca) é desviada enquanto vale o cookie de senha inicial
    const sessao = "ah_sessao=1; ah_troca=1";
    for (const caminho of ["/", "/meus-agendamentos", "/painel", "/agendar"]) {
      const r = proxy(new NextRequest(`http://localhost:3000${caminho}`, { headers: { cookie: sessao } }));
      expect(new URL(r.headers.get("location")!).pathname, caminho).toBe("/conta/trocar-senha");
    }
    expect(
      proxy(new NextRequest("http://localhost:3000/conta/trocar-senha", { headers: { cookie: sessao } })).headers.get(
        "location",
      ),
    ).toBeNull();

    // tela: senha curta não envia; erra a atual (401); acerta
    const corpos: Record<string, unknown>[] = [];
    let resposta: () => Response = () => erro(401, "CREDENCIAIS_INVALIDAS");
    servidor.use(
      http.put(`${BFF}/me/senha`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return resposta();
      }),
    );
    const { user } = renderizar(<TrocarSenha />, CLIENTE);
    expect(await screen.findByText("Antes de continuar, troque a senha inicial.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Senha atual"), "senha-inicial-123");
    await user.type(screen.getByLabelText("Nova senha"), "curta");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(await screen.findByText(/de 10 a 72 caracteres \(acentos/)).toBeInTheDocument();
    expect(corpos).toHaveLength(0);

    await user.clear(screen.getByLabelText("Senha atual"));
    await user.clear(screen.getByLabelText("Nova senha"));
    await user.type(screen.getByLabelText("Senha atual"), "senha-inicial-123");
    await user.type(screen.getByLabelText("Nova senha"), "uma-senha-nova-bem-longa");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(
      await screen.findByText("E-mail ou senha não conferem. Confira e tente de novo; o botão Mostrar a senha ajuda."),
    ).toBeInTheDocument();
    expect(corpos[0]).toEqual({ senhaAtual: "senha-inicial-123", novaSenha: "uma-senha-nova-bem-longa" });

    resposta = () => new HttpResponse(null, { status: 204 });
    await user.type(screen.getByLabelText("Senha atual"), "senha-inicial-123");
    await user.type(screen.getByLabelText("Nova senha"), "uma-senha-nova-bem-longa");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/entrar?aviso=senha-trocada"));

    // 403 TROCA_DE_SENHA_OBRIGATORIA em qualquer outra chamada leva à tela de troca
    servidor.use(http.get(`${BFF}/me/agendamentos`, () => erro(403, "TROCA_DE_SENHA_OBRIGATORIA")));
    await expect(chamar(api().GET("/me/agendamentos"))).rejects.toMatchObject({ code: "TROCA_DE_SENHA_OBRIGATORIA" });
    expect(irPara).toHaveBeenCalledWith("/conta/trocar-senha");
  });
});
