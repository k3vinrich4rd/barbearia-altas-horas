import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// C8 (Fatia 15, E16 a E19) com a API real: o seed tem a jornada de terça a sábado, das 7h às 20h com o vão das 12h às 13h.
// Na tira de 7 dias sempre há um domingo e uma segunda: os dois dizem "Fechado" e não se escolhem.
test("AF-10, AF-11 e AF-13: domingo e segunda 'Fechado' na tira, a faixa 'Fechado · 12h às 13h' no dia aberto e 0 violações do axe", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/agendar");
  await page.getByRole("radio").first().check();

  const tira = page.getByRole("group", { name: "Próximos dias" });
  const fechados = tira.getByRole("button", { name: /^(dom|seg) \d+\s?,\s?Fechado$/ });
  await expect(fechados).toHaveCount(2);
  for (const chip of await fechados.all()) {
    await expect(chip).toHaveAttribute("aria-disabled", "true");
    await expect(chip).toContainText("Fechado");
  }
  // o toque no dia fechado não troca o dia escolhido
  const escolhido = tira.locator('[aria-pressed="true"]');
  await expect(escolhido).toHaveCount(1);
  const antes = await escolhido.textContent();
  await fechados.first().click({ force: true });
  await expect(tira.locator('[aria-pressed="true"]')).toHaveText(antes ?? "");

  // o dia aberto mostra o vão do almoço como "Fechado", sem a palavra almoço, e o expediente
  const horarios = page.locator('section[aria-labelledby="passo-horario"]');
  await expect(horarios.getByText("Fechado · 12h às 13h")).toBeVisible();
  await expect(horarios.getByText(/^Atende das 7h às 20h$/)).toBeVisible();
  await expect(horarios.getByText(/almoço/i)).toHaveCount(0);

  for (const tamanho of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize(tamanho);
    const resultado = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
      .analyze();
    expect(
      resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`),
      `/agendar ${tamanho.width}px`,
    ).toEqual([]);
  }
});
