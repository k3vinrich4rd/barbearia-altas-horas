import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { barbeiroAdmin } from "@/testes/painel";
import { ADMIN, fixarRelogio, renderizar } from "@/testes/render";
import { CadastroDeBarbeiros } from "./CadastroDeBarbeiros";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
});
afterEach(() => vi.useRealTimers());

const EDUARDO = barbeiroAdmin({ whatsapp: "11906941689", usuarioId: "u-eduardo", nomeExibicao: "Eduardo Cândido" });

function lista(...barbeiros: unknown[]) {
  servidor.use(http.get(`${BFF}/admin/barbeiros`, () => HttpResponse.json(barbeiros)));
}

describe("barbeiros, jornada e folgas", () => {
  test("A50: lista com ativo, conta vinculada e WhatsApp com máscara; USUARIO_JA_VINCULADO no campo; o WhatsApp vai só com números", async () => {
    lista(EDUARDO, barbeiroAdmin({ id: "b2", nomeExibicao: "Rafa", ativo: false }));
    const posts: Record<string, unknown>[] = [];
    servidor.use(
      http.post(`${BFF}/admin/barbeiros`, async ({ request }) => {
        posts.push((await request.json()) as Record<string, unknown>);
        return erro(409, "USUARIO_JA_VINCULADO");
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    const tabela = await screen.findByRole("table", { name: "Barbeiros" });
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas[0]).toHaveTextContent("Eduardo Cândido");
    expect(linhas[0]).toHaveTextContent("(11) 90694-1689");
    expect(linhas[0]).toHaveTextContent("Vinculada");
    expect(linhas[0]).toHaveTextContent("Ativo");
    expect(linhas[0]).toHaveTextContent("almoço 12h às 13h");
    expect(linhas[1]).toHaveTextContent("Sem número");
    expect(linhas[1]).toHaveTextContent("Sem conta");
    expect(linhas[1]).toHaveTextContent("Inativo");

    await user.click(screen.getByRole("button", { name: "Novo barbeiro" }));
    await user.type(screen.getByLabelText("Nome de exibição"), "Novo Barbeiro");
    await user.type(screen.getByLabelText("WhatsApp comercial"), "11912345678");
    expect(screen.getByLabelText("WhatsApp comercial")).toHaveValue("(11) 91234-5678"); // a máscara aparece enquanto digita
    await user.type(screen.getByLabelText("Conta de login (opcional)"), "u-eduardo");
    await user.click(screen.getByRole("button", { name: "Salvar barbeiro" }));
    await vi.waitFor(() =>
      expect(screen.getByLabelText("Conta de login (opcional)")).toHaveAttribute("aria-invalid", "true"),
    );
    expect(screen.getByText("Esta conta já está ligada a outro barbeiro.")).toBeInTheDocument();
    expect(posts[0]).toEqual({ nomeExibicao: "Novo Barbeiro", usuarioId: "u-eduardo", whatsapp: "11912345678" });
  });

  test("A50: a jornada é editada como semana inteira (terça a sábado) e enviada de uma vez; o almoço é a lacuna; JORNADA_SOBREPOSTA aparece junto do dia", async () => {
    lista(EDUARDO);
    const puts: unknown[] = [];
    servidor.use(
      http.get(`${BFF}/admin/barbeiros/:id/folgas`, () => HttpResponse.json([])),
      http.put(`${BFF}/admin/barbeiros/:id/jornada`, async ({ request }) => {
        const corpo = await request.json();
        puts.push(corpo);
        return puts.length === 1 ? erro(422, "JORNADA_SOBREPOSTA") : HttpResponse.json(corpo);
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    await user.click(await screen.findByRole("button", { name: "Jornada e folgas de Eduardo Cândido" }));

    // terça a sábado carregadas; segunda e domingo sem expediente; almoço é a lacuna, não cadastro à parte
    const terca = screen.getByRole("group", { name: "Terça" });
    expect(within(terca).getAllByLabelText(/Início do intervalo/)).toHaveLength(2);
    expect(within(terca).getByText("Almoço: 12h às 13h")).toBeInTheDocument();
    expect(within(screen.getByRole("group", { name: "Segunda" })).getByText("Sem expediente.")).toBeInTheDocument();
    expect(screen.getByText(/10 de 28 intervalos/)).toBeInTheDocument();

    // sobreposição: terça ganha um intervalo que cruza o primeiro; a API recusa e o erro aparece junto do dia
    await user.click(screen.getByRole("button", { name: "Adicionar intervalo em terça" }));
    await user.type(screen.getByLabelText("Início do intervalo 3 de terça"), "11:00");
    await user.type(screen.getByLabelText("Fim do intervalo 3 de terça"), "12:30");
    await user.click(screen.getByRole("button", { name: "Salvar jornada" }));
    await vi.waitFor(() =>
      expect(within(screen.getByRole("group", { name: "Terça" })).getByText(/se sobrepõem/)).toBeInTheDocument(),
    );
    expect(within(screen.getByRole("group", { name: "Quarta" })).queryByText(/se sobrepõem/)).toBeNull();

    // corrige (remove o intervalo) e salva: a semana inteira vai de uma vez, com dia ISO e HH:mm
    await user.click(screen.getByRole("button", { name: "Remover o intervalo 3 de terça" }));
    await user.click(screen.getByRole("button", { name: "Salvar jornada" }));
    await vi.waitFor(() => expect(puts).toHaveLength(2));
    const enviada = puts[1] as { diaSemana: number; horaInicio: string; horaFim: string }[];
    expect(enviada).toHaveLength(10);
    expect(enviada[0]).toEqual({ diaSemana: 2, horaInicio: "07:00", horaFim: "12:00" });
    expect(enviada.at(-1)).toEqual({ diaSemana: 6, horaInicio: "13:00", horaFim: "20:00" });
    expect(await screen.findByText("Jornada salva.")).toBeInTheDocument();
  });

  test("A50: folgas de hoje em diante, criar com início, fim e motivo, remover com confirmação e FOLGA_COM_RESERVAS explicado", async () => {
    lista(EDUARDO);
    let folgas = [
      { id: "f1", inicio: "2026-11-25T09:00:00-03:00", fim: "2026-11-25T12:00:00-03:00", motivo: "Médico" },
    ];
    const criadas: Record<string, unknown>[] = [];
    const removidas: string[] = [];
    servidor.use(
      http.get(`${BFF}/admin/barbeiros/:id/folgas`, () => HttpResponse.json(folgas)),
      http.post(`${BFF}/admin/barbeiros/:id/folgas`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        criadas.push(corpo);
        if (criadas.length === 1) return erro(422, "FOLGA_COM_RESERVAS");
        return HttpResponse.json({ id: "f2", ...corpo }, { status: 201 });
      }),
      http.delete(`${BFF}/admin/barbeiros/:id/folgas/:folgaId`, ({ params }) => {
        removidas.push(String(params.folgaId));
        folgas = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderizar(<CadastroDeBarbeiros />, ADMIN);
    await user.click(await screen.findByRole("button", { name: "Jornada e folgas de Eduardo Cândido" }));
    expect(await screen.findByText(/quarta, 25\/11 às 09:00 até quarta, 25\/11 às 12:00 · Médico/)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Dia do início"), "2026-11-26");
    await user.type(screen.getByLabelText("Hora do início"), "09:00");
    await user.type(screen.getByLabelText("Dia do fim"), "2026-11-26");
    await user.type(screen.getByLabelText("Hora do fim"), "18:00");
    await user.type(screen.getByLabelText("Motivo (opcional)"), "Curso");
    await user.click(screen.getByRole("button", { name: "Lançar folga" }));
    expect(await screen.findByText(/Há agendamentos neste período/)).toBeInTheDocument();
    expect(criadas[0]).toEqual({
      inicio: "2026-11-26T09:00:00-03:00",
      fim: "2026-11-26T18:00:00-03:00",
      motivo: "Curso",
    });
    await user.click(screen.getByRole("button", { name: "Lançar folga" }));
    expect(await screen.findByText("Folga lançada.")).toBeInTheDocument();

    // remover pede confirmação
    await user.click(screen.getByRole("button", { name: /Remover a folga de quarta, 25\/11 às 09:00/ }));
    expect(removidas).toHaveLength(0);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remover folga" }));
    await vi.waitFor(() => expect(removidas).toEqual(["f1"]));
    expect(await screen.findByText("Nenhuma folga de hoje em diante.")).toBeInTheDocument();
  });
});
