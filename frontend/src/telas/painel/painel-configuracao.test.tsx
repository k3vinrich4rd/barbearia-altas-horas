import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { configuracaoAdmin } from "@/testes/painel";
import { ADMIN, renderizar } from "@/testes/render";
import { CadastroDeConfiguracao } from "./CadastroDeConfiguracao";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();
beforeEach(() => zerarNavegacao());

describe("regras da agenda", () => {
  test("A51: o fuso é só leitura e não vai no PUT; limites do contrato (granularidade, intervalo, prazo de 3 a 60; sem antecedência nem horizonte, D197); CONFLITO_DE_VERSAO recarrega; sucesso diz que vale a partir da próxima requisição", async () => {
    let atual = configuracaoAdmin({ version: 2 });
    let leituras = 0;
    const puts: Record<string, unknown>[] = [];
    servidor.use(
      http.get(`${BFF}/admin/configuracao`, () => {
        leituras++;
        return HttpResponse.json(atual);
      }),
      http.put(`${BFF}/admin/configuracao`, async ({ request }) => {
        const corpo = (await request.json()) as Record<string, unknown>;
        puts.push(corpo);
        if (corpo.version !== atual.version) return erro(409, "CONFLITO_DE_VERSAO");
        return HttpResponse.json({ ...atual, ...corpo, version: atual.version + 1 });
      }),
    );
    const { user } = renderizar(<CadastroDeConfiguracao />, ADMIN);

    // o fuso aparece só para ler
    const fuso = await screen.findByLabelText("Fuso horário");
    expect(fuso).toHaveValue("America/Sao_Paulo");
    expect(fuso).toHaveAttribute("readonly");
    // a grade só tem os valores do contrato
    const opcoes = Array.from((screen.getByLabelText("Grade de horários (minutos)") as HTMLSelectElement).options).map(
      (o) => o.value,
    );
    expect(opcoes).toEqual(["5", "10", "15", "20", "30", "60"]);

    // limites: prazo de pagamento de 3 a 60
    await user.clear(screen.getByLabelText("Prazo para pagar (minutos)"));
    await user.type(screen.getByLabelText("Prazo para pagar (minutos)"), "2");
    await user.clear(screen.getByLabelText("Intervalo entre atendimentos (minutos)"));
    await user.type(screen.getByLabelText("Intervalo entre atendimentos (minutos)"), "121");
    await user.click(screen.getByRole("button", { name: "Salvar regras" }));
    expect(screen.getAllByText(/Informe um número de/)).toHaveLength(2);
    // D197: a tela não tem mais os campos revogados
    expect(screen.queryByLabelText(/Antecedência mínima/)).toBeNull();
    expect(screen.queryByLabelText(/Agenda aberta até/)).toBeNull();
    expect(puts).toHaveLength(0);

    // 3 é o mínimo: o PUT sai sem `fuso` e com a version da tela
    await user.clear(screen.getByLabelText("Prazo para pagar (minutos)"));
    await user.type(screen.getByLabelText("Prazo para pagar (minutos)"), "3");
    await user.clear(screen.getByLabelText("Intervalo entre atendimentos (minutos)"));
    await user.type(screen.getByLabelText("Intervalo entre atendimentos (minutos)"), "10");
    await user.selectOptions(screen.getByLabelText("Grade de horários (minutos)"), "15");
    // mudou por outra pessoa antes: 409 recarrega
    atual = configuracaoAdmin({ version: 7 });
    await user.click(screen.getByRole("button", { name: "Salvar regras" }));
    expect(await screen.findByText(/Alguém alterou as regras antes de você/)).toBeInTheDocument();
    await vi.waitFor(() => expect(leituras).toBe(2));
    expect(puts[0]).toEqual({
      intervaloEntreAtendimentosMin: 10,
      granularidadeMin: 15,
      expiracaoPagamentoMin: 3,
      version: 2,
    });
    expect("fuso" in puts[0]).toBe(false);

    // com a version nova a gravação passa e a tela avisa quando vale
    await user.click(await screen.findByRole("button", { name: "Salvar regras" }));
    await vi.waitFor(() => expect(puts[1]).toMatchObject({ version: 7 }));
    expect(await screen.findByText(/Valem a partir da próxima requisição/)).toBeInTheDocument();
  });
});
