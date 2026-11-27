import { expect, test } from "@playwright/test";

// Fluxo sem repetição (Fatia 16). Links antigos já divulgados (ou do menu de antes) continuam chegando ao lugar certo.
test("A28: /#trajetoria, /#historia e /#barbeiro vão para quem-somos; /#duvidas vai para como-marcar", async ({ page }) => {
  for (const antigo of ["#trajetoria", "#historia", "#barbeiro"]) {
    await page.goto(`/${antigo}`);
    await expect(page, antigo).toHaveURL(/\/#quem-somos$/);
    const titulo = page.locator("#quem-somos").getByRole("heading", { level: 2 });
    await expect(titulo).toHaveText("Quase três décadas na mesma cadeira");
    await expect(titulo, antigo).toBeInViewport();
  }

  // dentro da home, um link para /#trajetoria (o menu de antes) também leva a quem-somos
  await page.goto("/");
  await page.evaluate(() => {
    window.location.hash = "trajetoria";
  });
  await expect(page).toHaveURL(/\/#quem-somos$/);
  await expect(page.locator("#quem-somos").getByRole("heading", { level: 2 })).toBeInViewport();

  await page.goto("/#duvidas");
  const duvidas = page.locator("#como-marcar #duvidas");
  await expect(duvidas).toHaveCount(1);
  await expect(duvidas).toBeInViewport();
});
