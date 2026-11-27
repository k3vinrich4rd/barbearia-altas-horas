import { expect, test, type Page } from "@playwright/test";
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

// Revisão do #154: o clique real no menu (Link do Next), da home e de outra rota, a 360 e a 1280, rola até a seção e leva
// o foco ao título dela. Antes, a URL ficava certa, mas a seção não entrava na vista e o foco ia ao h1 do herói.
const TAMANHOS = [
  { width: 360, height: 800 },
  { width: 1280, height: 800 },
];
const ORIGENS = ["/", "/privacidade"];

async function clicarNoMenu(page: Page, nome: string) {
  const botao = page.getByRole("button", { name: "Menu" });
  if (await botao.isVisible()) await botao.click();
  await page.getByRole("navigation", { name: "Principal" }).getByRole("link", { name: nome }).click();
}

async function clicarEmLinkDaPagina(page: Page, href: string) {
  await page.evaluate((h) => {
    const a = document.createElement("a");
    a.href = h;
    a.id = "link-de-teste";
    a.textContent = `link ${h}`;
    document.querySelector("main")!.prepend(a);
  }, href);
  await page.locator("#link-de-teste").click();
}

/** A seção está na tela e o foco está no título dela. */
async function conferirChegada(page: Page, secao: string, titulo: string, rotulo: string) {
  await expect(page.locator(`#${titulo}`), rotulo).toBeInViewport();
  await expect(page.locator(`#${secao}`), rotulo).toBeInViewport();
  await expect.poll(() => page.evaluate(() => document.activeElement?.id), { message: rotulo }).toBe(titulo);
}

for (const tamanho of TAMANHOS) {
  for (const origem of ORIGENS) {
    const caso = `${tamanho.width} px, saindo de ${origem}`;

    test(`A9, A10 e A17 (${caso}): o menu "O que fazemos" leva a 02 com foco no h2; linhas com preço, duração 1× e sem galeria`, async ({
      page,
      request,
    }) => {
      const servicos: { id: string; duracaoMin: number }[] = await (await request.get(`${API_URL}/servicos`)).json();
      await page.setViewportSize(tamanho);
      await page.emulateMedia({ reducedMotion: "reduce" });
      await page.goto(origem);
      await page.waitForLoadState("networkidle");
      await clicarNoMenu(page, "O que fazemos");
      await expect(page).toHaveURL(/\/#servicos$/);
      await conferirChegada(page, "servicos", "servicos-titulo", caso);

      const secao = page.locator("#servicos");
      // A9: cada linha é o link para /agendar?servicoId=<id>; não há "Agendar este serviço"
      await expect(secao.getByRole("listitem")).toHaveCount(servicos.length);
      for (const s of servicos) await expect(secao.locator(`a[href="/agendar?servicoId=${s.id}"]`)).toHaveCount(1);
      await expect(secao.getByText(/Agendar este serviço/)).toHaveCount(0);
      // A10: com a mesma duração em todos, ela aparece 1× no apoio
      if (new Set(servicos.map((s) => s.duracaoMin)).size === 1) {
        await expect(secao.getByText(new RegExp(`${servicos[0].duracaoMin} min`))).toHaveCount(1);
      }
      // A17 (cenário A): sem galeria nem link "Galeria"
      await expect(page.locator("#galeria")).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Galeria", exact: true })).toHaveCount(0);
    });

    test(`A27 (${caso}): um link para /servicos leva a 02 com foco no h2`, async ({ page }) => {
      await page.setViewportSize(tamanho);
      await page.emulateMedia({ reducedMotion: "reduce" });
      await page.goto(origem);
      await page.waitForLoadState("networkidle");
      await clicarEmLinkDaPagina(page, "/servicos");
      await expect(page).toHaveURL(/\/#servicos$/);
      await conferirChegada(page, "servicos", "servicos-titulo", caso);
    });

    test(`menu (${caso}): Quem somos, Como marcar e Onde estamos levam à seção com foco no título`, async ({
      page,
    }) => {
      await page.setViewportSize(tamanho);
      await page.emulateMedia({ reducedMotion: "reduce" });
      for (const [nome, secao, titulo] of [
        ["Quem somos", "quem-somos", "t-quem-somos"],
        ["Como marcar", "como-marcar", "t-como-marcar"],
        ["Onde estamos", "onde-estamos", "rodape-onde"],
      ]) {
        await page.goto(origem);
        await page.waitForLoadState("networkidle");
        await clicarNoMenu(page, nome);
        await expect(page).toHaveURL(new RegExp(`/#${secao}$`));
        await conferirChegada(page, secao, titulo, `${nome}, ${caso}`);
      }
    });

    test(`A28 (${caso}): links antigos /#barbeiro, /#trajetoria, /#historia e /#duvidas levam ao lugar certo com foco no título`, async ({
      page,
    }) => {
      await page.setViewportSize(tamanho);
      await page.emulateMedia({ reducedMotion: "reduce" });
      for (const [href, secao, titulo] of [
        ["/#barbeiro", "quem-somos", "t-quem-somos"],
        ["/#trajetoria", "quem-somos", "t-quem-somos"],
        ["/#historia", "quem-somos", "t-quem-somos"],
        ["/#duvidas", "duvidas", "t-antes-de-pagar"],
      ]) {
        await page.goto(origem);
        await page.waitForLoadState("networkidle");
        await clicarEmLinkDaPagina(page, href);
        await expect(page, href).toHaveURL(new RegExp(`/#${secao}$`));
        await conferirChegada(page, secao, titulo, `${href}, ${caso}`);
      }
    });
  }
}
