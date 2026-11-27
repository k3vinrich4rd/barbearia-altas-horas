import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Galeria sem rostos (27/11/2026): as fotos com clientes, a vitrine com as TVs e a fachada antiga saíram do site.
// GS-3: o build não publica mais esses arquivos (404). GS-5: axe com WCAG 2.2 AA e zero violações. GS-6: CLS ≤ 0,1.
const QUE_SAIRAM = [
  "galeria/eduardo-em-atendimento.jpg",
  "galeria/corte-topete-parede-do-logo.jpg",
  "galeria/corte-degrade-com-desenho.jpg",
  "galeria/corte-social-grisalho-na-regua.jpg",
  "galeria/corte-infantil-cacheado-com-degrade.jpg",
  "galeria/corte-franja-curta-jovem.jpg",
  "galeria/platinado-cliente-de-oculos.jpg",
  "galeria/corte-infantil-risca-lateral.jpg",
  "galeria/corte-cacheado-volume.jpg",
  "galeria/platinado-cliente-e-eduardo.jpg",
  "galeria/corte-topo-volumoso-com-degrade.jpg",
  "galeria/fade-perfil-capa-altas-horas.jpg",
  "galeria/barba-cheia-desenhada.jpg",
  "galeria/degrade-parede-do-logo.jpg",
  "galeria/degrade-baixo-alinhamento-na-cadeira.jpg",
  "galeria/franja-reta-alinhada.jpg",
  "galeria/degrade-grisalho-penteado-para-tras.jpg",
  "galeria/salao-vitrine-com-logo.jpg",
  "fachada.jpg",
];

test("GS-3: as 19 fotos que saíram respondem 404 e as novas respondem 200", async ({ request }) => {
  for (const nome of QUE_SAIRAM) {
    const r = await request.get(`/assets/${nome}`);
    expect(r.status(), nome).toBe(404);
  }
  for (const nome of ["galeria/salao-vitrine-sem-tv.jpg", "fachada-sem-tv.jpg"]) {
    const r = await request.get(`/assets/${nome}`);
    expect(r.status(), nome).toBe(200);
  }
});

for (const tamanho of [
  { width: 360, height: 800 },
  { width: 1280, height: 800 },
]) {
  test(`GS-5 e GS-6: home a ${tamanho.width}px sem violação (WCAG 2.2 AA) e com CLS ≤ 0,1`, async ({ page }) => {
    await page.setViewportSize(tamanho);
    await page.addInitScript(() => {
      const w = window as unknown as { __cls: number };
      w.__cls = 0;
      new PerformanceObserver((lista) => {
        for (const e of lista.getEntries() as unknown as { hadRecentInput: boolean; value: number }[])
          if (!e.hadRecentInput) w.__cls += e.value;
      }).observe({ type: "layout-shift", buffered: true });
    });
    await page.goto("/");
    // cenário A (Fatia 16): sem galeria na home; rola pela página inteira antes de medir
    await expect(page.locator("#galeria")).toHaveCount(0);
    await page.locator("#onde-estamos").scrollIntoViewIfNeeded();
    await page.locator("#duvidas").scrollIntoViewIfNeeded();
    const cls = await page.evaluate(() => (window as unknown as { __cls: number }).__cls);
    expect(cls, "CLS da home").toBeLessThanOrEqual(0.1);
    for (const tema of ["escuro", "claro"]) {
      await page.evaluate((t) => (document.documentElement.dataset.theme = t), tema);
      const r = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
        .analyze();
      expect(
        r.violations.map(
          (v) =>
            `${v.id}: ${v.nodes
              .map((n) => n.target.join(" "))
              .slice(0, 3)
              .join(" | ")}`,
        ),
        `home ${tamanho.width}px tema ${tema}`,
      ).toEqual([]);
    }
  });
}
