import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { emailUnico } from "./apoio";

// Fluxo de agendar em poucos toques (Fatia 12C). A reserva até a confirmação, com Pix e consulta, continua provada no
// A57 de e2e-visitante.spec.ts e nos de cliente e cartão: aqui o que a 12C mudou.
test("A23: no celular, da home ao formulário de dados são no máximo 4 toques (Agendar horário, serviço, horário, Continuar)", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  let toques = 0;

  await page.locator(".barra-agendar").getByRole("link", { name: "Agendar horário" }).click();
  toques++;
  await expect(page).toHaveURL(/\/agendar$/);

  await page.getByRole("radio").first().check();
  toques++;

  // o próximo dia livre e, com um só barbeiro, o barbeiro já vêm escolhidos: nada a tocar além do horário
  await expect(page.getByRole("radiogroup", { name: /Barbeiro/ })).toHaveCount(0);
  const chip = page.locator(".chip-horario:not(.chip-indisponivel)").first();
  await expect(chip).toBeVisible();
  await chip.click();
  toques++;

  await page.getByRole("button", { name: "Continuar", exact: true }).click();
  toques++;
  await expect(page).toHaveURL(/\/agendar\/dados\?/);
  expect(toques).toBeLessThanOrEqual(4);
});

test("A22: da home ao pagamento, as formas de reservar e o prazo vindo do expiraEm continuam como antes", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/agendar");
  // WhatsApp com reserva manual (6D), as contas e o visitante
  await expect(page.getByText("Prefere combinar pelo WhatsApp?")).toBeVisible();
  await page.getByRole("button", { name: "Menu", exact: true }).click(); // no celular o menu fica recolhido
  await expect(page.getByRole("link", { name: "Criar conta" })).toHaveAttribute("href", "/cadastro");
  await page.getByRole("button", { name: "Fechar menu" }).click();

  await page.getByRole("radio").first().check();
  const chip = page.locator(".chip-horario:not(.chip-indisponivel)").first();
  await chip.click();
  await page.getByRole("button", { name: "Continuar", exact: true }).click();
  await expect(page).toHaveURL(/\/agendar\/dados\?/);

  await expect(page.getByRole("link", { name: "Entrar" }).last()).toBeVisible();
  await page.getByRole("button", { name: "Continuar sem conta" }).click();
  await page.getByLabel("Nome").fill("Visitante Fluido");
  await page.getByLabel("E-mail").fill(emailUnico("fluido"));
  await page.getByLabel("WhatsApp").fill("11900000007");
  await page.getByRole("button", { name: "Reservar" }).click();

  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  // o prazo é um cronômetro (expiraEm), com tempo restante até 60 min (D121), e não um número escrito na tela de agendar
  const cronometro = page.getByRole("timer");
  await expect(cronometro).toBeVisible();
  await expect(cronometro).toHaveText(/\d{2}:\d{2}/);
});

test("WA-3 e WA-7: pedir o horário pelo WhatsApp não cria reserva, mostra a nota de volta, e o axe passa", async ({
  page,
}) => {
  const pedidos: string[] = [];
  page.on("request", (req) => {
    const metodo = req.method();
    const caminho = new URL(req.url()).pathname;
    if (
      metodo === "POST" &&
      (caminho.endsWith("/agendamentos") ||
        caminho.endsWith("/agendamentos/guest") ||
        caminho.endsWith("/api/agendamentos") ||
        caminho.endsWith("/api/agendamentos/guest"))
    )
      pedidos.push(`${metodo} ${caminho}`);
  });

  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/agendar");
  await page.getByRole("radio").first().check();
  await page.locator(".chip-horario:not(.chip-indisponivel)").first().click();
  await page.getByRole("button", { name: "Continuar", exact: true }).click();
  await expect(page).toHaveURL(/\/agendar\/dados\?/);

  const link = page.getByRole("link", { name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)" });
  await expect(link).toBeVisible();
  const href = await link.getAttribute("href");
  expect(href, href ?? "").toMatch(/^https:\/\/wa\.me\/55\d{10,11}\?text=/);
  await link.click();
  const nota = page.getByRole("status").filter({ hasText: "Mandou a mensagem?" });
  await expect(nota).toBeVisible();
  await expect(page.getByRole("link", { name: "Escolher outro horário" })).toBeVisible();
  expect(pedidos, pedidos.join(", ")).toEqual([]);

  for (const { width, height } of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize({ width, height });
    const resultado = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
      .analyze();
    expect(resultado.violations, `${width}x${height}: ${JSON.stringify(resultado.violations, null, 2)}`).toEqual([]);
  }
});
