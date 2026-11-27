import { expect, test } from "@playwright/test";

// A home conta a história da barbearia (1994, 1997, 2017, hoje) com interações que precisam funcionar no navegador
// de verdade e só com o teclado: linha do tempo, dúvidas em <details>, galeria (foto do salão) e o painel de acessibilidade.
test("home: linha do tempo e dúvidas pelo teclado, galeria e tema claro no navegador de verdade", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Seu horário com o Eduardo, garantido.");
  const trajetoria = page.locator("#trajetoria");
  await expect(
    trajetoria.getByText(
      "Em 1994, o Carlos Eduardo Candido, o Eduardo, começou no ofício aprendendo com o primeiro mestre.",
      { exact: false },
    ),
  ).toBeVisible();
  await expect(
    trajetoria.getByText("O filho, Kevin, criou esta agenda para que cada cliente marque do seu jeito."),
  ).toBeVisible();
  await expect(
    trajetoria.getByText("é só chamar o Eduardo no WhatsApp, que ele mesmo marca o seu horário."),
  ).toBeVisible();
  await expect(trajetoria.getByText("terça a sábado")).toHaveCount(0);
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

  // galeria (27/11/2026): só a foto do salão, em destaque, sem visualizador (nada para folhear)
  const galeria = page.locator("#galeria");
  await expect(galeria.getByRole("heading", { level: 2 })).toHaveText("O salão da Altas Horas");
  await expect(galeria.getByRole("img")).toHaveCount(1);
  await expect(galeria.getByRole("button")).toHaveCount(0);

  // painel de acessibilidade: tema claro e sem animação mudam o <html> e marcam o botão
  const painel = page.getByRole("region", { name: "Recursos de acessibilidade" });
  await painel.getByRole("button", { name: "Tema claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "claro");
  await expect(painel.getByRole("button", { name: "Tema claro" })).toHaveAttribute("aria-pressed", "true");
  await painel.getByRole("button", { name: "Sem animação" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-movimento", "reduzido");
});
