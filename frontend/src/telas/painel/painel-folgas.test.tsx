import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { agendamentoAdmin, barbeiroAdmin, colunaDoDia, gradeDoDia } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { PainelFolgas } from "./PainelFolgas";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// C13 (Fatia 15, ADR 0015, E1 a E15 e E24; spec de UX "Férias e folgas", FF-1 a FF-12). Hoje é qua 18/11/2026.
const EDUARDO = barbeiroAdmin({ nomeExibicao: "Eduardo Candido" });
const conflito = (n: number, extra: Record<string, unknown> = {}) => ({
  reservaId: `c0000000-0000-4000-8000-00000000000${n}`,
  inicio: `2026-11-26T1${n}:00:00-03:00`,
  fim: `2026-11-26T1${n}:59:00-03:00`,
  servico: { id: "s1", nome: "Corte Clássico" },
  clienteNome: `Cliente ${n}`,
  origem: "CLIENTE",
  status: "CONFIRMADO",
  ...extra,
});
const FOLGA_DE_HOJE = {
  id: "f1",
  inicio: "2026-11-25T09:00:00-03:00",
  fim: "2026-11-25T12:00:00-03:00",
  motivo: "Médico",
  tipo: "FOLGA",
  atualizadaEm: null,
};

interface Opcoes {
  folgas?: object[];
  conflitos?: (inicio: string, fim: string) => object[];
  salvar?: (corpo: Record<string, unknown>, metodo: "POST" | "PUT") => Response;
}
/** A API do painel; guarda o que chegou em cada rota. */
function servir(o: Opcoes = {}) {
  let folgas = o.folgas ?? [];
  const chamadas = {
    previas: [] as { inicio: string | null; fim: string | null }[],
    salvos: [] as { metodo: string; caminho: string; corpo: Record<string, unknown> }[],
    removidas: [] as string[],
    cancelados: [] as { id: string; corpo: Record<string, unknown> }[],
  };
  const salvar = async (request: Request, metodo: "POST" | "PUT") => {
    const corpo = (await request.json()) as Record<string, unknown>;
    chamadas.salvos.push({ metodo, caminho: new URL(request.url).pathname, corpo });
    return (
      o.salvar?.(corpo, metodo) ??
      HttpResponse.json({ id: "f9", ...corpo, reservasMantidas: [] }, { status: metodo === "POST" ? 201 : 200 })
    );
  };
  servidor.use(
    http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json([EDUARDO])),
    http.get(`${BFF}/admin/barbeiros/:id/folgas`, () => HttpResponse.json(folgas)),
    http.get(`${BFF}/admin/barbeiros/:id/folgas/conflitos`, ({ request }) => {
      const q = new URL(request.url).searchParams;
      chamadas.previas.push({ inicio: q.get("inicio"), fim: q.get("fim") });
      return HttpResponse.json(o.conflitos?.(q.get("inicio")!, q.get("fim")!) ?? []);
    }),
    http.post(`${BFF}/admin/barbeiros/:id/folgas`, ({ request }) => salvar(request, "POST")),
    http.put(`${BFF}/admin/barbeiros/:id/folgas/:folgaId`, ({ request }) => salvar(request, "PUT")),
    http.delete(`${BFF}/admin/barbeiros/:id/folgas/:folgaId`, ({ params }) => {
      chamadas.removidas.push(String(params.folgaId));
      folgas = [];
      return new HttpResponse(null, { status: 204 });
    }),
    http.get(`${BFF}/admin/agenda/dia`, () =>
      HttpResponse.json(
        gradeDoDia("2026-11-26", [
          colunaDoDia([agendamentoAdmin({ id: conflito(4).reservaId, version: 7, inicio: conflito(4).inicio })]),
        ]),
      ),
    ),
    http.patch(`${BFF}/admin/agendamentos/:id/status`, async ({ request, params }) => {
      chamadas.cancelados.push({ id: String(params.id), corpo: (await request.json()) as Record<string, unknown> });
      return HttpResponse.json(agendamentoAdmin({ status: "CANCELADO" }));
    }),
  );
  return chamadas;
}

async function abrirFormulario() {
  const tela = renderizar(<PainelFolgas />, ADMIN);
  await tela.user.click(await screen.findByRole("button", { name: "Marcar férias ou folga" }));
  return tela;
}
type Usuario = Awaited<ReturnType<typeof abrirFormulario>>["user"];
async function datas(user: Usuario, de: string, ate: string) {
  await user.type(screen.getByLabelText("De"), de);
  await user.type(screen.getByLabelText("Até"), ate);
}

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

describe("férias e folgas no painel (Fatia 15)", () => {
  test("FF-1: sem folgas, a página diz 'Nenhuma folga de hoje em diante.' e tem o botão 'Marcar férias ou folga'", async () => {
    servir();
    renderizar(<PainelFolgas />, ADMIN);
    expect(await screen.findByRole("heading", { name: "Férias e folgas" })).toBeInTheDocument();
    expect(await screen.findByText("Nenhuma folga de hoje em diante.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Marcar férias ou folga" })).toBeInTheDocument();
  });

  test("FF-10 e FF-3: ao escolher as datas, antes de salvar, a prévia já chamou GET .../folgas/conflitos e mostra os 2 clientes (horário, serviço, nome, origem, status)", async () => {
    const chamadas = servir({
      conflitos: () => [conflito(4), conflito(5, { origem: "GUEST", status: "AGUARDANDO_PAGAMENTO" })],
    });
    const { user } = await abrirFormulario();
    await datas(user, "2026-11-26", "2026-11-26");
    expect(await screen.findByText("2 clientes marcados nesse período")).toHaveAttribute("role", "status");
    expect(chamadas.previas.at(-1)).toEqual({ inicio: "2026-11-26T00:00:00-03:00", fim: "2026-11-27T00:00:00-03:00" });
    expect(chamadas.salvos).toHaveLength(0);
    const lista = screen.getByRole("list", { name: "Clientes marcados no período" });
    const itens = within(lista).getAllByRole("listitem");
    expect(itens[0]).toHaveTextContent(/quinta, 26\/11 às 14:00 · Corte Clássico · Cliente 4 · cliente com conta/);
    expect(itens[1]).toHaveTextContent(/Cliente 5 · visitante/);
    expect(itens[1]).toHaveTextContent(/Aguardando pagamento/i);
    // as três saídas, nenhuma escolhida por padrão
    expect(screen.getByRole("button", { name: "Salvar e manter os horários" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancelar um a um" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Trocar as datas" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Salvar" })).toBeNull();
  });

  test("FF-4: 'Salvar e manter os horários' manda manterReservas: true; o aviso diz quantos continuam marcados e lista as reservas mantidas com 'Ver na agenda'", async () => {
    const chamadas = servir({
      conflitos: () => [conflito(4), conflito(5)],
      salvar: (corpo) =>
        HttpResponse.json({ id: "f9", ...corpo, reservasMantidas: [conflito(4), conflito(5)] }, { status: 201 }),
    });
    const { user } = await abrirFormulario();
    await datas(user, "2026-11-26", "2026-11-26");
    await user.click(await screen.findByRole("button", { name: "Salvar e manter os horários" }));
    expect(await screen.findByText("Folga lançada. 2 clientes continuam marcados nesse período.")).toBeInTheDocument();
    expect(chamadas.salvos[0].corpo).toMatchObject({ manterReservas: true, tipo: "FOLGA" });
    const mantidas = screen.getByRole("list", { name: "Reservas mantidas" });
    const links = within(mantidas).getAllByRole("link", { name: /Ver na agenda/ });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", `/painel?data=2026-11-26&barbeiro=${EDUARDO.id}`);
    expect(chamadas.cancelados).toHaveLength(0);
  });

  test("FF-5: 'Cancelar um a um' cancela pela barbearia só a reserva escolhida (com motivo e a versão da grade do dia) e a prévia passa a mostrar 1", async () => {
    let restantes = [conflito(4), conflito(5)];
    const chamadas = servir({ conflitos: () => restantes });
    const { user } = await abrirFormulario();
    await datas(user, "2026-11-26", "2026-11-26");
    await user.click(await screen.findByRole("button", { name: "Cancelar um a um" }));
    await user.click(screen.getByRole("button", { name: "Cancelar pela barbearia a reserva de Cliente 4" }));
    const dialogo = screen.getByRole("dialog", { name: "Cancelar pela barbearia" });
    await user.type(within(dialogo).getByLabelText("Motivo do cancelamento"), "Férias do barbeiro");
    restantes = [conflito(5)];
    await user.click(within(dialogo).getByRole("button", { name: "Confirmar cancelamento" }));
    expect(await screen.findByText("1 cliente marcado nesse período")).toBeInTheDocument();
    expect(chamadas.cancelados).toEqual([
      { id: conflito(4).reservaId, corpo: { status: "CANCELADO", version: 7, motivo: "Férias do barbeiro" } },
    ]);
    expect(chamadas.salvos).toHaveLength(0);
  });

  test("FF-6: com 'Dia inteiro' de 25/11 a 27/11 e o tipo Férias, manda inicio 25/11 00:00 e fim 28/11 00:00 (BRT) e o tipo FERIAS", async () => {
    const chamadas = servir();
    const { user } = await abrirFormulario();
    expect(screen.getByLabelText("Dia inteiro")).toBeChecked();
    expect(screen.getByRole("button", { name: "Folga" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Férias" }));
    expect(screen.getByRole("button", { name: "Férias" })).toHaveAttribute("aria-pressed", "true");
    await datas(user, "2026-11-25", "2026-11-27");
    expect(screen.getByText("até sexta, 27/11 (o dia inteiro)")).toBeInTheDocument();
    expect(await screen.findByText("Nenhum cliente marcado nesse período.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText("Folga lançada.")).toBeInTheDocument();
    expect(chamadas.salvos[0]).toMatchObject({
      metodo: "POST",
      corpo: { inicio: "2026-11-25T00:00:00-03:00", fim: "2026-11-28T00:00:00-03:00", tipo: "FERIAS" },
    });
    expect(chamadas.salvos[0].corpo).not.toHaveProperty("manterReservas");
  });

  test("FF-7: 'Editar' abre a folga com os dados dela; mudar o fim e salvar manda PUT com inicio, fim, motivo e tipo; a lista mostra o fim novo", async () => {
    const chamadas = servir({ folgas: [FOLGA_DE_HOJE] });
    const { user } = renderizar(<PainelFolgas />, ADMIN);
    const item = (await screen.findByText(/Médico/)).closest("li")!;
    expect(item).toHaveTextContent("Folga de quarta, 25/11 às 09:00 até quarta, 25/11 às 12:00 · Médico");
    await user.click(within(item).getByRole("button", { name: /^Editar/ }));
    expect(screen.getByRole("heading", { name: "Editar férias ou folga" })).toBeInTheDocument();
    expect(screen.getByLabelText("Dia inteiro")).not.toBeChecked();
    expect(screen.getByLabelText("Hora do início")).toHaveValue("09:00");
    const fim = screen.getByLabelText("Hora do fim");
    expect(fim).toHaveValue("12:00");
    expect(screen.getByLabelText("Motivo (opcional)")).toHaveValue("Médico");
    await user.clear(fim);
    await user.type(fim, "15:00");
    servidor.use(
      http.get(`${BFF}/admin/barbeiros/:id/folgas`, () =>
        HttpResponse.json([{ ...FOLGA_DE_HOJE, fim: "2026-11-25T15:00:00-03:00" }]),
      ),
    );
    await user.click(await screen.findByRole("button", { name: "Salvar" }));
    expect(await screen.findByText("Folga alterada.")).toBeInTheDocument();
    expect(chamadas.salvos[0]).toEqual({
      metodo: "PUT",
      caminho: expect.stringMatching(/\/folgas\/f1$/),
      corpo: { inicio: "2026-11-25T09:00:00-03:00", fim: "2026-11-25T15:00:00-03:00", motivo: "Médico", tipo: "FOLGA" },
    });
    expect(await screen.findByText(/até quarta, 25\/11 às 15:00 · Médico/)).toBeInTheDocument();
  });

  test("FF-8: o motivo para em 200 caracteres (contador 200/200) e a tela lembra de não escrever dado de cliente", async () => {
    servir();
    const { user } = await abrirFormulario();
    const motivo = screen.getByLabelText("Motivo (opcional)");
    await user.click(motivo);
    await user.paste("a".repeat(201));
    expect(motivo).toHaveValue("a".repeat(200));
    expect(screen.getByText("200/200")).toBeInTheDocument();
    expect(screen.getByText("Só você vê. Não escreva dado de cliente aqui.")).toBeInTheDocument();
  });

  test("FF-11: alguém marcou entre a prévia e o salvar: o 422 FOLGA_COM_RESERVAS mostra o texto final, a lista de conflitos[] do problema e as 3 saídas; nada foi gravado", async () => {
    const chamadas = servir({
      conflitos: () => [],
      salvar: () =>
        HttpResponse.json(
          { status: 422, code: "FOLGA_COM_RESERVAS", title: "Existem reservas no período", conflitos: [conflito(6)] },
          { status: 422, headers: { "Content-Type": "application/problem+json" } },
        ),
    });
    const { user } = await abrirFormulario();
    await datas(user, "2026-11-26", "2026-11-26");
    expect(await screen.findByText("Nenhum cliente marcado nesse período.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(
      await screen.findByText(
        "Há clientes marcados nesse período. Confira a lista abaixo: você pode manter essas reservas ou cancelar uma por uma antes de salvar a folga.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("1 cliente marcado nesse período")).toBeInTheDocument();
    expect(
      within(screen.getByRole("list", { name: "Clientes marcados no período" })).getByText(/Cliente 6/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar e manter os horários" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancelar um a um" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Trocar as datas" })).toBeInTheDocument();
    expect(chamadas.salvos[0].corpo).not.toHaveProperty("manterReservas");
    expect(screen.queryByText("Folga lançada.")).toBeNull();
  });

  test("FF-1 (remover): 'Remover' pede confirmação e só então chama o DELETE; a lista fica vazia", async () => {
    const chamadas = servir({ folgas: [FOLGA_DE_HOJE] });
    const { user } = renderizar(<PainelFolgas />, ADMIN);
    await user.click(await screen.findByRole("button", { name: /^Remover de quarta, 25\/11 às 09:00/ }));
    expect(chamadas.removidas).toHaveLength(0);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remover folga" }));
    await vi.waitFor(() => expect(chamadas.removidas).toEqual(["f1"]));
    expect(await screen.findByText("Folga removida.")).toBeInTheDocument();
    expect(await screen.findByText("Nenhuma folga de hoje em diante.")).toBeInTheDocument();
  });

  test("FF-1 (erro): o fim antes do início é barrado na tela, sem chamar a prévia nem salvar", async () => {
    const chamadas = servir();
    const { user } = await abrirFormulario();
    await datas(user, "2026-11-27", "2026-11-25");
    expect(await screen.findByText("O fim da folga deve ser depois do início.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Salvar e manter os horários" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(chamadas.previas).toHaveLength(0);
    expect(chamadas.salvos).toHaveLength(0);
  });
});
