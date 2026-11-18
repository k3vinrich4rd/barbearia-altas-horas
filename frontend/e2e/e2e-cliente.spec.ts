import { expect, test } from "@playwright/test";
import {
  API_URL,
  SENHA_DE_TESTE,
  cadastrarCliente,
  emailUnico,
  escolherHorario,
  horarioLivre,
  pagarComPixESimular,
} from "./apoio";

test("A58: o cliente se cadastra, reserva e paga, controla o marketing, baixa os dados e não exclui com reserva paga", async ({
  page,
  request,
}) => {
  const email = await cadastrarCliente(page, emailUnico("cliente"), { cep: "01001-000", marketing: true });
  // o CEP pode achar ou degradar (os dois passam): o cadastro terminou de qualquer jeito
  await expect(page.getByRole("heading", { name: "Meus agendamentos" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sair" })).toBeVisible();

  // reservar e pagar (simulado)
  const horario = await horarioLivre(request);
  await escolherHorario(page, horario);
  await page.getByRole("button", { name: "Reservar" }).click();
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  await pagarComPixESimular(page);

  // Meus agendamentos mostra CONFIRMADO
  await page.goto("/meus-agendamentos");
  await expect(page.getByRole("listitem").filter({ hasText: "Confirmado" }).first()).toBeVisible();

  // marketing: estava aceito; revoga, vê na tela e na API
  await page.goto("/minha-conta");
  const caixa = page.getByRole("checkbox");
  await expect(caixa).toBeChecked();
  await caixa.click();
  await expect(caixa).not.toBeChecked();
  await expect(page.getByText("Marketing: não aceito.")).toBeVisible();
  const consentimentos = await (await page.request.get("/api/me/consentimentos")).json();
  const marketing = consentimentos
    .filter((c: { finalidade: string }) => c.finalidade === "MARKETING_CEP")
    .sort((a: { registradoEm: string }, b: { registradoEm: string }) =>
      b.registradoEm.localeCompare(a.registradoEm),
    )[0];
  expect(marketing.aceito).toBe(false);

  // baixar os dados: o arquivo tem os agendamentos
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "Baixar meus dados" }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^dados-altas-horas-\d{4}-\d{2}-\d{2}\.json$/);
  const dados = JSON.parse(await (await import("node:fs/promises")).readFile((await download.path())!, "utf8"));
  expect(dados.agendamentos.length).toBeGreaterThanOrEqual(1);

  // excluir com reserva paga: 409 explicado e nada é apagado
  await page.getByRole("button", { name: "Excluir minha conta" }).click();
  await page.getByLabel("Digite EXCLUIR para confirmar").fill("EXCLUIR");
  await page.getByRole("button", { name: "Excluir para sempre" }).click();
  await expect(page.getByText(/Você tem um agendamento em aberto/)).toBeVisible();
  await page.keyboard.press("Escape");
  expect((await page.request.get("/api/me")).status()).toBe(200);

  // sair e entrar de novo
  await page.getByRole("button", { name: "Sair" }).click();
  await expect(page.getByRole("link", { name: "Entrar" }).first()).toBeVisible();
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA_DE_TESTE);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/meus-agendamentos/);
});

test("A58: uma conta sem reserva exclui a conta e não consegue mais entrar", async ({ page }) => {
  const email = await cadastrarCliente(page, emailUnico("sem-reserva"));
  await page.goto("/minha-conta");
  await page.getByRole("button", { name: "Excluir minha conta" }).click();
  await page.getByLabel("Digite EXCLUIR para confirmar").fill("EXCLUIR");
  await page.getByRole("button", { name: "Excluir para sempre" }).click();
  await expect(page).toHaveURL(/\/\?aviso=conta-excluida/);
  await expect(page.getByText(/Conta excluída/)).toBeVisible();

  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA_DE_TESTE);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.locator(".aviso-erro")).toContainText("E-mail ou senha incorretos.");
  expect(API_URL).toBeTruthy();
});
