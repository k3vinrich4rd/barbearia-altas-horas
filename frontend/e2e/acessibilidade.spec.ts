import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { cadastrarCliente, horarioLivre, reservaDeVisitanteNaApi } from "./apoio";

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
