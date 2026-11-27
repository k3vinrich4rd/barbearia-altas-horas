import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { cadastrarCliente, entrarComoAdmin, horarioLivre, reservaDeVisitanteNaApi } from "./apoio";

// A54: axe com WCAG 2.1 A e AA nas telas da 7A, nos dois temas e também em 375 x 812.
// Zero violações serious e critical. As demais (moderate e minor) entram no relatório; exceção aceita só com motivo, aqui.
const EXCECOES_ACEITAS: Record<string, string> = {};

async function conferir(page: Page, nome: string, tema: "escuro" | "claro") {
  await page.evaluate((t) => (document.documentElement.dataset.theme = t), tema);
  const resultado = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  const graves = resultado.violations.filter(
    (v) => (v.impact === "serious" || v.impact === "critical") && !(v.id in EXCECOES_ACEITAS),
  );
  const leves = resultado.violations.filter((v) => v.impact !== "serious" && v.impact !== "critical");
  if (leves.length)
    await test.info().attach(`axe-leves-${nome}-${tema}`, {
      body: JSON.stringify(
        leves.map((v) => ({ id: v.id, impacto: v.impact, nos: v.nodes.length })),
        null,
        2,
      ),
      contentType: "application/json",
    });
  expect(
    graves.map(
      (v) =>
        `${v.id} (${v.impact}): ${v.nodes
          .slice(0, 3)
          .map((n) => n.target.join(" "))
          .join(" | ")}`,
    ),
    `${nome} no tema ${tema}`,
  ).toEqual([]);
}

test("A54: zero violações serious ou critical nas telas da 7A, nos dois temas e no celular", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  const reserva = await reservaDeVisitanteNaApi(request, horario);
  const paginasPublicas: [string, string][] = [
    ["home", "/"],
    ["servicos", "/servicos"],
    ["agendar", "/agendar"],
    [
      "dados do visitante",
      `/agendar/dados?${new URLSearchParams({ servicoId: horario.servicoId, barbeiroId: horario.barbeiroId, inicio: horario.inicio })}`,
    ],
    ["consulta da reserva", "/reserva"],
    ["cadastro", "/cadastro"],
    ["entrar", "/entrar"],
    ["politica de privacidade", "/privacidade"],
    ["acesso negado", "/acesso-negado"],
  ];

  for (const tamanho of [
    { width: 1280, height: 800 },
    { width: 375, height: 812 },
  ]) {
    await page.setViewportSize(tamanho);
    for (const [nome, caminho] of paginasPublicas) {
      await page.goto(caminho);
      await page.waitForLoadState("networkidle");
      for (const tema of ["escuro", "claro"] as const) await conferir(page, `${nome} ${tamanho.width}`, tema);
    }
  }

  // agendar com serviço e dia escolhidos (lista de horários na tela)
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/agendar");
  await page.getByRole("radio").first().check();
  const livre = await horarioLivre(request, { ignorar: [horario.inicio] });
  await page.locator("#data").fill(livre.dia);
  await expect(page.getByRole("button", { name: livre.hora, exact: true }).first()).toBeVisible();
  for (const tema of ["escuro", "claro"] as const) await conferir(page, "agendar com horarios", tema);

  // pagamento e confirmação do visitante (cookie do visitante já existe no BFF só depois de reservar pela UI; pagamento usa o código)
  await page.goto("/reserva");
  await page.getByLabel("Número da reserva").fill(reserva.id);
  await page.getByLabel("Código da reserva").fill(reserva.tokenGuest);
  await page.getByRole("button", { name: "Consultar" }).click();
  await expect(page.getByText("Aguardando pagamento").first()).toBeVisible();
  for (const tema of ["escuro", "claro"] as const) await conferir(page, "reserva consultada", tema);

  // telas da conta (logado)
  await cadastrarCliente(page, undefined, { marketing: true });
  for (const [nome, caminho] of [
    ["meus agendamentos", "/meus-agendamentos"],
    ["meus dados", "/minha-conta"],
    ["trocar senha", "/conta/trocar-senha"],
  ] as const) {
    for (const tamanho of [
      { width: 1280, height: 800 },
      { width: 375, height: 812 },
    ]) {
      await page.setViewportSize(tamanho);
      await page.goto(caminho);
      await page.waitForLoadState("networkidle");
      for (const tema of ["escuro", "claro"] as const) await conferir(page, `${nome} ${tamanho.width}`, tema);
    }
  }
  // checkout (pagamento) com uma reserva de cliente real
  await page.setViewportSize({ width: 1280, height: 800 });
  const outro = await horarioLivre(request, { ignorar: [horario.inicio] });
  await page.goto(
    `/agendar/dados?${new URLSearchParams({ servicoId: outro.servicoId, barbeiroId: outro.barbeiroId, inicio: outro.inicio })}`,
  );
  await page.getByRole("button", { name: "Reservar" }).click();
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  await expect(page.getByRole("timer")).toBeVisible();
  for (const tema of ["escuro", "claro"] as const) await conferir(page, "pagamento", tema);
});

test("A54: zero violações serious ou critical nas telas do painel, nos dois temas e no celular", async ({ page }) => {
  await entrarComoAdmin(page);
  const paginas: [string, string][] = [
    ["agenda", "/painel"],
    ["reserva manual", "/painel/reserva-manual"],
    ["pendencias", "/painel/pendencias"],
    ["cadastros", "/painel/cadastros"],
    ["auditoria", "/painel/auditoria"],
    ["alcance", "/painel/alcance"],
  ];
  for (const tamanho of [
    { width: 1280, height: 800 },
    { width: 375, height: 812 },
  ]) {
    await page.setViewportSize(tamanho);
    for (const [nome, caminho] of paginas) {
      await page.goto(caminho);
      await page.waitForLoadState("networkidle");
      for (const tema of ["escuro", "claro"] as const) await conferir(page, `painel ${nome} ${tamanho.width}`, tema);
    }
  }
  // cadastros com o editor de jornada e as folgas abertos
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/painel/cadastros");
  await page
    .getByRole("button", { name: /^Jornada e folgas de/ })
    .first()
    .click();
  await expect(page.getByRole("button", { name: "Salvar jornada" })).toBeVisible();
  for (const tema of ["escuro", "claro"] as const) await conferir(page, "painel cadastros com jornada", tema);
});

const ROTAS_ALVO = [
  "/",
  "/agendar",
  "/agendar/dados",
  "/servicos",
  "/reserva",
  "/entrar",
  "/cadastro",
  "/privacidade",
  "/nao-existe",
] as const;

const TABELA_AT1: { rota: string; nome: string | RegExp }[] = [
  { rota: "/", nome: "Ver serviços e preços" },
  { rota: "/entrar", nome: "Criar conta" },
  { rota: "/entrar", nome: "Agende sem cadastro" },
  { rota: "/cadastro", nome: "Entrar" },
  { rota: "/cadastro", nome: "Saiba mais na política de privacidade" },
  { rota: "/cadastro", nome: "Política de privacidade" },
  { rota: "/privacidade", nome: /./ }, // os links do sumário; medidos à parte
  { rota: "/nao-existe", nome: "Voltar para o início" },
  { rota: "/nao-existe", nome: "Agendar horário" },
  { rota: "/nao-existe", nome: "Consultar reserva" },
];

test("AT-1, AT-2, AT-4 e AT-5: alvos de toque de 44 px nos links soltos", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });

  // AT-1
  await page.goto("/");
  await expect(page.getByRole("link", { name: "Ver serviços e preços" })).toBeVisible();
  for (const item of TABELA_AT1) {
    if (item.rota !== "/privacidade") {
      if (page.url().replace(/https?:\/\/[^/]+/, "") !== item.rota) await page.goto(item.rota);
      const links = page.getByRole("link", { name: item.nome });
      await expect(links.first()).toBeVisible();
      const n = await links.count();
      for (let i = 0; i < n; i++) {
        const caixa = await links.nth(i).boundingBox();
        expect(caixa, `${item.rota} ${item.nome} #${i}`).toBeTruthy();
        expect(caixa!.height, `${item.rota} ${String(item.nome)} #${i} h=${caixa!.height}`).toBeGreaterThanOrEqual(44);
      }
    }
  }
  await page.goto("/privacidade");
  await expect(page.getByRole("navigation", { name: "Nesta página" })).toBeVisible();
  const sumario = page.getByRole("navigation", { name: "Nesta página" }).getByRole("link");
  expect(await sumario.count()).toBeGreaterThan(0);
  for (let i = 0; i < (await sumario.count()); i++) {
    const caixa = await sumario.nth(i).boundingBox();
    expect(caixa!.height, `sumário #${i}`).toBeGreaterThanOrEqual(44);
  }

  // AT-2: a, button e summary visíveis fora de um parágrafo
  for (const rota of ROTAS_ALVO) {
    await page.goto(rota);
    await page.waitForLoadState("networkidle");
    const baixos = await page.evaluate(() =>
      [...document.querySelectorAll("a, button, summary")]
        .filter((el) => {
          const html = el as HTMLElement;
          if (!html.checkVisibility()) return false;
          if (html.closest("p")) return false;
          const r = html.getBoundingClientRect();
          return r.height > 0 && r.width > 0 && r.height < 44;
        })
        .map((el) => {
          const r = el.getBoundingClientRect();
          return {
            tag: el.tagName,
            texto: (el.textContent ?? "").trim().slice(0, 80),
            h: Math.round(r.height * 10) / 10,
          };
        }),
    );
    expect(baixos, `AT-2 ${rota}: ${JSON.stringify(baixos)}`).toEqual([]);
  }

  // AT-4: 320 px e zoom 200%, sem rolagem horizontal
  await page.setViewportSize({ width: 320, height: 800 });
  for (const rota of ROTAS_ALVO) {
    await page.goto(rota);
    await page.waitForLoadState("networkidle");
    await page.evaluate(() => {
      document.body.style.zoom = "2";
    });
    const overflow = await page.evaluate(() => {
      const raiz = document.documentElement;
      return { scroll: raiz.scrollWidth, cliente: raiz.clientWidth };
    });
    expect(overflow.scroll, `AT-4 ${rota} ${JSON.stringify(overflow)}`).toBeLessThanOrEqual(overflow.cliente);
    await page.evaluate(() => {
      document.body.style.zoom = "";
    });
  }

  // AT-5: axe wcag22aa em 360 e 1280
  for (const tamanho of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize(tamanho);
    for (const rota of ROTAS_ALVO) {
      await page.goto(rota);
      await page.waitForLoadState("networkidle");
      const axe = await new AxeBuilder({ page }).withTags(["wcag22aa"]).analyze();
      expect(axe.violations, `AT-5 ${rota} ${tamanho.width}: ${JSON.stringify(axe.violations)}`).toEqual([]);
    }
  }
});
