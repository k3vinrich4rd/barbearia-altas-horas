import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { cadastrarCliente } from "./apoio";

// Fatia 14B: o olhinho (CampoSenha) nas 4 telas com senha, escondida e visível, sem violação do axe (WCAG 2.1 A e AA).
const CODIGO_DE_FORMATO_VALIDO = "q3tD7mYb2uVn0pLwXc8sKfHrJ1aZgE5iO9vTyNdB4Mk"; // abrir o convite não chama a API (A36 da 10)

async function semViolacoes(page: Page, nome: string) {
  const resultado = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(
    resultado.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`),
    nome,
  ).toEqual([]);
}

async function conferirTela(page: Page, nome: string) {
  const botoes = page.getByRole("button", { name: "Mostrar a senha" });
  await expect(botoes.first()).toBeVisible();
  await semViolacoes(page, `${nome} com a senha escondida`);
  const quantos = await botoes.count();
  for (let i = 0; i < quantos; i++) await page.getByRole("button", { name: "Mostrar a senha" }).first().click();
  await expect(page.getByRole("button", { name: "Esconder a senha" })).toHaveCount(quantos);
  await semViolacoes(page, `${nome} com a senha visível`);
}

test("A33: Entrar, Cadastro, Trocar senha e Convite, com a senha escondida e visível: 0 violações do axe", async ({
  page,
}) => {
  await page.goto("/entrar");
  await conferirTela(page, "Entrar");

  await page.goto("/cadastro");
  await conferirTela(page, "Cadastro");

  await page.goto(`/convite#${CODIGO_DE_FORMATO_VALIDO}`);
  await conferirTela(page, "Convite");

  await cadastrarCliente(page);
  await page.goto("/conta/trocar-senha");
  await conferirTela(page, "Trocar senha");
});
