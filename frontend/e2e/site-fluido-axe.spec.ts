import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { horarioLivre } from "./apoio";

// Fatia 12, A14 e A25: axe com WCAG 2.1 A e AA e ZERO violações (de qualquer impacto) na galeria, na home, nos
// serviços e nos dois primeiros passos do agendamento, no celular e no desktop, nos dois temas.
async function semViolacoes(page: Page, nome: string, escopo?: string) {
  let axe = new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]);
  if (escopo) axe = axe.include(escopo);
  const resultado = await axe.analyze();
  expect(
    resultado.violations.map(
      (v) =>
        `${v.id} (${v.impact}): ${v.nodes
          .slice(0, 3)
          .map((n) => n.target.join(" "))
          .join(" | ")}`,
    ),
    nome,
  ).toEqual([]);
}

const TAMANHOS = [
  { width: 1280, height: 800 },
  { width: 375, height: 812 },
];

test("A14: a galeria (grade e visualizador) não tem violação de acessibilidade", async ({ page }) => {
  for (const tamanho of TAMANHOS) {
    await page.setViewportSize(tamanho);
    await page.goto("/");
    for (const tema of ["escuro", "claro"]) {
      await page.evaluate((t) => (document.documentElement.dataset.theme = t), tema);
      await semViolacoes(page, `grade da galeria, ${tamanho.width}px, tema ${tema}`, "#galeria");
    }
    // o visualizador só existe com 2 ou mais fotos (27/11/2026: a galeria tem só a foto do salão)
    const miniaturas = page.locator("#galeria").getByRole("button", { name: /^Ampliar foto:/ });
    if ((await miniaturas.count()) > 1) {
      await miniaturas.first().click();
      await expect(page.getByRole("dialog")).toBeVisible();
      await semViolacoes(page, `visualizador, ${tamanho.width}px`, '[role="dialog"]');
      await page.keyboard.press("Escape");
    }
  }
});

test("A25: /, /servicos, /agendar e /agendar/dados não têm violação, a ordem de foco é coerente e a barra fixa não se sobrepõe", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  const dados = `/agendar/dados?${new URLSearchParams({ servicoId: horario.servicoId, barbeiroId: horario.barbeiroId, inicio: horario.inicio })}`;
  for (const tamanho of TAMANHOS) {
    await page.setViewportSize(tamanho);
    for (const caminho of ["/", "/servicos", "/agendar", dados]) {
      await page.goto(caminho);
      await page.waitForLoadState("networkidle");
      for (const tema of ["escuro", "claro"]) {
        await page.evaluate((t) => (document.documentElement.dataset.theme = t), tema);
        await semViolacoes(page, `${caminho.split("?")[0]}, ${tamanho.width}px, tema ${tema}`);
      }
    }
  }

  // ordem de foco na home (teclado, celular): o foco só anda para baixo na página, na mesma ordem da leitura
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const alturas: number[] = [];
  for (let i = 0; i < 14; i++) {
    await page.keyboard.press("Tab");
    alturas.push(
      await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        return el && el !== document.body ? Math.round(el.getBoundingClientRect().top + window.scrollY) : -1;
      }),
    );
  }
  expect(
    alturas.every((y) => y >= 0),
    "todo Tab cai num elemento da página",
  ).toBe(true);
  alturas.slice(1).forEach((y, i) => {
    expect(y, `o foco ${i + 2} não volta para cima: ${alturas.join(", ")}`).toBeGreaterThanOrEqual(alturas[i]);
  });

  // a barra fixa e o painel de acessibilidade (no cabeçalho) não se sobrepõem a nenhum controle em foco
  const barra = await page.locator(".barra-agendar").boundingBox();
  const painel = await page.getByRole("region", { name: "Recursos de acessibilidade" }).boundingBox();
  expect(barra).not.toBeNull();
  expect(painel).not.toBeNull();
  const cruza = (a: { x: number; y: number; width: number; height: number }, b: typeof a) =>
    a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;
  expect(cruza(barra!, painel!)).toBe(false);
  // com a barra no lugar, o último link do rodapé ainda é alcançável: ele rola para fora da área coberta
  await page.getByRole("link", { name: "Voltar ao topo" }).focus();
  const topo = await page.getByRole("link", { name: "Voltar ao topo" }).boundingBox();
  const barraDepois = await page.locator(".barra-agendar").boundingBox();
  expect(cruza(topo!, barraDepois!)).toBe(false);
});
