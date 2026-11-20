import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, delay, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, SESSAO_BARBEIRO } from "@/testes/painel";
import { fixarRelogio, renderizar } from "@/testes/render";
import { PainelAgenda } from "./PainelAgenda";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  definirBusca("data=2026-11-24");
  fixarRelogio("2026-11-24T12:00:00-03:00"); // o atendimento das 10:00 já começou
});
afterEach(() => vi.useRealTimers());

const RESERVA = agendamentoAdmin({ id: "a2", version: 3 });

/** Clica no bloco da reserva na grade e devolve o item (data-estado) e o painel de detalhes ao lado, onde ficam as ações. */
async function escolher(user: ReturnType<typeof renderizar>["user"], nome: RegExp = /Rafael Souza/) {
  const item = (await screen.findByText(nome)).closest('[role="listitem"]') as HTMLElement;
  await user.click(within(item).getByRole("button"));
  return { item, detalhe: screen.getByRole("complementary") };
}

function agenda(leituras: { n: number }) {
  servidor.use(
    http.get(`${BFF}/admin/agenda`, () => {
      leituras.n++;
      return HttpResponse.json([RESERVA]);
    }),
    http.get(`${BFF}/admin/minha-jornada`, () =>
      HttpResponse.json({ barbeiroId: "b", nomeExibicao: "B", intervalos: [] }),
    ),
  );
}

describe("fechar atendimento", () => {
  test("A46: Concluído e Não compareceu mandam status e version; sucesso atualiza só o item e anuncia; TRANSICAO_INVALIDA e CONFLITO_DE_VERSAO têm texto; falta avisa que o valor fica retido; botões travam durante o envio", async () => {
    const leituras = { n: 0 };
    agenda(leituras);
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.patch(`${BFF}/admin/agendamentos/:id/status`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        await delay(50);
        return HttpResponse.json({ ...RESERVA, status: "CONCLUIDO", version: 4 });
      }),
    );
    const { user, unmount } = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const { item, detalhe } = await escolher(user);

    // sucesso: PATCH com status e version = 3; durante o envio os botões ficam desabilitados
    await user.click(within(detalhe).getByRole("button", { name: "Concluído" }));
    expect(within(detalhe).getByRole("button", { name: "Não compareceu" })).toBeDisabled();
    expect(await screen.findByText("Atendimento concluído.")).toBeInTheDocument();
    expect(screen.getByText("Atendimento concluído.").closest("[role=status]")).not.toBeNull();
    expect(corpos[0]).toEqual({ status: "CONCLUIDO", version: 3 });
    await vi.waitFor(() => expect(item).toHaveAttribute("data-estado", "concluido"));
    expect(within(screen.getByRole("complementary")).queryByRole("button", { name: "Concluído" })).toBeNull();
    expect(leituras.n).toBe(1); // só o item mudou: a agenda não foi lida de novo
    unmount();

    // antes do início: 422 TRANSICAO_INVALIDA vira texto claro
    servidor.use(http.patch(`${BFF}/admin/agendamentos/:id/status`, () => erro(422, "TRANSICAO_INVALIDA")));
    const segundo = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const { detalhe: detalhe2 } = await escolher(segundo.user);
    await segundo.user.click(within(detalhe2).getByRole("button", { name: "Concluído" }));
    expect(await screen.findByText("Só dá para fechar o atendimento depois do horário de início.")).toBeInTheDocument();
    segundo.unmount();

    // outro atendimento mexeu antes: 409 recarrega a agenda e pede para tentar de novo
    const leiturasConflito = { n: 0 };
    agenda(leiturasConflito);
    servidor.use(http.patch(`${BFF}/admin/agendamentos/:id/status`, () => erro(409, "CONFLITO_DE_VERSAO")));
    const terceiro = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const { detalhe: detalhe3 } = await escolher(terceiro.user);
    await terceiro.user.click(within(detalhe3).getByRole("button", { name: "Concluído" }));
    expect(await screen.findByText(/Alguém mexeu neste agendamento antes de você/)).toBeInTheDocument();
    await vi.waitFor(() => expect(leiturasConflito.n).toBe(2));
    terceiro.unmount();

    // Não compareceu: confirma antes, avisando que o valor pago fica retido (R19)
    const faltas: Record<string, unknown>[] = [];
    agenda({ n: 0 });
    servidor.use(
      http.patch(`${BFF}/admin/agendamentos/:id/status`, async ({ request }) => {
        faltas.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ ...RESERVA, status: "NAO_COMPARECEU", version: 4 });
      }),
    );
    const quarto = renderizar(<PainelAgenda />, SESSAO_BARBEIRO);
    const { item: item4, detalhe: detalhe4 } = await escolher(quarto.user);
    await quarto.user.click(within(detalhe4).getByRole("button", { name: "Não compareceu" }));
    const dialogo = screen.getByRole("dialog");
    expect(dialogo).toHaveTextContent("O valor pago fica retido");
    expect(faltas).toHaveLength(0);
    await quarto.user.click(within(dialogo).getByRole("button", { name: "Confirmar falta" }));
    await vi.waitFor(() => expect(faltas[0]).toEqual({ status: "NAO_COMPARECEU", version: 3 }));
    await vi.waitFor(() => expect(item4).toHaveAttribute("data-estado", "nao-compareceu"));
    expect(item4).toHaveTextContent("Não compareceu");
  });
});
