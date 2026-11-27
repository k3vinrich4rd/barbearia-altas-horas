import AxeBuilder from "@axe-core/playwright";
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
  await expect(page.getByRole("heading", { level: 1, name: "Agendado! Seu horário está garantido." })).toBeFocused();
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

// RF-1 a RF-3, RF-5 e RF-7: no celular o resumo compacto não esconde o foco (WCAG 2.4.11).
// O CI deste repositório roda só Chromium (Desktop Chrome), como o restante da suíte.

async function caixaDoFocoEscondidaPeloResumo(page: Page) {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    const resumo = document.querySelector("aside.resumo-fixo") as HTMLElement | null;
    if (!el || el === document.body || el === document.documentElement || !resumo) return null;
    if (resumo.contains(el)) return null;
    const f = el.getBoundingClientRect();
    const r = resumo.getBoundingClientRect();
    if (f.width === 0 || f.height === 0) return null;
    // a caixa toda está abaixo (ou a partir) do topo do resumo: ficaria escondida atrás da barra
    if (f.top >= r.top) {
      return {
        texto: (el.textContent ?? el.getAttribute("aria-label") ?? el.tagName).trim().slice(0, 80),
        top: Math.round(f.top),
        bottom: Math.round(f.bottom),
        resumoTop: Math.round(r.top),
      };
    }
    return null;
  });
}

async function tabDoServicoAteContinuar(page: Page) {
  const primeiro = page.getByRole("radio").first();
  await primeiro.focus();
  const continuar = page.getByRole("button", { name: "Continuar", exact: true });
  const handle = await continuar.elementHandle();
  for (let i = 0; i < 80; i++) {
    const escondido = await caixaDoFocoEscondidaPeloResumo(page);
    expect(escondido, escondido ? JSON.stringify(escondido) : undefined).toBeNull();
    if (await handle!.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press("Tab");
  }
  throw new Error("o foco não chegou em Continuar");
}

test.describe("resumo fixo não cobre o foco", () => {
  test.use({ viewport: { width: 360, height: 800 } });

  test("RF-1 e RF-2: em 360×800, Tab do serviço ao Continuar não esconde o foco e a barra tem no máximo 128 px (176 px com A+)", async ({
    page,
    request,
  }) => {
    const horario = await horarioLivre(request);
    await page.goto("/agendar");
    await page.getByRole("radio").first().click();
    await page.locator("#data").fill(horario.dia);
    await page.getByRole("button", { name: horario.hora, exact: true }).first().click();

    const altura = await page.locator("aside.resumo-fixo").evaluate((el) => el.getBoundingClientRect().height);
    expect(altura, `altura sem A+: ${altura}`).toBeLessThanOrEqual(128);

    await tabDoServicoAteContinuar(page);

    await page.getByRole("button", { name: /Acessibilidade/ }).click();
    const aumentar = page.getByRole("button", { name: "Aumentar o tamanho do texto" });
    await aumentar.click();
    await aumentar.click();
    await aumentar.click(); // 150%, o passo máximo
    const alturaMais = await page.locator("aside.resumo-fixo").evaluate((el) => el.getBoundingClientRect().height);
    expect(alturaMais, `altura com A+: ${alturaMais}`).toBeLessThanOrEqual(176);
  });

  test("RF-3: em /agendar/dados no modo sem conta, o resumo não esconde nome, e-mail, WhatsApp, caixinha nem Reservar", async ({
    page,
    request,
  }) => {
    const horario = await horarioLivre(request);
    await page.goto("/agendar");
    await page.getByRole("radio").first().click();
    await page.locator("#data").fill(horario.dia);
    await page.getByRole("button", { name: horario.hora, exact: true }).first().click();
    await page.getByRole("button", { name: "Continuar", exact: true }).click();
    await expect(page).toHaveURL(/\/agendar\/dados\?/);
    await page.getByRole("button", { name: "Continuar sem conta" }).click();

    await page.getByLabel("Nome", { exact: true }).focus();
    const reservar = page.getByRole("button", { name: "Reservar" });
    const handle = await reservar.elementHandle();
    for (let i = 0; i < 40; i++) {
      const escondido = await caixaDoFocoEscondidaPeloResumo(page);
      expect(escondido, escondido ? JSON.stringify(escondido) : undefined).toBeNull();
      if (await handle!.evaluate((el) => el === document.activeElement)) return;
      await page.keyboard.press("Tab");
    }
    throw new Error("o foco não chegou em Reservar");
  });

  test("RF-5: axe (WCAG 2.2 AA) em /agendar e /agendar/dados, em 360×800 e em 1280×800", async ({ page, request }) => {
    const horario = await horarioLivre(request);
    async function axeNaRota(largura: number, altura: number) {
      await page.setViewportSize({ width: largura, height: altura });
      const resultado = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
        .analyze();
      expect(resultado.violations, JSON.stringify(resultado.violations, null, 2)).toEqual([]);
    }
    await page.goto("/agendar");
    await axeNaRota(360, 800);
    await axeNaRota(1280, 800);
    await page.setViewportSize({ width: 360, height: 800 });
    await page.getByRole("radio").first().click();
    await page.locator("#data").fill(horario.dia);
    await page.getByRole("button", { name: horario.hora, exact: true }).first().click();
    await page.getByRole("button", { name: "Continuar", exact: true }).click();
    await expect(page).toHaveURL(/\/agendar\/dados\?/);
    await axeNaRota(360, 800);
    await axeNaRota(1280, 800);
  });

  test("RF-7: em 320 px com zoom de 200%, /agendar não rola na horizontal, também depois do serviço e do horário", async ({
    page,
    request,
  }) => {
    const horario = await horarioLivre(request);
    await page.setViewportSize({ width: 320, height: 800 });
    await page.goto("/agendar");
    const medir = async () => {
      await page.evaluate(() => {
        document.body.style.zoom = "2";
      });
      const overflow = await page.evaluate(() => {
        const raiz = document.documentElement;
        return { scroll: raiz.scrollWidth, cliente: raiz.clientWidth };
      });
      await page.evaluate(() => {
        document.body.style.zoom = "";
      });
      return overflow;
    };
    const noInicio = await medir();
    expect(noInicio.scroll, `no início: ${JSON.stringify(noInicio)}`).toBeLessThanOrEqual(noInicio.cliente);

    await page.getByRole("radio").first().click();
    await page.locator("#data").fill(horario.dia);
    await expect(page.locator(".dias-tira .dia-chip-nota").filter({ hasText: /livre|Fechado|sem horário/ }).first()).toBeVisible();
    await page.getByRole("button", { name: horario.hora, exact: true }).first().click();
    await expect(page.locator(".resumo-fixo")).toContainText(horario.hora);

    const depois = await medir();
    expect(depois.scroll, `depois do serviço, do horário e das notas da tira: ${JSON.stringify(depois)}`).toBeLessThanOrEqual(depois.cliente);
  });
});

test("RF-4: em 1280×800 o resumo continua sticky, com Serviço, Quando e Total visíveis", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/agendar");
  const resumo = page.locator("aside.resumo-fixo");
  await expect(resumo.getByText("Serviço", { exact: true })).toBeVisible();
  await expect(resumo.getByText("Quando", { exact: true })).toBeVisible();
  await expect(resumo.getByText("Total", { exact: true })).toBeVisible();
  await expect(resumo).toHaveCSS("position", "sticky");
});
