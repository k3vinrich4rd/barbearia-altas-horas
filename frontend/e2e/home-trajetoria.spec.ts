import { expect, test } from "@playwright/test";

// A home conta a história da barbearia (1995, 1997, 2017, hoje) com interações que precisam funcionar no navegador
// de verdade e só com o teclado: linha do tempo, dúvidas em <details>, galeria (visualizador) e o painel de acessibilidade.
test("home: linha do tempo e dúvidas pelo teclado, galeria e tema claro no navegador de verdade", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Seu horário com o Eduardo, garantido.");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "escuro");

  // linha do tempo: 1997 vem marcado; Tab até 2017 e Enter troca o detalhe anunciado
  const linha = page.getByRole("group", { name: "Escolha um ano da linha do tempo" });
  await expect(linha.getByRole("button", { pressed: true })).toContainText("1997");
  await linha.getByRole("button", { name: /1997/ }).focus();
  await page.keyboard.press("Tab");
  await expect(linha.getByRole("button", { name: /2017/ })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(linha.getByRole("button", { pressed: true })).toContainText("2017");
  await expect(page.locator("#marco-detalhe")).toContainText("2017 · Dois prêmios.");

  // dúvidas: o <summary> abre com Enter, sem JavaScript da página
  const duvida = page.locator("#duvidas details").first();
  await duvida.locator("summary").focus();
  await page.keyboard.press("Enter");
  await expect(duvida).toHaveAttribute("open", "");

  // galeria: a miniatura abre a foto inteira, a seta troca, Esc fecha e o foco volta
  const galeria = page.locator("#galeria");
  await expect(galeria.getByRole("button", { name: /^Ampliar foto:/ })).toHaveCount(18);
  const primeira = galeria.getByRole("button", { name: /^Ampliar foto:/ }).first();
  await primeira.focus();
  await page.keyboard.press("Enter");
  const visualizador = page.getByRole("dialog");
  await expect(visualizador.getByText("Foto 1 de 18")).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(visualizador.getByText("Foto 2 de 18")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(visualizador).toBeHidden();
  await expect(galeria.getByRole("button", { name: /^Ampliar foto:/ }).nth(1)).toBeFocused();

  // painel de acessibilidade: tema claro e sem animação mudam o <html> e marcam o botão
  const painel = page.getByRole("region", { name: "Recursos de acessibilidade" });
  await painel.getByRole("button", { name: "Tema claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "claro");
  await expect(painel.getByRole("button", { name: "Tema claro" })).toHaveAttribute("aria-pressed", "true");
  await painel.getByRole("button", { name: "Sem animação" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-movimento", "reduzido");
});
