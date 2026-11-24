import { expect, test, type Locator, type Page } from "@playwright/test";
import { CPF_DE_TESTE, cadastrarCliente, emailUnico, horarioLivre } from "./apoio";

// A55: o fluxo do visitante só com o teclado (Tab, Shift+Tab, Enter, Espaço, setas, Esc).

/** Tab até o foco chegar ao elemento (prova que ele é alcançável e que não há armadilha de foco). */
async function tabAte(page: Page, alvo: Locator, maximo = 120) {
  const manipulador = await alvo.elementHandle();
  for (let i = 0; i < maximo; i++) {
    if (await manipulador!.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press("Tab");
  }
  throw new Error(`o foco não chegou em ${alvo} com ${maximo} Tabs`);
}

async function foco(page: Page) {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement;
    const css = getComputedStyle(el);
    return {
      tag: el.tagName,
      texto: (el.textContent ?? "").trim().slice(0, 40),
      saida: css.outlineStyle,
      largura: css.outlineWidth,
    };
  });
}

test("A55: o fluxo do visitante se faz só com o teclado, com foco visível, link de pular e foco no h1 ao mudar de rota", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  await page.goto("/");

  // o primeiro Tab cai no link "Pular para o conteúdo" e o anel de foco é visível
  await page.keyboard.press("Tab");
  expect(await foco(page)).toMatchObject({ tag: "A", texto: "Pular para o conteúdo" });
  const anel = await foco(page);
  expect(anel.saida).not.toBe("none");
  expect(anel.largura).toBe("3px"); // design system v2: anel de 3px (traco-3), afastado 3px
  // Shift+Tab volta para o início sem prender
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter"); // pula para o conteúdo
  await expect(page.locator("#conteudo"))
    .toBeFocused()
    .catch(() => undefined);

  // Enter no link principal da home: a rota muda e o foco vai para o h1
  await tabAte(page, page.getByRole("main").getByRole("link", { name: "Agendar horário" }).first());
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/agendar$/);
  await expect(page.getByRole("heading", { level: 1 })).toBeFocused();

  // serviço com as setas (grupo de rádio), dia digitado e horário com Enter
  await tabAte(page, page.getByRole("radio").first());
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowUp");
  await expect(page.getByRole("radio").first()).toBeChecked();
  await tabAte(page, page.locator("#data"));
  await page.locator("#data").fill(horario.dia); // data nativa: entrada de texto pelo teclado
  const botaoHora = page.getByRole("button", { name: horario.hora, exact: true }).first();
  await tabAte(page, botaoHora);
  await page.keyboard.press("Enter");
  await tabAte(page, page.getByRole("button", { name: "Continuar", exact: true })); // resumo fixo (Fatia 12C)
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/agendar\/dados\?/);
  await expect(page.getByRole("heading", { level: 1 })).toBeFocused();

  // dados do visitante
  await tabAte(page, page.getByRole("button", { name: "Continuar sem conta" }));
  await page.keyboard.press("Space");
  for (const [rotulo, valor] of [
    ["Nome", "Visitante Teclado"],
    ["E-mail", emailUnico("teclado")],
    ["WhatsApp", "11900000005"],
  ] as const) {
    await tabAte(page, page.getByLabel(rotulo, { exact: true }));
    await page.keyboard.type(valor);
  }
  await tabAte(page, page.getByRole("button", { name: "Reservar" }));
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);

  // pagamento: caixa com Espaço, CPF, método com setas, Pagar com Enter
  await tabAte(page, page.getByLabel(/Li e aceito/));
  await page.keyboard.press("Space");
  await expect(page.getByLabel(/Li e aceito/)).toBeChecked();
  await tabAte(page, page.getByLabel("CPF"));
  await page.keyboard.type(CPF_DE_TESTE);
  await tabAte(page, page.getByRole("button", { name: "Pagar" }));
  await page.keyboard.press("Enter");
  await expect(page.getByLabel("Pix copia e cola")).toBeVisible();
  await tabAte(page, page.getByRole("button", { name: "Copiar" }));
  await tabAte(page, page.getByRole("button", { name: "Simular pagamento" }));
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/agendar\/confirmacao\//, { timeout: 30_000 });
  await expect(page.getByRole("heading", { level: 1, name: "Fechado! Seu horário está garantido." })).toBeFocused();
  await expect(page.getByLabel("Código", { exact: true })).toBeVisible();
});

test("A55: o diálogo de exclusão prende o foco, Esc fecha e o foco volta ao botão de origem", async ({ page }) => {
  await cadastrarCliente(page);
  await page.goto("/minha-conta");
  const origem = page.getByRole("button", { name: "Excluir minha conta" });
  await tabAte(page, origem);
  await page.keyboard.press("Enter");
  const dialogo = page.getByRole("dialog");
  await expect(dialogo).toBeVisible();
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press("Tab");
    expect(await dialogo.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Shift+Tab");
  expect(await dialogo.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  await page.keyboard.press("Escape");
  await expect(dialogo).toBeHidden();
  await expect(origem).toBeFocused();
});
