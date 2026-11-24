import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS, reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";
import { ReservaDados } from "./ReservaDados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const INICIO = "2026-11-24T10:00:00-03:00";
const DADOS = new URLSearchParams({
  servicoId: CINCO_SERVICOS[1].id,
  barbeiroId: BARBEIRO.id,
  inicio: INICIO,
}).toString();

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(DADOS);
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
  );
});
afterEach(() => vi.useRealTimers());

async function reservar() {
  const r = renderizar(<ReservaDados />);
  await r.user.click(await screen.findByRole("button", { name: "Continuar sem conta" }));
  await r.user.type(screen.getByLabelText("Nome"), "Fulano");
  await r.user.type(screen.getByLabelText("E-mail"), "fulano@exemplo.test");
  await r.user.type(screen.getByLabelText("WhatsApp"), "11900000001");
  await r.user.click(screen.getByRole("button", { name: "Reservar" }));
  return r;
}

describe("conflitos de reserva", () => {
  test("A24: horário ocupado some da lista e a disponibilidade recarrega; conflito concorrente repete UMA vez; antecedência mostra a regra de 2 h", async () => {
    // 1) HORARIO_INDISPONIVEL: volta à escolha com o horário marcado como ocupado
    servidor.use(http.post(`${BFF}/agendamentos/guest`, () => erro(409, "HORARIO_INDISPONIVEL")));
    const primeira = await reservar();
    await waitFor(() => expect(roteador.replace).toHaveBeenCalled());
    const volta = new URL(roteador.replace.mock.calls[0][0], "http://x");
    expect(volta.pathname).toBe("/agendar");
    expect(volta.searchParams.get("ocupado")).toBe(INICIO);
    expect(volta.searchParams.get("data")).toBe("2026-11-24");
    primeira.unmount();

    //    e a tela de escolha recarrega a disponibilidade, sem o horário que acabou de ser tomado
    let leituras = 0;
    servidor.use(
      http.get(`${BFF}/disponibilidade`, () => {
        leituras++;
        // a API (cache de leitura) ainda lista o horário velho: a tela precisa escondê-lo
        return HttpResponse.json({
          data: "2026-11-24",
          fuso: "America/Sao_Paulo",
          servicoId: CINCO_SERVICOS[1].id,
          duracaoMin: 60,
          barbeiros: [
            {
              barbeiroId: BARBEIRO.id,
              nomeExibicao: "Carlos Eduardo",
              horarios: [INICIO, "2026-11-24T11:00:00-03:00"],
            },
          ],
        });
      }),
    );
    definirBusca(volta.searchParams.toString());
    renderizar(<Agendar />);
    expect(await screen.findByRole("button", { name: "11:00" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "10:00" })).toBeNull();
    expect(
      screen.getByText(/Esse horário acabou de ser reservado por outra pessoa. Escolha outro./),
    ).toBeInTheDocument();
    expect(leituras).toBeGreaterThanOrEqual(1); // o dia escolhido e a tira de 7 dias (Fatia 12C) consultam a disponibilidade
    (await import("@testing-library/react")).cleanup();

    // 2) CONFLITO_CONCORRENTE duas vezes: a MESMA requisição, uma segunda vez, e então "tente de novo"
    definirBusca(DADOS);
    const corpos: string[] = [];
    servidor.use(
      http.post(`${BFF}/agendamentos/guest`, async ({ request }) => {
        corpos.push(await request.text());
        return erro(409, "CONFLITO_CONCORRENTE");
      }),
    );
    await reservar();
    expect(await screen.findByText("Muitas reservas ao mesmo tempo. Tente de novo.")).toBeInTheDocument();
    expect(corpos).toHaveLength(2);
    expect(corpos[0]).toBe(corpos[1]);
    expect(roteador.replace).toHaveBeenCalledTimes(1); // só a do passo 1: o conflito concorrente NÃO esconde o horário
    (await import("@testing-library/react")).cleanup();

    // 2b) conflito na primeira, sucesso na repetição
    let n = 0;
    servidor.use(
      http.post(`${BFF}/agendamentos/guest`, () =>
        ++n === 1
          ? erro(409, "CONFLITO_CONCORRENTE")
          : HttpResponse.json({ ...reserva(), tokenGuest: "x" }, { status: 201 }),
      ),
    );
    await reservar();
    await waitFor(() => expect(roteador.push).toHaveBeenCalledWith(`/agendar/pagamento/${reserva().id}`));
    expect(n).toBe(2);
    (await import("@testing-library/react")).cleanup();

    // 3) antecedência insuficiente
    servidor.use(http.post(`${BFF}/agendamentos/guest`, () => erro(422, "ANTECEDENCIA_INSUFICIENTE")));
    await reservar();
    expect(await screen.findByText(/pelo menos 2 horas de antecedência/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Escolher outro horário" })).toBeInTheDocument();
  });
});
