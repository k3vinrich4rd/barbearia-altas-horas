import { expect, test } from "@playwright/test";
import { API_URL } from "./apoio";

// Fluxo sem repetição (Fatia 16). Links antigos já divulgados (ou do menu de antes) continuam chegando ao lugar certo.
test("A28: /#trajetoria, /#historia e /#barbeiro vão para quem-somos; /#duvidas vai para como-marcar", async ({ page }) => {
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
