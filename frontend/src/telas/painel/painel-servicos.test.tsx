import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { centavosDoTexto } from "@/api/formato-painel";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { servicoAdmin } from "@/testes/painel";
import { ADMIN, renderizar } from "@/testes/render";
import { CadastroDeServicos } from "./CadastroDeServicos";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

const ATIVO = servicoAdmin();
const INATIVO = servicoAdmin({
  id: "s2",
  nome: "Barba Completa",
  precoCentavos: 3000,
  precoFormatado: "R$ 30,00",
  ativo: false,
});

describe("serviços do painel", () => {
  test("A49: lista com os inativos marcados; o preço digitado vira centavos inteiros sem float e rejeita 0 e texto; duração de 5 a 480; NOME_JA_EXISTE no campo; CONFLITO_DE_VERSAO recarrega; o PUT leva ativo e version", async () => {
    // o parse do preço, sem float
    expect(centavosDoTexto("55,90")).toBe(5590);
    expect(centavosDoTexto("55")).toBe(5500);
    expect(centavosDoTexto("1.234,5")).toBe(123450);
    expect(centavosDoTexto("R$ 0,05")).toBe(5);
    expect(centavosDoTexto("0")).toBeNull();
    expect(centavosDoTexto("0,00")).toBeNull();
    expect(centavosDoTexto("abc")).toBeNull();
    expect(centavosDoTexto("-5")).toBeNull();
    expect(centavosDoTexto("55,999")).toBeNull();
    expect(centavosDoTexto("")).toBeNull();

    let lista = [ATIVO, INATIVO];
    let leituras = 0;
    let versaoAtual = 2;
    const posts: Record<string, unknown>[] = [];
    const puts: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/admin/servicos`, () => {
        leituras++;
        return HttpResponse.json(lista);
      }),
      http.post(`${BFF}/admin/servicos`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        posts.push(corpo);
        if (String(corpo.nome).toLowerCase() === "corte degradê / fade / navalhado") return erro(409, "NOME_JA_EXISTE");
        return HttpResponse.json(servicoAdmin({ id: "s3", ...corpo, ativo: true, version: 0 }), { status: 201 });
      }),
      http.put(`${BFF}/admin/servicos/:id`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        puts.push(corpo);
        if (corpo.version !== versaoAtual) return erro(409, "CONFLITO_DE_VERSAO");
        return HttpResponse.json(servicoAdmin({ ...corpo, version: 3 }));
      }),
    );
    const { user } = renderizar(<CadastroDeServicos />, ADMIN);

    // os inativos aparecem, marcados em texto
    const tabela = await screen.findByRole("table", { name: /Serviços/ });
    const linhas = within(tabela).getAllByRole("row").slice(1);
    expect(linhas).toHaveLength(2);
    expect(linhas[0]).toHaveTextContent("Ativo");
    expect(linhas[1]).toHaveTextContent("Barba Completa");
    expect(linhas[1]).toHaveTextContent("Inativo");
    expect(screen.queryByRole("button", { name: /excluir|apagar|remover/i })).toBeNull(); // nenhum serviço é apagado

    // criar: validação do navegador antes de chamar a API
    await user.click(screen.getByRole("button", { name: "Novo serviço" }));
    await user.type(screen.getByLabelText("Nome"), "Hidratação");
    await user.type(screen.getByLabelText("Duração (minutos)"), "4");
    await user.type(screen.getByLabelText("Preço (R$)"), "0");
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    expect(screen.getByText("A duração deve ser um número de 5 a 480 minutos.")).toBeInTheDocument();
    expect(screen.getByText("Informe um preço maior que zero, como 55,90.")).toBeInTheDocument();
    expect(posts).toHaveLength(0);
    await user.clear(screen.getByLabelText("Duração (minutos)"));
    await user.type(screen.getByLabelText("Duração (minutos)"), "481");
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    expect(screen.getByText("A duração deve ser um número de 5 a 480 minutos.")).toBeInTheDocument();

    // nome repetido: 409 aparece no campo nome
    await user.clear(screen.getByLabelText("Nome"));
    await user.type(screen.getByLabelText("Nome"), "Corte Degradê / Fade / Navalhado");
    await user.clear(screen.getByLabelText("Duração (minutos)"));
    await user.type(screen.getByLabelText("Duração (minutos)"), "30");
    await user.clear(screen.getByLabelText("Preço (R$)"));
    await user.type(screen.getByLabelText("Preço (R$)"), "55,90");
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    await vi.waitFor(() => expect(screen.getByLabelText("Nome")).toHaveAttribute("aria-invalid", "true"));
    expect(screen.getByText("Já existe um item com este nome.")).toBeInTheDocument();
    expect(posts[0]).toEqual({ nome: "Corte Degradê / Fade / Navalhado", duracaoMin: 30, precoCentavos: 5590 });

    // criar de verdade: centavos inteiros no corpo e a lista é lida de novo
    await user.clear(screen.getByLabelText("Nome"));
    await user.type(screen.getByLabelText("Nome"), "Hidratação");
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    await vi.waitFor(() => expect(posts[1]).toEqual({ nome: "Hidratação", duracaoMin: 30, precoCentavos: 5590 }));
    expect(Number.isInteger(posts[1].precoCentavos)).toBe(true);
    await vi.waitFor(() => expect(leituras).toBe(2));

    // editar: o PUT leva ativo e version; versão velha dá 409 e a lista recarrega
    await user.click(await screen.findByRole("button", { name: "Editar Corte Degradê / Fade / Navalhado" }));
    expect(screen.getByLabelText("Preço (R$)")).toHaveValue("55,00");
    await user.click(screen.getByLabelText(/^Ativo/));
    versaoAtual = 9; // outra pessoa salvou antes: a lista que a tela vai ler de novo já traz a version 9
    lista = [servicoAdmin({ version: 9 }), INATIVO];
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    await vi.waitFor(() => expect(puts[0]).toMatchObject({ ativo: false, version: 2, precoCentavos: 5500 }));
    expect(await screen.findByText(/Alguém alterou este serviço antes de você/)).toBeInTheDocument();
    await vi.waitFor(() => expect(leituras).toBe(3));

    // com a version nova (9) o salvamento passa a mandar a certa
    await user.click(screen.getByRole("button", { name: "Salvar serviço" }));
    await vi.waitFor(() => expect(puts[1]).toMatchObject({ version: 9 }));
  });
});
