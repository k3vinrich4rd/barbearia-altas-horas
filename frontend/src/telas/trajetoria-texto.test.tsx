import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { BarbeiroDaHome } from "@/componentes/BarbeiroDaHome";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { HISTORIA, MARCOS } from "@/conteudo/historia";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

/** Texto aprovado pelo Kevin em 27/11/2026 (versão 3). */
const APROVADO = readFileSync(resolve(__dirname, "../../../docs/sdd/historia-v3-aprovada.txt"), "utf8");
const TEXTO_DO_BARBEIRO =
  "Barbeiro desde 1994 e dono da Altas Horas desde 1997. Professor na Academy e duas vezes premiado em 2017.";

/** Tabela 6.1: cada ano, nome e prêmio citado tem fonte no README do repositório. */
const README = readFileSync(resolve(__dirname, "../../../README.md"), "utf8");
const FATOS_COM_FONTE = [
  "Eduardo Candido",
  "1994",
  "1997",
  "2017",
  "Jardim Bonança",
  "Osasco",
  "Prêmio Barbeiros do Brasil",
  "Prêmio Master Barber · Êxito Profissional",
  "Ibel Formação",
  "Altas Horas Barbearia Academy",
  "Amor na ponta da tesoura!",
  "meu pai",
];

describe("Trajetória com fatos do repositório (Fatia 14B)", () => {
  test("A39: a Trajetória e o bloco do barbeiro têm os textos de docs/sdd/historia-v3-aprovada.txt; cada ano, nome e prêmio citado está na tabela 6.1 (com fonte no README)", async () => {
    const { unmount } = renderizar(<Trajetoria />);
    for (const paragrafo of HISTORIA) expect(screen.getByText(paragrafo)).toBeInTheDocument();
    expect(MARCOS.find((m) => m.ano === "Hoje")?.texto).toBe(
      "Barbeiro na cadeira e professor na Altas Horas Barbearia Academy. A agenda sai do caderno e vem para a internet.",
    );
    unmount();

    servidor.use(
      http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido" }])),
    );
    renderizar(<BarbeiroDaHome />);
    const bloco = (await screen.findByRole("heading", { name: "Quem corta o seu cabelo" })).closest("section")!;
    expect(await within(bloco).findByText(TEXTO_DO_BARBEIRO)).toBeInTheDocument();

    // nenhum ano fora da tabela 6.1 (fato inventado viraria um ano novo)
    const anos = new Set([...HISTORIA, TEXTO_DO_BARBEIRO].join(" ").match(/\b(19|20)\d{2}\b/g));
    expect([...anos].sort()).toEqual(["1994", "1997", "2017"]);
    for (const fato of FATOS_COM_FONTE) expect(README, `${fato} sem fonte no README`).toContain(fato);
  });

  test("HR-2 e HR-4: HISTORIA é idêntica, caractere por caractere, ao texto aprovado", () => {
    expect(HISTORIA).toHaveLength(3);
    expect(HISTORIA.join("\n\n") + "\n").toBe(APROVADO);
  });

  test('HR-3: o texto não traz "Cândido", "degradê", "platinado" nem "terça a sábado"; os únicos anos são 1994, 1997 e 2017', () => {
    const texto = HISTORIA.join(" ");
    for (const proibido of ["Cândido", "degradê", "platinado", "terça a sábado"]) {
      expect(texto).not.toContain(proibido);
    }
    expect([...(texto.match(/\b(19|20)\d{2}\b/g) ?? [])].sort()).toEqual(["1994", "1997", "2017"]);
  });
});
