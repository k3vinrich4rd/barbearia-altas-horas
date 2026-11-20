import { expect, test } from "@playwright/test";
import { CPF_DE_TESTE, emailUnico, escolherHorario, horarioLivre, statusDaReservaGuest } from "./apoio";

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
  await expect(outra.getByRole("alert")).toBeVisible();
  await expect(outra).toHaveURL(new RegExp(`/pagamento-simulado/${cobrancaId}$`));
  await semCookie.close();

  // o dono abre a rota de verdade, sem campo de cartão, e confirma
  await link.click();
  await expect(page).toHaveURL(new RegExp(`/pagamento-simulado/${cobrancaId}$`));
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Pagamento com cartão");
  await expect(page.locator('input[autocomplete^="cc-"]')).toHaveCount(0);
  await page.getByRole("button", { name: ROTULO_CONFIRMAR }).click();
  await expect(page).toHaveURL(new RegExp(`/agendar/confirmacao/${reservaId}`), { timeout: 30_000 });
  await expect(page.getByRole("heading", { name: "Horário confirmado" })).toBeVisible();
  const codigo = await page.getByLabel("Código", { exact: true }).inputValue();
  expect(await statusDaReservaGuest(request, reservaId, codigo)).toBe("CONFIRMADO");
});
