import { expect, test, type Page } from "@playwright/test";

// Fatia 16, F2 (docs/sdd/fatia-16-fluxo-sem-repeticao.md), topo e barra fixa: na home nunca aparecem dois "Agendar horário" juntos.
// A1: no máximo 1 visível e clicável ao rolar. A2: a barra fixa do celular some (e fica inert) com o herói ou
// "Como marcar" na tela. A3: no computador, o "Agendar horário" do topo fica fora da home e volta nas outras páginas.

/** Rola até `y` sem animação e espera a transição da barra (200 ms). */
async function rolarPara(page: Page, y: number) {
  await page.evaluate((t) => window.scrollTo({ top: t, behavior: "instant" }), y);
  await page.waitForTimeout(300);
}

test("A1: rolando a home a 360 e a 1280, nunca há mais de 1 Agendar horário visível e clicável", async ({ page }) => {
  for (const tamanho of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize(tamanho);
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    const altura = await page.evaluate(() => document.documentElement.scrollHeight);
    let maximo = 0;
    for (let y = 0; y <= altura; y += 200) {
      await page.evaluate((t) => window.scrollTo({ top: t, behavior: "instant" }), y);
      await page.waitForTimeout(300); // a transição da barra é de 200 ms
      const visiveis = await page.evaluate(
        () =>
          [...document.querySelectorAll<HTMLElement>("a, button")].filter((e) => {
            if (e.innerText.trim().toLowerCase() !== "agendar horário" || e.closest("[inert]")) return false;
            const r = e.getBoundingClientRect();
            const s = getComputedStyle(e);
            return (
              r.width > 0 &&
              r.bottom > 0 &&
              r.top < window.innerHeight &&
              s.visibility !== "hidden" &&
              Number(s.opacity) > 0.5
            );
          }).length,
      );
      maximo = Math.max(maximo, visiveis);
    }
    expect(maximo, `${tamanho.width}px`).toBeLessThanOrEqual(1);
  }
});

test("A2: a 360, a barra fica escondida e inert com o herói ou Como marcar na tela e aparece fora deles", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const aside = page.locator(".barra-agendar");

  // herói na tela
  await expect(aside).toHaveAttribute("data-escondida", "true");
  await expect(aside).toHaveAttribute("inert", "");

  // um ponto da página sem o herói e sem "Como marcar" na tela (a lista de serviços fica entre os dois)
  const livre = await page.evaluate(() => {
    const h = window.innerHeight;
    const caixas = ["inicio", "como-marcar"].map((id) => {
      const r = document.getElementById(id)!.getBoundingClientRect();
      return { topo: r.top + window.scrollY, fim: r.bottom + window.scrollY };
    });
    const maximo = document.documentElement.scrollHeight - h;
    for (let y = 0; y <= maximo; y += 20) {
      if (caixas.every((c) => c.fim <= y || c.topo >= y + h)) return y;
    }
    return null;
  });
  expect(livre, "existe trecho da home sem o herói e sem Como marcar a 360").not.toBeNull();
  await rolarPara(page, livre!);
  await expect(aside).toHaveAttribute("data-escondida", "false");
  await expect(aside).not.toHaveAttribute("inert");
  await expect(aside.getByRole("link", { name: "Agendar horário" })).toBeVisible();

  // "Como marcar" na tela
  const comoMarcar = await page.locator("#como-marcar").evaluate((e) => e.getBoundingClientRect().top + window.scrollY);
  await rolarPara(page, comoMarcar);
  await expect(page.locator("#como-marcar")).toBeInViewport();
  await expect(aside).toHaveAttribute("data-escondida", "true");
  await expect(aside).toHaveAttribute("inert", "");
});

test("A3: a 1280, na home o Agendar horário do topo fica escondido e o do herói é o único; em /privacidade o do topo aparece", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const doTopo = page.getByRole("banner").locator("a", { hasText: "Agendar horário" });
  await expect(doTopo).toBeHidden();
  const doHeroi = page.locator("#inicio").getByRole("link", { name: "Agendar horário" });
  await expect(doHeroi).toBeVisible();
  await expect(page.getByRole("link", { name: "Agendar horário", exact: true })).toHaveCount(1);

  await page.goto("/privacidade");
  await expect(page.getByRole("banner").getByRole("link", { name: "Agendar horário", exact: true })).toBeVisible();
});
