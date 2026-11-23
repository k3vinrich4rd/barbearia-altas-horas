import { expect, test } from "@playwright/test";

// Home mais fluida (Fatia 12). A 12A cobre o link antigo; a 12B e a 12C acrescentam os demais critérios.
test("A4: o link antigo /#historia leva à Trajetória", async ({ page }) => {
  await page.goto("/#historia");
  await expect(page).toHaveURL(/#trajetoria$/);
  const trajetoria = page.locator("#trajetoria");
  await expect(trajetoria).toBeVisible();
  await expect(trajetoria.getByRole("heading", { level: 2 })).toHaveText("Quase três décadas na mesma cadeira");
  await expect(page.locator("#historia")).toHaveCount(0);
  // a janela rolou até o bloco: o título está dentro da área visível
  await expect(trajetoria.getByRole("heading", { level: 2 })).toBeInViewport();
});
