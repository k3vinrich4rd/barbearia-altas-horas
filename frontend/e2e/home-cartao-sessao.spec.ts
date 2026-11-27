import { expect, test, type Page } from "@playwright/test";
import { cadastrarCliente } from "./apoio";

// Fatia 16, decisão 6 (A29): com sessão, o cartão 1 de "Como marcar" mostra "Meus agendamentos". A sessão vem do BFF
// (/api/sessao, same-origin, sem cache), como no menu; o cookie HttpOnly não é lido pelo JavaScript.
const cartao1 = (page: Page) =>
  page.locator("#como-marcar article").filter({ has: page.getByRole("heading", { level: 3, name: "Criar conta e agendar" }) });

async function medirCls(page: Page) {
  await page.addInitScript(() => {
    const w = window as unknown as { __cls: number };
    w.__cls = 0;
    new PerformanceObserver((lista) => {
      for (const e of lista.getEntries() as unknown as { hadRecentInput: boolean; value: number }[])
        if (!e.hadRecentInput) w.__cls += e.value;
    }).observe({ type: "layout-shift", buffered: true });
  });
}

test("A29: sem sessão \"Criar conta ou entrar\"; com sessão \"Meus agendamentos\", sem deslocar o layout; erro no BFF vira sem sessão", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/#como-marcar");
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Criar conta ou entrar");
  await expect(cartao1(page).getByRole("link")).toHaveAttribute("href", "/entrar");
  const semSessao = await cartao1(page).boundingBox();

  await cadastrarCliente(page);
  // a resposta da sessão chega atrasada: o cartão começa sem sessão e troca o rótulo sem mudar de tamanho
  await medirCls(page);
  await page.route("**/api/sessao", async (rota) => {
    await new Promise((fim) => setTimeout(fim, 2500));
    await rota.continue();
  });
  await page.goto("/#como-marcar");
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Criar conta ou entrar");
  // o resto da home (serviços, fontes) assenta antes; daí em diante só a troca do cartão poderia deslocar algo
  await expect(page.locator("#servicos li").first()).toBeVisible();
  await page.waitForTimeout(500);
  await page.evaluate(() => ((window as unknown as { __cls: number }).__cls = 0));
  const antes = await cartao1(page).boundingBox();
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Meus agendamentos");
  const depois = await cartao1(page).boundingBox();
  expect(depois!.height).toBe(antes!.height);
  expect(depois!.y).toBe(antes!.y);
  expect(depois!.height).toBe(semSessao!.height);
  const cls = await page.evaluate(() => (window as unknown as { __cls: number }).__cls);
  expect(cls, "CLS com a troca do cartão").toBe(0);
  expect((await cartao1(page).getByRole("link").boundingBox())!.height).toBeGreaterThanOrEqual(44);
  await page.unroute("**/api/sessao");

  // o JavaScript da página não vê o cookie da sessão (HttpOnly)
  expect(await page.evaluate(() => document.cookie)).not.toMatch(/acesso|refresh|sessao/i);

  await cartao1(page).getByRole("link", { name: "Meus agendamentos" }).click();
  await expect(page).toHaveURL(/\/meus-agendamentos$/);

  // BFF com erro: estado sem sessão
  await page.route("**/api/sessao", (rota) => rota.fulfill({ status: 500, contentType: "application/problem+json", body: "{}" }));
  await page.goto("/#como-marcar");
  await page.waitForLoadState("networkidle");
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Criar conta ou entrar");
});

test("A29: depois do logout, ao voltar para a home (bfcache ou não), o cartão 1 aparece sem sessão", async ({ page, context }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await cadastrarCliente(page);
  await page.goto("/");
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Meus agendamentos");

  // sai em outra aba (mesmos cookies); esta página não fica sabendo
  const outra = await context.newPage();
  await outra.setViewportSize({ width: 1280, height: 800 });
  await outra.goto("/privacidade");
  await outra.getByRole("navigation", { name: "Conta" }).getByRole("button", { name: "Sair" }).click();
  await expect(outra.getByRole("navigation", { name: "Conta" }).getByRole("link", { name: "Entrar" })).toBeVisible();
  await outra.close();

  // a página voltou do bfcache (pageshow com persisted): pergunta de novo ao BFF
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true })));
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Criar conta ou entrar");
  await expect(cartao1(page).getByRole("link")).toHaveAttribute("href", "/entrar");

  // e pelo botão Voltar de verdade: com sessão na home, vai a outra página, sai em outra aba e volta
  await cadastrarCliente(page);
  await page.goto("/");
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Meus agendamentos");
  await page.goto("/privacidade");
  const terceira = await context.newPage();
  await terceira.setViewportSize({ width: 1280, height: 800 });
  await terceira.goto("/privacidade");
  await terceira.getByRole("navigation", { name: "Conta" }).getByRole("button", { name: "Sair" }).click();
  await expect(terceira.getByRole("navigation", { name: "Conta" }).getByRole("link", { name: "Entrar" })).toBeVisible();
  await terceira.close();
  await page.goBack();
  await expect(page).toHaveURL(/\/$/);
  await expect(cartao1(page).getByRole("link")).toHaveAccessibleName("Criar conta ou entrar");
  await expect(cartao1(page).getByRole("link")).toHaveAttribute("href", "/entrar");
});
