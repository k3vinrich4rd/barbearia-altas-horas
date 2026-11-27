import { expect, test } from "@playwright/test";
import { API_URL } from "./apoio";

// Fluxo sem repetição (Fatia 16). Links antigos já divulgados (ou do menu de antes) continuam chegando ao lugar certo.
test("A28: /#trajetoria, /#historia e /#barbeiro vão para quem-somos; /#duvidas vai para como-marcar", async ({
  page,
}) => {
  for (const antigo of ["#trajetoria", "#historia", "#barbeiro"]) {
    await page.goto(`/${antigo}`);
    await expect(page, antigo).toHaveURL(/\/#quem-somos$/);
    const titulo = page.locator("#quem-somos").getByRole("heading", { level: 2 });
    await expect(titulo).toHaveText("Quase três décadas na mesma cadeira");
    await expect(titulo, antigo).toBeInViewport();
  }

  // dentro da home, um link para /#trajetoria (o menu de antes) também leva a quem-somos
  await page.goto("/");
  await page.evaluate(() => {
    window.location.hash = "trajetoria";
  });
  await expect(page).toHaveURL(/\/#quem-somos$/);
  await expect(page.locator("#quem-somos").getByRole("heading", { level: 2 })).toBeInViewport();

  await page.goto("/#duvidas");
  const duvidas = page.locator("#como-marcar #duvidas");
  await expect(duvidas).toHaveCount(1);
  await expect(duvidas).toBeInViewport();
});

test('A9: tocar uma linha de "O que fazemos" abre /agendar?servicoId=<id> com o serviço marcado; não há "Agendar este serviço"', async ({
  page,
  request,
}) => {
  const servicos: { id: string; nome: string }[] = await (await request.get(`${API_URL}/servicos`)).json();
  const corte = servicos.find((s) => s.nome === "Corte Clássico / Social") ?? servicos[0];

  await page.goto("/");
  const secao = page.locator("#servicos");
  await expect(secao.getByRole("listitem")).toHaveCount(servicos.length);
  await expect(secao.getByText(/Agendar este serviço/)).toHaveCount(0);
  const linha = secao.getByRole("link", { name: new RegExp(corte.nome.replace(/[/()]/g, "\\$&")) });
  expect((await linha.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  await linha.click();
  await expect(page).toHaveURL(new RegExp(`/agendar\\?servicoId=${corte.id}$`));
  await expect(page.getByRole("radio", { name: new RegExp(corte.nome.replace(/[/()]/g, "\\$&")) })).toBeChecked();
});

test("A27: /servicos vai para /#servicos, direto ou por um link de dentro do site", async ({ page }) => {
  await page.goto("/servicos");
  await expect(page).toHaveURL(/\/#servicos$/);
  await expect(page.locator("#servicos").getByRole("heading", { level: 2 })).toBeInViewport();

  // navegação do cliente (link antigo do menu ou de outra página): o redirect vale também sem recarregar
  await page.goto("/privacidade");
  await page.evaluate(() => {
    const a = document.createElement("a");
    a.href = "/servicos";
    a.textContent = "serviços";
    document.body.append(a);
  });
  await page.getByRole("link", { name: "serviços", exact: true }).click();
  await expect(page).toHaveURL(/\/#servicos$/);
  await expect(page.locator("#servicos").getByRole("heading", { level: 2 })).toBeInViewport();
});

test("A28 no celular (360 px): com a home aberta, o 01 · Quem somos do menu e um link para âncora antiga (/#barbeiro, /#trajetoria) levam a quem-somos", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const principal = page.getByRole("navigation", { name: "Principal" });
  const titulo = page.locator("#t-quem-somos");

  // o menu do F2 aponta direto para /#quem-somos
  await page.getByRole("button", { name: "Menu" }).click();
  await expect(principal.getByRole("link", { name: "Quem somos" })).toHaveAttribute("href", "/#quem-somos");
  await principal.getByRole("link", { name: "Quem somos" }).click();
  await expect(page).toHaveURL(/\/#quem-somos$/);
  await expect(titulo).toBeInViewport();

  // links antigos já divulgados ou guardados (o menu de antes): o clique no mesmo documento também vai para quem-somos
  for (const href of ["/#barbeiro", "/#trajetoria", "/#historia"]) {
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.evaluate((h) => {
      const a = document.createElement("a");
      a.href = h;
      a.id = "link-antigo-de-teste";
      a.textContent = "link antigo";
      document.querySelector("main")!.prepend(a);
    }, href);
    await expect(titulo, href).not.toBeInViewport();
    await page.locator("#link-antigo-de-teste").click();
    await expect(page, href).toHaveURL(/\/#quem-somos$/);
    await expect(titulo, href).toBeInViewport();
    await expect(titulo, href).toBeFocused();
    // sem animação: a rolagem não fica pela metade
    const topo = await titulo.evaluate((el) => el.getBoundingClientRect().top);
    expect(topo, href).toBeGreaterThanOrEqual(0);
    expect(topo, href).toBeLessThan(800);
  }
});
