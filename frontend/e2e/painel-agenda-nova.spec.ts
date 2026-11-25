import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Locator, type Page } from "@playwright/test";
import { entrarComoAdmin, horarioLivre, reservaPagaDeVisitante } from "./apoio";

// Fatia 14C, A53: a agenda nova do painel contra a API real, com axe (WCAG 2.1 A e AA) e só com o teclado.
// Fechar como "Concluído" exige que o horário já tenha começado, e a API real não aceita reserva no passado (D197): o
// teste aperta Concluído pelo teclado e confere a resposta da 7B ("depois do horário de início"), e fecha o atendimento
// cancelando pela barbearia, também só pelo teclado.

async function semViolacoes(page: Page, nome: string) {
  const resultado = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(
    resultado.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`),
    nome,
  ).toEqual([]);
}

/** Tab até o elemento ganhar o foco (sem mouse). */
async function tabAte(page: Page, alvo: Locator, limite = 250) {
  for (let i = 0; i < limite; i++) {
    if (await alvo.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press("Tab");
  }
  throw new Error("o foco não chegou ao elemento pelo Tab");
}

const somarDias = (dia: string, n: number) =>
  new Date(Date.parse(`${dia}T12:00:00Z`) + n * 86_400_000).toISOString().slice(0, 10);

const diasEntre = (de: string, ate: string) =>
  Math.round((Date.parse(`${ate}T12:00:00Z`) - Date.parse(`${de}T12:00:00Z`)) / 86_400_000);

test("A53: a agenda nova tem 0 violações do axe e dá para abrir um dia e fechar um atendimento só com o teclado", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  const nome = await reservaPagaDeVisitante(page, horario);
  await page.context().clearCookies();
  await entrarComoAdmin(page);

  await page.goto("/painel");
  await expect(page.locator(".calendario-dia[tabindex='0']")).toBeVisible();
  await semViolacoes(page, "agenda de hoje");

  // abrir o dia da reserva pelo calendário: Tab até o dia com o foco, setas até o dia certo, Enter
  const diaComFoco = page.locator(".calendario-dia[tabindex='0']");
  const inicio = (await diaComFoco.getAttribute("data-dia"))!;
  await tabAte(page, diaComFoco);
  for (let i = 1; i <= diasEntre(inicio, horario.dia); i++) {
    await page.keyboard.press("ArrowRight");
    // na virada do mês o calendário carrega o mês seguinte antes de o foco chegar
    await expect(page.locator(`[data-dia="${somarDias(inicio, i)}"]`)).toBeFocused();
  }
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(new RegExp(`data=${horario.dia}`));
  const item = page.getByRole("listitem").filter({ hasText: nome });
  await expect(item).toBeVisible();
  await expect(page.getByRole("rowheader", { name: horario.hora })).toBeVisible();
  await semViolacoes(page, "dia com a reserva");

  // Concluído pelo teclado: antes do início a API responde a mensagem da 7B
  await tabAte(page, item.getByRole("button", { name: "Concluído" }));
  await page.keyboard.press("Enter");
  await expect(page.getByText("Só dá para fechar o atendimento depois do horário de início.")).toBeVisible();

  // cancelar pela barbearia, só pelo teclado
  await tabAte(page, item.getByRole("button", { name: "Cancelar pela barbearia" }));
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await tabAte(page, page.getByLabel("Motivo do cancelamento"));
  await page.keyboard.type("Imprevisto (teste E2E pelo teclado)");
  await tabAte(page, page.getByRole("button", { name: "Confirmar cancelamento" }));
  await page.keyboard.press("Enter");
  await expect(item).toHaveAttribute("data-estado", "cancelado");
  await semViolacoes(page, "depois de cancelar");
});
