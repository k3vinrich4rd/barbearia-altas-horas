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
