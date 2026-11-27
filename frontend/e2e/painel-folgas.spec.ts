import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin, horarioLivre, reservaDeVisitanteNaApi } from "./apoio";

// C13 (Fatia 15, ADR 0015) com a API real: a página Férias e folgas do ADMIN, a prévia dos clientes marcados antes de
// salvar (E24) e o "Salvar e manter os horários" (manterReservas). No fim a folga é removida, para não tirar horário dos
// outros testes.
async function semViolacoes(page: Page, nome: string) {
  const resultado = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(
    resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`),
    nome,
  ).toEqual([]);
}

test("FF-3, FF-4, FF-9 e FF-10: o ADMIN marca uma folga num dia com cliente, vê a prévia antes de salvar, mantém a reserva e remove a folga; axe 0 em 360×800", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  await reservaDeVisitanteNaApi(request, horario);
  const [, mes, dia] = horario.dia.split("-");

  await page.setViewportSize({ width: 360, height: 800 });
  await entrarComoAdmin(page);
  await expect(
    page.getByRole("navigation", { name: "Painel" }).getByRole("link", { name: "Férias e folgas" }),
  ).toHaveAttribute("href", "/painel/folgas");
  // Outros testes cadastram barbeiros; abre direto no barbeiro do horário reservado (o mesmo link do Cadastros).
  await page.goto(`/painel/folgas?barbeiro=${horario.barbeiroId}`);
  await expect(page.getByRole("heading", { name: "Férias e folgas" })).toBeVisible();
  await semViolacoes(page, "lista de folgas 360");

  await page.getByRole("button", { name: "Marcar férias ou folga" }).click();
  const previa = page.waitForRequest((r) => r.method() === "GET" && r.url().includes("/folgas/conflitos"));
  await page.getByLabel("De", { exact: true }).fill(horario.dia);
  await page.getByLabel("Até", { exact: true }).fill(horario.dia);
  // FF-10: a prévia sai antes de salvar, com o dia inteiro escolhido
  expect(new URL((await previa).url()).searchParams.get("inicio")).toBe(`${horario.dia}T00:00:00-03:00`);
  await expect(page.getByText(/^\d+ clientes? marcados? nesse período$/)).toBeVisible();
  const lista = page.getByRole("list", { name: "Clientes marcados no período" });
  await expect(lista.getByText(new RegExp(`${dia}/${mes} às ${horario.hora}`))).toBeVisible();
  await semViolacoes(page, "prévia com clientes 360");

  await page.getByRole("button", { name: "Salvar e manter os horários" }).click();
  await expect(
    page.getByText(/^Folga lançada\. \d+ clientes? contin(ua|uam) marcados? nesse período\.$/),
  ).toBeVisible();
  await expect(page.getByRole("list", { name: "Reservas mantidas" }).getByRole("link").first()).toHaveAttribute(
    "href",
    new RegExp(`/painel\\?data=${horario.dia}&barbeiro=`),
  );

  // limpeza: remover a folga (com confirmação)
  await page
    .getByRole("button", { name: new RegExp(`^Remover de .*${dia}/${mes} até .*\\(o dia inteiro\\)$`) })
    .click();
  await page.getByRole("dialog").getByRole("button", { name: "Remover folga" }).click();
  await expect(page.getByText("Folga removida.")).toBeVisible();
});
