import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { Rodape } from "@/componentes/Rodape";
import { BARBEIRO, SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

// Fatia 16 (fluxo sem repetição): cada informação num lugar só e nenhuma seção sem finalidade.
function usarApi(alcance = { atualizadoEm: "2026-11-27T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] }) {
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...BARBEIRO, nomeExibicao: "Eduardo Candido" }])),
    http.get(`${BFF}/alcance`, () => HttpResponse.json(alcance)),
  );
}

async function renderizarHome() {
  const r = renderizar(
    <>
      <Home />
      <Rodape />
    </>,
  );
  await screen.findByText("Descoloração / Platinado (química)");
  await new Promise((fim) => setTimeout(fim, 50));
  return r;
}

describe("home: fluxo sem repetição (Fatia 16)", () => {
  test("A8: sem dado de alcance, a seção 'De onde vêm nossos clientes' não existe (nem o 'Em breve')", async () => {
    usarApi();
    const { container } = await renderizarHome();
    expect(container.querySelector("#alcance")).toBeNull();
    expect(screen.queryByText(/Em breve/)).toBeNull();
  });

  test("A21: nenhum total de clientes na home (nem número de pessoas, nem de atendimentos)", async () => {
    usarApi();
    const { container } = await renderizarHome();
    expect(container.textContent).not.toMatch(/\d[\d.]*\s*(clientes|pessoas|atendimentos|cortes feitos)/i);
    expect(container.textContent).not.toMatch(/(clientes|pessoas) atendid/i);
  });

  // parte do A5 que cabe ao F4 ("Quem somos"); endereço, dias e WhatsApp em onde-estamos ficam para o F6
  test("parte do A5 (F4): \"Amor na ponta da tesoura\" 1×; \"Prêmio Barbeiros do Brasil\" no máximo 2×, só em quem-somos; endereço e dias 1× cada", async () => {
    usarApi();
    const { container } = await renderizarHome();
    const texto = container.textContent ?? "";
    const vezes = (t: string | RegExp, em = texto) => em.split(t).length - 1;
    for (const fato of ["R. São Judas Tadeu, 313", "Terça a sábado"]) expect(vezes(fato), fato).toBe(1);
    expect(vezes("Amor na ponta da tesoura")).toBe(1);
    const quemSomos = container.querySelector("#quem-somos")?.textContent ?? "";
    expect(vezes("Prêmio Barbeiros do Brasil")).toBeLessThanOrEqual(2);
    expect(vezes("Prêmio Barbeiros do Brasil", quemSomos)).toBe(vezes("Prêmio Barbeiros do Brasil"));
  });

  test("A6: um retrato do Eduardo, dentro de quem-somos; #barbeiro não existe", async () => {
    usarApi();
    const { container } = await renderizarHome();
    const retratos = [...container.querySelectorAll("img")].filter((img) => /^Eduardo Candido/.test(img.alt));
    expect(retratos).toHaveLength(1);
    expect(container.querySelector("#quem-somos")!.contains(retratos[0])).toBe(true);
    expect(container.querySelector("#barbeiro")).toBeNull();
    expect(screen.queryByRole("heading", { name: "Quem corta o seu cabelo" })).toBeNull();
  });

  // parte do A12 que cabe ao F4: o botão wa.me do bloco "Barbeiro" saiu; o contato entra em onde-estamos no F6
  test("parte do A12 (F4): no máximo 2 links wa.me, o \"Ou abrir o WhatsApp direto\" e o do rodapé", async () => {
    usarApi();
    const { container } = await renderizarHome();
    const links = [...container.querySelectorAll<HTMLAnchorElement>('a[href*="wa.me"]')];
    expect(links.length).toBeLessThanOrEqual(2);
    const comoMarcar = container.querySelector("#como-marcar")!;
    const rodape = container.querySelector("footer")!;
    for (const link of links) expect(comoMarcar.contains(link) || rodape.contains(link), link.href).toBe(true);
  });
});
