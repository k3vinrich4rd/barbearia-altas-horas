import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { POLITICA_PRIVACIDADE } from "@/testes/dados";
import { BFF, delay, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { Cadastro } from "./Cadastro";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

beforeEach(() => {
  zerarNavegacao();
  definirBusca("");
  servidor.use(
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
    http.get(`${BFF}/sessao`, () =>
      HttpResponse.json({ autenticado: true, usuario: { id: "u", nome: "F" }, papeis: ["CLIENTE"] }),
    ),
  );
});

describe("CEP no cadastro", () => {
  test("A33: com a caixinha de região marcada, CEP achado mostra o endereço; 404, 503, 429, rede e demora avisam; em TODOS os casos Criar conta continua habilitado e o corpo leva o cep, a autorização e a versão do texto", async () => {
    const cenarios: { nome: string; resposta: () => Response | Promise<Response>; texto: RegExp }[] = [
      {
        nome: "achou",
        resposta: () =>
          HttpResponse.json({ cep: "01001000", bairro: "Sé", cidade: "São Paulo", uf: "SP", fonte: "VIACEP" }),
        texto: /^Sé, São Paulo - SP$/,
      },
      { nome: "404", resposta: () => erro(404, "CEP_NAO_ENCONTRADO"), texto: /Não encontramos este CEP/ },
      { nome: "503", resposta: () => erro(503, "CEP_INDISPONIVEL"), texto: /o cadastro continua, só sem a sua região/i },
      {
        nome: "429",
        resposta: () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "10" }),
        texto: /o cadastro continua, só sem a sua região/i,
      },
      { nome: "rede", resposta: () => HttpResponse.error(), texto: /o cadastro continua, só sem a sua região/i },
      {
        nome: "demora de 4 s",
        resposta: async () => {
          await delay(4000);
          return HttpResponse.json({ cep: "01001000", bairro: "Sé", cidade: "São Paulo", uf: "SP" });
        },
        texto: /o cadastro continua, só sem a sua região/i,
      },
    ];
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/auth/cadastro`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ sessao: true, usuario: {}, deveTrocarSenha: false }, { status: 201 });
      }),
    );

    for (const c of cenarios) {
      servidor.use(http.get(`${BFF}/cep/01001000`, () => c.resposta()));
      const { user, unmount } = renderizar(<Cadastro />);
      await user.click(await screen.findByRole("checkbox", { name: /Autorizo usar só meu bairro/ }));
      await user.type(screen.getByRole("textbox", { name: "CEP" }), "01001-000");
      await user.tab();
      expect(await screen.findByText(c.texto, {}, { timeout: 6000 }), c.nome).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Criar conta" }), c.nome).toBeEnabled();

      await user.type(screen.getByLabelText("Nome"), "Fulana de Tal");
      await user.type(screen.getByLabelText("E-mail"), "fulana@exemplo.test");
      await user.type(screen.getByLabelText("WhatsApp"), "11900000002");
      await user.type(screen.getByLabelText("Senha"), "senha-bem-longa-123");
      await user.click(screen.getByRole("button", { name: "Criar conta" }));
      await waitFor(() => expect(corpos.length).toBeGreaterThan(0));
      const corpo = corpos.pop()!;
      expect(corpo.cep, c.nome).toBe("01001-000");
      expect(corpo.autorizaRegiao, c.nome).toBe(true);
      expect(corpo.versaoTextoRegiao, c.nome).toBe(POLITICA_PRIVACIDADE.versao);
      expect(Object.keys(corpo).sort(), c.nome).toEqual([
        "aceitaMarketing",
        "autorizaRegiao",
        "cep",
        "email",
        "nome",
        "senha",
        "versaoTextoRegiao",
        "whatsapp",
      ]);
      unmount();
    }
  }, 60000);
});
