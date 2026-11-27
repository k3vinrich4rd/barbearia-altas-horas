import { expect, test } from "@playwright/test";
import { API_URL } from "./apoio";

// Home mais fluida (Fatia 12). A 12A cobre o link antigo; a 12B, a barra fixa, o serviço novo e o peso das imagens.
test("A4: o link antigo /#historia leva à Trajetória", async ({ page }) => {
  await page.goto("/#historia");
  await expect(page).toHaveURL(/#quem-somos$/); // Fatia 16 (A28): a Trajetória mora em "Quem somos"
  const trajetoria = page.locator("#trajetoria");
  await expect(trajetoria).toBeVisible();
  await expect(trajetoria.getByRole("heading", { level: 2 })).toHaveText("Quase três décadas na mesma cadeira");
  await expect(page.locator("#historia")).toHaveCount(0);
  // a janela rolou até o bloco: o título está dentro da área visível
  await expect(trajetoria.getByRole("heading", { level: 2 })).toBeInViewport();
});

test("A5 e A2 (Fatia 16, F2): no celular a barra começa escondida e inert com o herói na tela, aparece ao rolar, leva a /agendar e não cobre o rodapé; em tela larga ela não existe", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const aside = page.locator(".barra-agendar");
  const barra = aside.getByRole("link", { name: "Agendar horário" });
  await expect(aside).toHaveAttribute("data-escondida", "true");
  await expect(aside).toHaveAttribute("inert", "");
  // a página cresce quando os serviços e o barbeiro chegam: rola até o fim só depois, e confere a posição já estável
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect(page.locator("#onde-estamos")).toBeInViewport();
  await expect(aside).toHaveAttribute("data-escondida", "false");
  await expect(barra).toBeVisible();
  // a barra não cobre o último conteúdo do rodapé: o link "Voltar ao topo" fica acima dela
  const voltar = page.getByRole("link", { name: "Voltar ao topo" });
  await expect
    .poll(async () => {
      const topo = await voltar.boundingBox();
      const caixa = await barra.boundingBox();
      return Boolean(topo && caixa && topo.y + topo.height <= caixa.y);
    })
    .toBe(true);
  expect((await barra.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  // de volta ao herói, ela some de novo
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect(aside).toHaveAttribute("data-escondida", "true");
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect(aside).toHaveAttribute("data-escondida", "false");
  await barra.click();
  await expect(page).toHaveURL(/\/agendar$/);
  // nas telas de agendar a barra não aparece
  await expect(page.locator(".barra-agendar")).toHaveCount(0);

  for (const largura of [1024, 1280]) {
    await page.setViewportSize({ width: largura, height: 800 });
    await page.goto("/");
    await expect(page.locator(".barra-agendar")).toBeHidden();
  }
});

test("A15 (Fatia 16, F2): em /agendar, /entrar e /cadastro o topo não tem Agendar horário e o rodapé tem uma linha", async ({
  page,
}) => {
  for (const tamanho of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize(tamanho);
    for (const caminho of ["/agendar", "/entrar", "/cadastro"]) {
      await page.goto(caminho);
      await expect(page.getByRole("banner").getByRole("link", { name: "Agendar horário" }), caminho).toHaveCount(0);
      const rodape = page.getByRole("contentinfo");
      await expect(rodape.getByRole("link"), caminho).toHaveText(["Onde estamos", "Política de privacidade"]);
    }
    await page.goto("/agendar");
    await page.waitForLoadState("networkidle");
    await expect(page.locator('a[href^="https://wa.me/"]')).toHaveCount(1);
  }
});

test("A8: o cartão da Descoloração / Platinado (química) leva a /agendar com esse serviço já marcado", async ({
  page,
  request,
}) => {
  const servicos: { id: string; nome: string }[] = await (await request.get(`${API_URL}/servicos`)).json();
  const platinado = servicos.find((s) => s.nome === "Descoloração / Platinado (química)");
  expect(platinado, "a API traz o serviço novo (V10)").toBeDefined();

  await page.goto("/");
  const cartao = page.locator("#servicos li", { hasText: "Descoloração / Platinado (química)" });
  await expect(cartao).toContainText("60 min");
  await expect(cartao).toContainText("R$ 80,00");
  await cartao.getByRole("link", { name: /Agendar este serviço/ }).click();
  await expect(page).toHaveURL(new RegExp(`/agendar\\?servicoId=${platinado!.id}$`));
  await expect(page.getByRole("radio", { name: /Descoloração \/ Platinado/ })).toBeChecked();
});

test("A26: no celular a home não baixa as 15 fotos novas de uma vez e o peso inicial de imagens fica em até 600 KB", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  const imagens: { url: string; bytes: number }[] = [];
  page.on("response", async (r) => {
    const tipo = r.headers()["content-type"] ?? "";
    if (!tipo.startsWith("image/")) return;
    const corpo = await r.body().catch(() => Buffer.alloc(0));
    imagens.push({ url: r.url(), bytes: corpo.length });
  });
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  const total = imagens.reduce((soma, i) => soma + i.bytes, 0);
  const daGaleria = imagens.filter((i) => decodeURIComponent(i.url).includes("/assets/galeria/"));
  const unicas = new Set(daGaleria.map((i) => decodeURIComponent(i.url).replace(/&.*$/, "")));
  await test.info().attach("peso-de-imagens-inicial", {
    body: JSON.stringify({ totalBytes: total, imagens: imagens.length, fotosDaGaleria: unicas.size }, null, 2),
    contentType: "application/json",
  });
  expect(unicas.size, "fotos da galeria baixadas antes de rolar").toBeLessThan(15);
  expect(total, `peso de imagens baixado antes de rolar: ${total} bytes`).toBeLessThanOrEqual(600 * 1024);
});
