import { expect, test, type Page } from "@playwright/test";
import { horarioLivre } from "./apoio";

// Fatia 14D, A58: com prefers-reduced-motion OU com o "Sem animação" do painel de acessibilidade, as microanimações
// (chip escolhido, aviso) ficam com transition-duration e animation-duration calculados em 0 s.

async function duracoes(page: Page, seletor: string) {
  return page
    .locator(seletor)
    .first()
    .evaluate((el) => {
      const estilo = getComputedStyle(el);
      return [estilo.transitionDuration, estilo.animationDuration];
    });
}

async function escolherUmChip(page: Page, dia: string, servicoId: string) {
  await page.goto(`/agendar?${new URLSearchParams({ servicoId, data: dia })}`);
  const chip = page.locator(".chip-horario:not(.chip-indisponivel)").first();
  await chip.click();
  await expect(chip).toHaveAttribute("aria-pressed", "true");
}

test("A58: com prefers-reduced-motion: reduce, o chip escolhido e o aviso têm duração calculada 0 s", async ({
  page,
  request,
}) => {
  const h = await horarioLivre(request);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await escolherUmChip(page, h.dia, h.servicoId);
  for (const d of await duracoes(page, ".chip-horario[aria-pressed='true']")) expect(d).toBe("0s");
  await page.evaluate(() => {
    const aviso = document.createElement("div");
    aviso.className = "aviso";
    document.body.appendChild(aviso);
  });
  for (const d of await duracoes(page, "body > .aviso")) expect(d).toBe("0s");
});

test("A58: com o Sem animação do painel de acessibilidade (sem a preferência do sistema), a duração também é 0 s", async ({
  page,
  request,
}) => {
  const h = await horarioLivre(request);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await escolherUmChip(page, h.dia, h.servicoId);
  // sem nada ligado a animação existe (prova que o teste mede algo)
  expect((await duracoes(page, ".chip-horario[aria-pressed='true']"))[0]).not.toBe("0s");
  await page.evaluate(() => (document.documentElement.dataset.movimento = "reduzido"));
  for (const d of await duracoes(page, ".chip-horario[aria-pressed='true']")) expect(d).toBe("0s");
});

test("HG-2: com prefers-reduced-motion, a home não anima transform e as seções já aparecem", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Quase três décadas na mesma cadeira" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Antes de pagar" })).toBeVisible();
  const transicoesDeTransform = await page.evaluate(
    () =>
      [...document.querySelectorAll("body *")].filter((el) => {
        const estilo = getComputedStyle(el);
        const propriedades = estilo.transitionProperty.split(",").map((p) => p.trim());
        const duracoes = estilo.transitionDuration.split(",").map((d) => d.trim());
        return propriedades.some((p, i) => p.includes("transform") && duracoes[i] !== "0s");
      }).length,
  );
  expect(transicoesDeTransform).toBe(0);
});

test("A24: com prefers-reduced-motion ou com o Sem animação, a barra fixa e o Agendar horário do topo mudam sem transição", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 800 });
  const semTransicao = async () => {
    for (const seletor of [".barra-agendar", ".botao-agendar-topo", "#inicio .botao-principal"]) {
      const duracoes = await page
        .locator(seletor)
        .first()
        .evaluate((el) =>
          getComputedStyle(el)
            .transitionDuration.split(",")
            .map((d) => d.trim()),
        );
      for (const d of duracoes) expect(d, seletor).toBe("0s");
    }
  };
  const aside = page.locator(".barra-agendar");

  // sem nada ligado a barra tem transição (prova que o teste mede algo)
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const antes = await aside.evaluate((el) => getComputedStyle(el).transitionDuration);
  expect(antes.split(",").some((d) => d.trim() !== "0s")).toBe(true);

  // preferência do sistema
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await expect(aside).toHaveAttribute("data-escondida", "true");
  await semTransicao();
  await page.evaluate(() => window.scrollTo({ top: document.body.scrollHeight, behavior: "instant" }));
  await expect(aside).toHaveAttribute("data-escondida", "false");
  await semTransicao();

  // "Sem animação" do painel, sem a preferência do sistema
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await page.evaluate(() => (document.documentElement.dataset.movimento = "reduzido"));
  await semTransicao();
});
