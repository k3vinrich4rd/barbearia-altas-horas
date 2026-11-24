import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { Agendar } from "./Agendar";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// Fatia 14A (D197): a agenda não tem antecedência mínima nem limite de dias à frente. Hoje é ter 01/12/2026.
const VAZIA = (data: string) =>
  HttpResponse.json({
    data,
    fuso: "America/Sao_Paulo",
    servicoId: CINCO_SERVICOS[1].id,
    duracaoMin: 60,
    barbeiros: [],
  });

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio("2026-12-01T09:00:00-03:00");
  definirBusca(`servicoId=${CINCO_SERVICOS[1].id}`);
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([BARBEIRO])),
  );
});
afterEach(() => vi.useRealTimers());

/** Todos os arquivos de código do front (sem testes), mais os de configuração que já falaram do horizonte. */
function arquivosDeCodigo(pasta: string): string[] {
  return readdirSync(pasta, { withFileTypes: true }).flatMap((e) => {
    const caminho = join(pasta, e.name);
    if (e.isDirectory()) return e.name === "node_modules" || e.name === ".next" ? [] : arquivosDeCodigo(caminho);
    return /\.(ts|tsx|mjs|json|yml)$/.test(e.name) && !/\.test\.tsx?$/.test(e.name) ? [caminho] : [];
  });
}

describe("agenda sem limites", () => {
  test("A26: o 'Outro dia' tem só o mínimo (hoje), busca qualquer data futura, e a página não fala de 'dias à frente', 'até {data}' nem 'antecedência'; o código não lê NEXT_PUBLIC_HORIZONTE_DIAS", async () => {
    const datas: string[] = [];
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) => {
        const data = new URL(request.url).searchParams.get("data")!;
        datas.push(data);
        return VAZIA(data);
      }),
    );
    renderizar(<Agendar />);
    const dia = await screen.findByLabelText("Outro dia");
    expect(dia).toHaveAttribute("min", "2026-12-01");
    expect(dia).not.toHaveAttribute("max");

    fireEvent.change(dia, { target: { value: "2028-01-05" } });
    await waitFor(() => expect(datas).toContain("2028-01-05"));
    expect(await screen.findByText(/Esse dia já está cheio/)).toBeInTheDocument();

    const pagina = document.body.textContent ?? "";
    expect(pagina).not.toMatch(/dias à frente/i);
    expect(pagina).not.toMatch(/antecedência/i);
    expect(pagina).not.toMatch(/Outro dia \(até/);
    expect(pagina).not.toMatch(/Escolha um dia de hoje até/);
    expect(pagina).toContain("Pode marcar para hoje ou para qualquer data futura.");

    const raiz = resolve(__dirname, "..", "..");
    const nome = "NEXT_PUBLIC_" + "HORIZONTE_DIAS";
    const comONome = [
      ...arquivosDeCodigo(join(raiz, "src")),
      join(raiz, "playwright.config.ts"),
      join(raiz, ".env.example"),
      join(raiz, "..", ".github", "workflows", "frontend-ci.yml"),
    ].filter((arquivo) => readFileSync(arquivo, "utf8").includes(nome));
    expect(comONome).toEqual([]);
  });

  test("A26: sem horário no trecho da busca, 'Ver o próximo dia com horário' olha 30 dias e oferece 'Procurar mais 30 dias' e 'Escolher em Outro dia'", async () => {
    const datas: string[] = [];
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) => {
        const data = new URL(request.url).searchParams.get("data")!;
        datas.push(data);
        return data === "2026-12-31"
          ? HttpResponse.json({
              data,
              fuso: "America/Sao_Paulo",
              servicoId: CINCO_SERVICOS[1].id,
              duracaoMin: 60,
              barbeiros: [
                { barbeiroId: BARBEIRO.id, nomeExibicao: "Carlos Eduardo", horarios: ["2026-12-31T10:00:00-03:00"] },
              ],
            })
          : VAZIA(data);
      }),
    );
    const { user } = renderizar(<Agendar />);
    fireEvent.change(await screen.findByLabelText("Outro dia"), { target: { value: "2026-12-01" } });
    await user.click(await screen.findByRole("button", { name: "Ver o próximo dia com horário" }));

    // 30 dias a partir de 01/12 vão até 31/12: o dia 31 tem horário e a busca para nele
    await screen.findByRole("button", { name: "10:00" });
    expect(datas).toContain("2026-12-31");
    expect(screen.queryByText(/Procurar mais 30 dias/)).toBeNull();
  });

  test("A26: quando nenhum dos 30 dias tem horário a tela avisa e deixa procurar mais 30 dias ou escolher em Outro dia", async () => {
    const datas: string[] = [];
    servidor.use(
      http.get(`${BFF}/disponibilidade`, ({ request }) => {
        const data = new URL(request.url).searchParams.get("data")!;
        datas.push(data);
        return VAZIA(data);
      }),
    );
    const { user } = renderizar(<Agendar />);
    fireEvent.change(await screen.findByLabelText("Outro dia"), { target: { value: "2026-12-01" } });
    await user.click(await screen.findByRole("button", { name: "Ver o próximo dia com horário" }));

    expect(await screen.findByText(/Não achamos horário livre nos próximos 30 dias/)).toBeInTheDocument();
    expect(datas).toContain("2026-12-31");
    expect(datas).not.toContain("2027-01-01");
    await user.click(screen.getByRole("button", { name: "Procurar mais 30 dias" }));
    await waitFor(() => expect(datas).toContain("2027-01-30"));
    await user.click(await screen.findByRole("button", { name: "Escolher em Outro dia" }));
    expect(screen.getByLabelText("Outro dia")).toHaveFocus();
  });
});
