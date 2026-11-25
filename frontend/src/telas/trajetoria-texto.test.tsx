import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { BarbeiroDaHome } from "@/componentes/BarbeiroDaHome";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { MARCOS } from "@/conteudo/historia";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

/** Seção 6.2 da spec, aprovada pelo Kevin em 24/11/2026. */
const TRAJETORIA = [
  "O Carlos Eduardo Candido (o Eduardo) começou no ofício em 1994, aprendendo com o primeiro mestre. Três anos depois, em 1997, abriu a Barbearia Altas Horas no Jardim Bonança, em Osasco, e de lá não saiu mais: são quase 30 anos na mesma cadeira, de terça a sábado.",
  'Do corte social clássico ao degradê navalhado, passando pelo platinado e pelo corte das crianças, o trabalho fala por ele. Em 2017 veio o reconhecimento em dobro: o Prêmio Barbeiros do Brasil, que ele fez questão de trazer para Osasco, e o Prêmio Master Barber · Êxito Profissional. Ao receber o primeiro, resumiu tudo numa frase: "Amor na ponta da tesoura!".',
  "Instrutor formado pela Ibel Formação, hoje ele também ensina o ofício na Altas Horas Barbearia Academy. Com o dia dividido entre clientes e alunos, a agenda saiu do caderno e do WhatsApp e veio para cá, feita pelo filho dele, o Kevin: você escolhe, paga e o horário é seu.",
];
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
  test("A39: a Trajetória e o bloco do barbeiro têm os textos da seção 6.2; cada ano, nome e prêmio citado está na tabela 6.1 (com fonte no README)", async () => {
    const { unmount } = renderizar(<Trajetoria />);
    for (const paragrafo of TRAJETORIA) expect(screen.getByText(paragrafo)).toBeInTheDocument();
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
    const anos = new Set([...TRAJETORIA, TEXTO_DO_BARBEIRO].join(" ").match(/\b(19|20)\d{2}\b/g));
    expect([...anos].sort()).toEqual(["1994", "1997", "2017"]);
    for (const fato of FATOS_COM_FONTE) expect(README, `${fato} sem fonte no README`).toContain(fato);
  });
});
