import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, roteador, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const TZ_ORIGINAL = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "Asia/Tokyo"; // o navegador de teste está em Tóquio: a tela tem que mostrar o horário da barbearia
});
afterAll(() => {
  if (TZ_ORIGINAL === undefined) delete process.env.TZ;
  else process.env.TZ = TZ_ORIGINAL;
});
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
  );
});
afterEach(() => vi.useRealTimers());

const disponibilidade = (barbeiros: unknown[]) =>
  HttpResponse.json({
    data: "2026-11-24",
    fuso: "America/Sao_Paulo",
    servicoId: CINCO_SERVICOS[1].id,
    duracaoMin: 60,
    barbeiros,
  });

describe("horários", () => {
  test("A19: horários no fuso da barbearia, agrupados por barbeiro, com grupo nomeado; dia vazio avisa; o seletor só tem o mínimo (hoje) e avisa o dia que já passou", async () => {
    expect(new Date("2026-11-24T10:00:00-03:00").getHours()).toBe(22); // prova de que o fuso do navegador é Tóquio
    servidor.use(
      http.get(`${BFF}/disponibilidade`, () =>
        disponibilidade([
          {
            barbeiroId: BARBEIRO.id,
            nomeExibicao: "Carlos Eduardo",
            horarios: ["2026-11-24T10:00:00-03:00", "2026-11-24T10:30:00-03:00"],
          },
          { barbeiroId: "b2", nomeExibicao: "Outro Barbeiro", horarios: ["2026-11-24T10:30:00-03:00"] },
        ]),
      ),
    );
    renderizar(<Agendar />);
    const dia = await screen.findByLabelText(/Outro dia/);
    expect(dia).toHaveAttribute("min", "2026-11-18");
    expect(dia).not.toHaveAttribute("max"); // D197: sem limite de dias à frente

    fireEvent.change(dia, { target: { value: "2026-11-24" } });
    const grupo = await screen.findByRole("group", { name: "Horários de Carlos Eduardo, terça 24/11" });
    expect(
      within(grupo)
        .getAllByRole("button")
        .map((b) => b.textContent),
    ).toEqual(["10:00", "10:30"]);
    const outro = screen.getByRole("group", { name: "Horários de Outro Barbeiro, terça 24/11" });
    expect(
      within(outro)
        .getAllByRole("button")
        .map((b) => b.textContent),
    ).toEqual(["10:30"]);

    // antes de hoje: avisa e não chama a API; depois de hoje, qualquer data vale (D197)
    let chamadas = 0;
    servidor.use(
      http.get(`${BFF}/disponibilidade`, () => {
        chamadas++;
        return disponibilidade([]);
      }),
    );
    fireEvent.change(dia, { target: { value: "2026-11-10" } });
    expect(await screen.findByText(/Escolha um dia de hoje em diante/)).toBeInTheDocument();
    expect(chamadas).toBe(0);
    fireEvent.change(dia, { target: { value: "2027-01-30" } });
    await waitFor(() => expect(chamadas).toBe(1));
    expect(screen.queryByText(/Escolha um dia de hoje/)).toBeNull();

    // dia sem horários
    fireEvent.change(dia, { target: { value: "2026-11-25" } });
    expect(await screen.findByText(/Esse dia já está cheio/)).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: /Horários de/ })).toBeNull();
  });

  test("A20: 422 FORA_DO_HORIZONTE (deprecated, D197) mostra a mensagem junto do campo, leva o foco a ela e limpa os horários antigos", async () => {
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) =>
        new URL(request.url).searchParams.get("data") === "2026-12-10"
          ? erro(422, "FORA_DO_HORIZONTE")
          : disponibilidade([
              { barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-11-24T10:00:00-03:00"] },
            ]),
      ),
    );
    renderizar(<Agendar />);
    const dia = await screen.findByLabelText(/Outro dia/);
    fireEvent.change(dia, { target: { value: "2026-11-24" } });
    expect(await screen.findByRole("button", { name: "10:00" })).toBeInTheDocument();

    fireEvent.change(dia, { target: { value: "2026-12-10" } });
    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Essa data não está disponível. Escolha outra em Outro dia.");
    await waitFor(() => expect(alerta).toHaveFocus());
    expect(screen.queryByRole("button", { name: "10:00" })).toBeNull();
    expect(dia.parentElement).toContainElement(alerta);
  });

  test("A16: os horários do dia são chips (livres e escolhíveis) e o que falta na grade vira chip cinza com aria-disabled", async () => {
    servidor.use(
      http.get(`${BFF}/disponibilidade`, () =>
        disponibilidade([
          {
            barbeiroId: BARBEIRO.id,
            nomeExibicao: "Carlos Eduardo",
            horarios: ["2026-11-24T10:00:00-03:00", "2026-11-24T10:30:00-03:00", "2026-11-24T11:30:00-03:00"],
          },
        ]),
      ),
    );
    const { user } = renderizar(<Agendar />);
    fireEvent.change(await screen.findByLabelText(/Outro dia/), { target: { value: "2026-11-24" } });

    const livre = await screen.findByRole("button", { name: "10:00" });
    expect(livre).not.toHaveAttribute("aria-disabled");
    expect(livre).toHaveAttribute("aria-pressed", "false");
    await user.click(livre);
    expect(livre).toHaveAttribute("aria-pressed", "true");

    const cinza = screen.getByRole("button", { name: /^11:00/ });
    expect(cinza).toHaveAttribute("aria-disabled", "true");
    expect(cinza).toHaveClass("chip-indisponivel");
    await user.click(cinza);
    expect(cinza).not.toHaveAttribute("aria-pressed", "true");
    expect(roteador.push).not.toHaveBeenCalled();
  });

  test("A17: dia sem horário mostra 'Sem horários neste dia' e o botão leva ao próximo dia com horário", async () => {
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) => {
        const data = new URL(request.url).searchParams.get("data");
        return HttpResponse.json({
          data,
          fuso: "America/Sao_Paulo",
          servicoId: CINCO_SERVICOS[1].id,
          duracaoMin: 60,
          barbeiros:
            data === "2026-11-27"
              ? [{ barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-11-27T09:00:00-03:00"] }]
              : [],
        });
      }),
    );
    const { user } = renderizar(<Agendar />);
    const dia = await screen.findByLabelText(/Outro dia/);
    fireEvent.change(dia, { target: { value: "2026-11-25" } });
    expect(await screen.findByText("Esse dia já está cheio. Veja o próximo dia com horário.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Ver o próximo dia com horário" }));
    expect(await screen.findByRole("button", { name: "09:00" })).toBeInTheDocument();
    expect(dia).toHaveValue("2026-11-27");
    expect(screen.queryByText("Esse dia já está cheio. Veja o próximo dia com horário.")).toBeNull();
  });
});
