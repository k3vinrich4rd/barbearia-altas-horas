import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { roteador, zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, SESSAO_BARBEIRO } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelReservaManual } from "./PainelReservaManual";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/admin/minha-jornada`, () =>
      HttpResponse.json({ barbeiroId: BARBEIRO.id, nomeExibicao: BARBEIRO.nomeExibicao, intervalos: [] }),
    ),
  );
});
afterEach(() => vi.useRealTimers());

async function preencher(user: ReturnType<typeof renderizar>["user"], admin: boolean) {
  await screen.findByLabelText("Serviço");
  await user.selectOptions(screen.getByLabelText("Serviço"), CINCO_SERVICOS[0].id);
  if (admin) await user.selectOptions(screen.getByLabelText("Barbeiro"), BARBEIRO.id);
  await user.type(screen.getByLabelText("Dia"), "2026-11-24");
  await user.type(screen.getByLabelText("Horário"), "15:00");
}

describe("reserva manual", () => {
  test("A62: ADMIN escolhe o barbeiro, BARBEIRO só vê o próprio; o corpo leva serviço, barbeiro, início com o fuso da barbearia, nome e observação opcionais; avisa para não digitar telefone nem documento; criar leva à agenda do dia", async () => {
    const corpos: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/barbeiros`, () =>
        HttpResponse.json([BARBEIRO, { ...BARBEIRO, id: "b2", nomeExibicao: "Rafa" }]),
      ),
      http.post(`${BFF}/admin/agendamentos`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json(agendamentoAdmin({ origem: "MANUAL" }), { status: 201 });
      }),
    );
    // BARBEIRO: só o próprio, sem lista dos outros
    const barbeiro = renderizar(<PainelReservaManual />, SESSAO_BARBEIRO);
    await screen.findByLabelText("Barbeiro");
    expect(screen.getAllByRole("option", { name: "Carlos Eduardo" })).toHaveLength(1);
    expect(screen.queryByRole("option", { name: "Rafa" })).toBeNull();
    barbeiro.unmount();

    const { user } = renderizar(<PainelReservaManual />, ADMIN);
    expect(await screen.findByRole("option", { name: "Rafa" })).toBeInTheDocument();
    expect(screen.getByText(/Não digite telefone nem documento/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/whatsapp|e-mail|cpf/i)).toBeNull(); // a API não guarda contato do cliente
    await preencher(user, true);
    await user.type(screen.getByLabelText("Nome do cliente (opcional)"), "Seu Jorge");
    await user.type(screen.getByLabelText("Observação (opcional)"), "Combinado pelo WhatsApp.");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    await vi.waitFor(() =>
      expect(corpos[0]).toEqual({
        servicoId: CINCO_SERVICOS[0].id,
        barbeiroId: BARBEIRO.id,
        inicio: "2026-11-24T15:00:00-03:00",
        nomeCliente: "Seu Jorge",
        observacao: "Combinado pelo WhatsApp.",
      }),
    );
    // a agenda do dia da reserva abre e se recarrega sozinha
    await vi.waitFor(() => expect(roteador.push).toHaveBeenCalledWith("/painel?data=2026-11-24"));
  });

  test("A62: sem nome nem observação o corpo não leva as chaves; 409, 422 e 404 aparecem com texto; campos obrigatórios são conferidos antes", async () => {
    const corpos: Record<string, unknown>[] = [];
    let resposta: () => Response = () => erro(409, "HORARIO_INDISPONIVEL");
    servidor.use(
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
      http.post(`${BFF}/admin/agendamentos`, async ({ request }) => {
        corpos.push((await request.json()) as Record<string, unknown>);
        return resposta();
      }),
    );
    const { user } = renderizar(<PainelReservaManual />, ADMIN);
    await screen.findByLabelText("Serviço");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(screen.getByText("Escolha o serviço.")).toBeInTheDocument();
    expect(screen.getByText("Escolha o barbeiro.")).toBeInTheDocument();
    expect(screen.getByText("Informe o dia.")).toBeInTheDocument();
    expect(screen.getByText("Informe o horário.")).toBeInTheDocument();
    expect(corpos).toHaveLength(0);

    await preencher(user, true);
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText(/Este horário já está ocupado/)).toBeInTheDocument();
    expect(corpos[0]).toEqual({
      servicoId: CINCO_SERVICOS[0].id,
      barbeiroId: BARBEIRO.id,
      inicio: "2026-11-24T15:00:00-03:00",
    });
    expect(screen.getByLabelText("Horário")).toHaveAttribute("aria-invalid", "true");
    expect(roteador.push).not.toHaveBeenCalled();

    resposta = () => erro(422, "ANTECEDENCIA_INSUFICIENTE");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText(/Este horário já passou/)).toBeInTheDocument();

    resposta = () => erro(422, "FORA_DO_EXPEDIENTE");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText(/não atende neste horário/)).toBeInTheDocument();

    // A62: 18:18 (fora da grade) mostra o que a API explicou: o passo da grade e a jornada do dia
    resposta = () =>
      erro(422, "FORA_DO_EXPEDIENTE", {
        detail:
          "Este horário não está na grade: os inícios são de 30 em 30 minutos a partir do começo de cada turno. Jornada do barbeiro neste dia: 07:00 às 12:00 e 13:00 às 20:00.",
      });
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText(/de 30 em 30 minutos.*07:00 às 12:00 e 13:00 às 20:00/)).toBeInTheDocument();

    resposta = () => erro(422, "SERVICO_INATIVO");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText("Este serviço não está disponível agora. Escolha outro.")).toBeInTheDocument();

    resposta = () => erro(404, "RECURSO_NAO_ENCONTRADO");
    await user.click(screen.getByRole("button", { name: "Criar reserva" }));
    expect(await screen.findByText("Não encontramos o que você procura. Volte para o início.")).toBeInTheDocument();
  });
});
