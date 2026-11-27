import { expect, test, type Page } from "@playwright/test";
import { CPF_DE_TESTE, emailUnico, escolherHorario, horarioLivre, statusDaReservaGuest } from "./apoio";

async function conferirPasso(page: Page, atual: 1 | 2 | 3) {
  const nav = page.getByRole("navigation", { name: "Etapas do agendamento" });
  await expect(nav).toBeVisible();
  const itens = nav.getByRole("listitem");
  await expect(itens).toHaveCount(3);
  await expect(itens.nth(atual - 1)).toHaveAttribute("aria-current", "step");
  await expect(itens.nth(atual - 1)).toContainText(["1 · Horário", "2 · Pagamento", "3 · Confirmação"][atual - 1]);
  if (atual > 1) {
    await expect(itens.nth(0)).toContainText("concluído");
    await expect(itens.nth(0)).toContainText("✓");
  }
  await expect(page.getByText("Etapa 3 · Confirmação")).toHaveCount(0);
}

const ROTULO_CONFIRMAR = "Ver a tela de confirmação (simula o pagamento aprovado)";

test("A63: o cartão simulado abre /pagamento-simulado/{cobrancaId} de verdade, confirma e chega à confirmação; sem o cookie dá erro e não redireciona", async ({
  page,
  request,
  browser,
}) => {
  const horario = await horarioLivre(request);

  // reserva de visitante pela tela
  await page.goto("/agendar");
  await escolherHorario(page, horario);
  await page.getByRole("button", { name: "Continuar sem conta" }).click();
  await page.getByLabel("Nome").fill("Visitante do Cartão");
  await page.getByLabel("E-mail").fill(emailUnico("cartao"));
  await page.getByLabel("WhatsApp").fill("11900000005");
  await page.getByRole("button", { name: "Reservar" }).click();
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  await conferirPasso(page, 2);
  const reservaId = page.url().split("/").pop()!.split("?")[0];

  // cartão de crédito: a API devolve o urlCheckout do GatewaySimulado
  await page.getByLabel(/Li e aceito/).check();
  await page.getByLabel("CPF").fill(CPF_DE_TESTE);
  await page.getByLabel("Cartão de crédito").check();
  await page.getByRole("button", { name: "Pagar" }).click();
  const link = page.getByRole("link", { name: "Ir para o pagamento com cartão" });
  await expect(link).toBeVisible();
  const href = (await link.getAttribute("href"))!;
  expect(href).toMatch(/\/pagamento-simulado\/[0-9a-f-]{36}$/);
  const cobrancaId = href.split("/").pop()!;

  // sem o cookie do visitante (outro navegador): erro na tela, sem redirecionar
  const semCookie = await browser.newContext();
  const outra = await semCookie.newPage();
  await outra.goto(`/pagamento-simulado/${cobrancaId}`);
  await outra.getByRole("button", { name: ROTULO_CONFIRMAR }).click();
  await expect(outra.getByRole("alert").filter({ hasText: "Entre na sua conta" })).toBeVisible();
  await expect(outra).toHaveURL(new RegExp(`/pagamento-simulado/${cobrancaId}$`));
  await semCookie.close();

  // o dono abre a rota de verdade, sem campo de cartão, e confirma
  await link.click();
  await expect(page).toHaveURL(new RegExp(`/pagamento-simulado/${cobrancaId}$`));
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Pagamento com cartão");
  await conferirPasso(page, 2);
  await expect(page.locator('input[autocomplete^="cc-"]')).toHaveCount(0);
  await page.getByRole("button", { name: ROTULO_CONFIRMAR }).click();
  await expect(page).toHaveURL(new RegExp(`/agendar/confirmacao/${reservaId}`), { timeout: 30_000 });
  await expect(page.getByRole("heading", { name: "Agendado! Seu horário está garantido." })).toBeVisible();
  await conferirPasso(page, 3);
  const codigo = await page.getByLabel("Código", { exact: true }).inputValue();
  expect(await statusDaReservaGuest(request, reservaId, codigo)).toBe("CONFIRMADO");
});
