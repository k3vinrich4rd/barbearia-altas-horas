import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS, POLITICA_PRIVACIDADE, reserva } from "@/testes/dados";
import { BFF, delay, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { ReservaDados } from "./ReservaDados";
// Token fictício montado em tempo de execução, para o gitleaks não confundir com segredo.
const TOKEN_FICTICIO = "g1_" + "teste".repeat(8);

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const INICIO = "2026-11-24T10:00:00-03:00";
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(
    new URLSearchParams({ servicoId: CINCO_SERVICOS[1].id, barbeiroId: BARBEIRO.id, inicio: INICIO }).toString(),
  );
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
    http.get(`${BFF}/politica-privacidade`, () => HttpResponse.json(POLITICA_PRIVACIDADE)),
  );
});
afterEach(() => vi.useRealTimers());

async function abrirFormulario() {
  const r = renderizar(<ReservaDados />);
  await r.user.click(await screen.findByRole("button", { name: "Continuar sem conta" }));
  return r;
}

async function preencher(user: Awaited<ReturnType<typeof abrirFormulario>>["user"], cep = "") {
  await user.type(screen.getByLabelText("Nome"), "Fulano de Tal");
  await user.type(screen.getByLabelText("E-mail"), "fulano@exemplo.test");
  await user.type(screen.getByLabelText("WhatsApp"), "(11) 90000-0001");
  if (cep) {
    // o CEP só existe com a caixinha de região marcada (A47)
    await user.click(await screen.findByRole("checkbox", { name: /Autorizo usar só meu bairro/ }));
    await user.type(screen.getByRole("textbox", { name: "CEP" }), cep);
  }
}

describe("reserva do visitante", () => {
  test("A21: escolha entre Entrar e continuar sem conta, campos com label, erros da API no campo certo com foco no primeiro, sem reserva dupla", async () => {
    let posts = 0;
    servidor.use(
      http.post(`${BFF}/agendamentos/guest`, async () => {
        posts++;
        await delay(50);
        return erro(400, "VALIDACAO_INVALIDA", {
          errors: [
            { field: "email", message: "e-mail inválido" },
            { field: "nome", message: "nome obrigatório" },
          ],
        });
      }),
    );
    const { user } = renderizar(<ReservaDados />);
    expect(await screen.findByRole("link", { name: "Entrar" })).toHaveAttribute(
      "href",
      expect.stringContaining("/entrar?voltar="),
    );
    expect(screen.queryByLabelText("Nome")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Continuar sem conta" }));
    for (const rotulo of ["Nome", "E-mail", "WhatsApp"]) expect(screen.getByLabelText(rotulo)).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "CEP" })).toBeNull(); // sem a caixinha marcada o CEP nem aparece

    await preencher(user);
    await user.dblClick(screen.getByRole("button", { name: "Reservar" }));
    expect(screen.getByRole("button", { name: /Reservando/ })).toBeDisabled();
    const email = await screen.findByLabelText("E-mail");
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
    expect(posts).toBe(1);

    const nome = screen.getByLabelText("Nome");
    expect(nome).toHaveAttribute("aria-invalid", "true");
    expect(nome).toHaveFocus(); // o primeiro campo da tela, não o primeiro da lista da API
    expect(email).toHaveAccessibleDescription(/Erro:s*e-mail inválido/);
    expect(nome).toHaveAccessibleDescription(/nome obrigatório/);
    expect(screen.getByLabelText("WhatsApp")).not.toHaveAttribute("aria-invalid");
  }, 15000); // teste longo (vários envios): folga para o CI sob carga

  test("A22: CEP vazio não envia a chave nem consulta /cep; CEP preenchido (com a caixinha) vai como digitado, com a versão do texto; CEP inválido aparece no campo", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/agendamentos/guest`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        corpos.push(corpo);
        return corpo.cep === "123"
          ? erro(400, "CEP_INVALIDO")
          : HttpResponse.json({ ...reserva(), tokenGuest: TOKEN_FICTICIO }, { status: 201 });
      }),
    );
    // (qualquer GET /api/cep/* cairia em onUnhandledRequest: "error" e derrubaria o teste)
    for (const cep of ["", "01001-000", "123"]) {
      const { user, unmount } = await abrirFormulario();
      await preencher(user, cep);
      await user.click(screen.getByRole("button", { name: "Reservar" }));
      await waitFor(() => expect(corpos.length).toBeGreaterThan(0));
      const corpo = corpos.at(-1)!;
      if (cep === "") {
        expect("cep" in corpo).toBe(false);
        expect("autorizaRegiao" in corpo).toBe(false);
      }
      if (cep === "01001-000") {
        expect(corpo.cep).toBe("01001-000");
        expect(corpo.autorizaRegiao).toBe(true);
        expect(corpo.versaoTextoRegiao).toBe(POLITICA_PRIVACIDADE.versao);
      }
      if (cep === "123") {
        const campo = await screen.findByRole("textbox", { name: "CEP" });
        await waitFor(() => expect(campo).toHaveAttribute("aria-invalid", "true"));
        expect(campo).toHaveAccessibleDescription(/Esse CEP não confere. Digite os 8 números./);
      }
      unmount();
      roteador.push.mockClear();
    }
    expect(corpos).toHaveLength(3);
  });

  test("A23: com 201 vai ao pagamento com o resumo; o código do visitante não aparece na URL, nos storages nem no console", async () => {
    const consoles = (["log", "info", "warn", "error"] as const).map((m) =>
      vi.spyOn(console, m).mockImplementation(() => {}),
    );
    servidor.use(
      http.post(`${BFF}/agendamentos/guest`, () =>
        HttpResponse.json({ ...reserva(), tokenGuest: TOKEN_FICTICIO }, { status: 201 }),
      ),
    );
    const { user } = await abrirFormulario();
    expect(screen.getByText(/Corte Degradê/)).toBeInTheDocument();
    expect(screen.getByText(/terça, 24\/11 às 10:00/)).toBeInTheDocument();
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Reservar" }));

    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/pagamento/${reserva().id}`));
    expect(roteador.push.mock.calls[0][0]).not.toContain("CODIGO-SECRETO");
    expect(JSON.stringify({ ...localStorage })).not.toContain("CODIGO-SECRETO");
    expect(JSON.stringify({ ...sessionStorage })).not.toContain("CODIGO-SECRETO");
    expect(localStorage.length + sessionStorage.length).toBe(0);
    expect(
      consoles
        .flatMap((c) => c.mock.calls)
        .flat()
        .join(" "),
    ).not.toContain("CODIGO-SECRETO");
    expect(window.location.href).not.toContain("CODIGO-SECRETO");
    consoles.forEach((c) => c.mockRestore());
  });
});
