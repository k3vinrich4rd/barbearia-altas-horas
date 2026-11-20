import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, barbeiroAdmin, SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  definirBusca("data=2026-11-24");
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

const RESERVA = agendamentoAdmin({ id: "a2", version: 3 });

/** Clica no bloco da reserva na grade e devolve o item (data-estado) e o painel de detalhes ao lado, onde ficam as ações. */
async function escolher(user: ReturnType<typeof renderizar>["user"], nome: RegExp = /Rafael Souza/) {
  const item = (await screen.findByText(nome)).closest('[role="listitem"]') as HTMLElement;
  await user.click(within(item).getByRole("button"));
  return { item, detalhe: screen.getByRole("complementary") };
}

function agenda() {
  servidor.use(
    http.get(`${BFF}/admin/agenda`, () => HttpResponse.json([RESERVA])),
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([barbeiroAdmin()])),
    http.get(`${BFF}/admin/minha-jornada`, () =>
      HttpResponse.json({ barbeiroId: "b", nomeExibicao: "B", intervalos: [] }),
    ),
  );
}

describe("cancelar pela barbearia", () => {
  test("A47: só o ADMIN vê o botão; o diálogo exige o motivo (até 300, com contador), avisa da devolução e manda status, motivo e version; 422 e 403 têm mensagem; sucesso atualiza o item", async () => {
    agenda();
    // BARBEIRO não vê "Cancelar pela barbearia"
    const barbeiro = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    await escolher(barbeiro.user);
    expect(screen.queryByRole("button", { name: /Cancelar pela barbearia/ })).toBeNull();
    expect(screen.getByRole("button", { name: "Concluído" })).toBeInTheDocument(); // o painel abriu: só o cancelar some
    barbeiro.unmount();

    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.patch(`${BFF}/admin/agendamentos/:id/status`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({
          ...RESERVA,
          status: "CANCELADO",
          motivoCancelamento: "Barbeiro com imprevisto",
          version: 4,
        });
      }),
    );
    const { user } = renderizar(<PainelAgenda />, ADMIN);
    const { item, detalhe } = await escolher(user);
    await user.click(within(detalhe).getByRole("button", { name: "Cancelar pela barbearia" }));

    const dialogo = screen.getByRole("dialog");
    expect(dialogo).toHaveTextContent("o valor vai para devolução pendente");
    const confirmar = within(dialogo).getByRole("button", { name: "Confirmar cancelamento" });
    expect(confirmar).toBeDisabled(); // sem motivo o botão não envia
    const campo = within(dialogo).getByLabelText("Motivo do cancelamento");
    expect(within(dialogo).getByText("0/300")).toBeInTheDocument();
    await user.click(campo);
    await user.paste("x".repeat(301));
    expect((campo as HTMLTextAreaElement).value).toHaveLength(300); // o 301º caractere não entra
    expect(within(dialogo).getByText("300/300")).toBeInTheDocument();

    await user.clear(campo);
    await user.type(campo, "Barbeiro com imprevisto");
    await user.click(confirmar);
    await vi.waitFor(() =>
      expect(corpos[0]).toEqual({ status: "CANCELADO", motivo: "Barbeiro com imprevisto", version: 3 }),
    );
    await vi.waitFor(() => expect(item).toHaveAttribute("data-estado", "cancelado"));
    expect(screen.getByRole("complementary")).toHaveTextContent("MotivoBarbeiro com imprevisto");
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(await screen.findByText(/a devolução aparece em Pendências/)).toBeInTheDocument();
  });

  test("A47: 422 MOTIVO_OBRIGATORIO aparece no campo e o 403 do BARBEIRO tem mensagem própria", async () => {
    agenda();
    servidor.use(http.patch(`${BFF}/admin/agendamentos/:id/status`, () => erro(422, "MOTIVO_OBRIGATORIO")));
    const { user, unmount } = renderizar(<PainelAgenda />, ADMIN);
    const { item, detalhe } = await escolher(user);
    await user.click(within(detalhe).getByRole("button", { name: "Cancelar pela barbearia" }));
    await user.type(within(screen.getByRole("dialog")).getByLabelText("Motivo do cancelamento"), "Imprevisto");
    await user.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    const campo = await screen.findByLabelText("Motivo do cancelamento");
    await vi.waitFor(() => expect(campo).toHaveAttribute("aria-invalid", "true"));
    expect(screen.getByText("Informe o motivo do cancelamento.")).toBeInTheDocument();
    unmount();

    servidor.use(http.patch(`${BFF}/admin/agendamentos/:id/status`, () => erro(403, "ACESSO_NEGADO")));
    const outro = renderizar(<PainelAgenda />, ADMIN);
    const { detalhe: detalhe2 } = await escolher(outro.user);
    await outro.user.click(within(detalhe2).getByRole("button", { name: "Cancelar pela barbearia" }));
    await outro.user.type(within(screen.getByRole("dialog")).getByLabelText("Motivo do cancelamento"), "Imprevisto");
    await outro.user.click(screen.getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByText("Só o administrador cancela agendamentos.")).toBeInTheDocument();
  });
});
