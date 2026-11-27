import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { API_URL, emailUnico, escolherHorario, horarioLivre, pagarComPixESimular, statusDaReservaGuest } from "./apoio";

async function conferirPasso(page: Page, atual: 1 | 2 | 3) {
  const nav = page.getByRole("navigation", { name: "Etapas do agendamento" });
  await expect(nav).toBeVisible();
  const itens = nav.getByRole("listitem");
  await expect(itens).toHaveCount(3);
  await expect(itens.nth(atual - 1)).toHaveAttribute("aria-current", "step");
  await expect(itens.nth(atual - 1)).toContainText(["1 · Horário", "2 · Pagamento", "3 · Confirmação"][atual - 1]);
  if (atual > 1) {
    await expect(itens.nth(0)).toContainText("concluído");
    await expect(itens.nth(0)).toContainText("✓");
  }
  await expect(page.getByText("Etapa 3 · Confirmação")).toHaveCount(0);
}

test("A57: o visitante vai da home à confirmação, consulta pelo código e o horário some para o próximo", async ({
  page,
  request,
  browser,
}) => {
  const horario = await horarioLivre(request);

  // home → agendar
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.getByRole("main").getByRole("link", { name: "Agendar horário" }).first().click();
  await expect(page).toHaveURL(/\/agendar$/);
  await conferirPasso(page, 1);

  // serviço, dia e horário → dados (sem CEP) → reservar
  await escolherHorario(page, horario);
  await conferirPasso(page, 1);
  await page.getByRole("button", { name: "Continuar sem conta" }).click();
  await page.getByLabel("Nome").fill("Visitante de Teste");
  await page.getByLabel("E-mail").fill(emailUnico("visitante"));
  await page.getByLabel("WhatsApp").fill("11900000003");
  const esperaBff = page.waitForResponse(
    (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/api/agendamentos/guest",
  );
  await page.getByRole("button", { name: "Reservar" }).click();
  const respBff = await esperaBff;
  expect(respBff.status()).toBe(201);
  const corpoBff = await respBff.json();
  expect(corpoBff).not.toHaveProperty("tokenGuest");
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  await conferirPasso(page, 2);
  const reservaId = page.url().split("/").pop()!.split("?")[0];
  expect(reservaId).toBe(corpoBff.id);
  expect(page.url()).not.toContain("codigo");
  const cookies = await page.context().cookies();
  expect(cookies.some((c) => c.name === "ah_visitante" && c.httpOnly && c.value.length > 0)).toBe(true);

  // aceite da política, CPF de teste, Pix, simular
  await expect(page.getByRole("timer")).toBeVisible();
  await pagarComPixESimular(page);

  // confirmação com o código
<<<<<<< HEAD
  await expect(page.getByRole("heading", { name: "Fechado! Seu horário está garantido." })).toBeVisible();
  await conferirPasso(page, 3);
  // CA-1: aviso de atraso pelo WhatsApp do barbeiro, sem mensagem pronta
  const resumo = await page.getByRole("region", { name: "Resumo" }).innerText();
  const com = resumo.match(/com ([^\n]+)/);
  expect(com, resumo).toBeTruthy();
  const primeiroNome = com![1].trim().split(" ")[0];
  await expect(page.getByText(`Vai se atrasar? Avise o ${primeiroNome} pelo WhatsApp.`)).toBeVisible();
  expect(page.getByText(/Prefere combinar pelo WhatsApp/)).toHaveCount(0);
  const avisar = page.getByRole("link", { name: "Avisar pelo WhatsApp (abre o WhatsApp)" });
  await expect(avisar).toBeVisible();
  const hrefAviso = await avisar.getAttribute("href");
  expect(hrefAviso).toMatch(/^https:\/\/wa\.me\/55\d{10,11}$/);
  expect(hrefAviso).not.toContain("text=");

  // CA-5: axe em 360 e 1280; botão à esquerda no desktop
  await page.setViewportSize({ width: 360, height: 800 });
  const overflow360 = await page.evaluate(() => {
    const raiz = document.documentElement;
    return { scroll: raiz.scrollWidth, cliente: raiz.clientWidth };
  });
  expect(overflow360.scroll, JSON.stringify(overflow360)).toBeLessThanOrEqual(overflow360.cliente);
  const axe360 = await new AxeBuilder({ page }).withTags(["wcag22aa"]).analyze();
  expect(axe360.violations, JSON.stringify(axe360.violations)).toEqual([]);
  await page.setViewportSize({ width: 1280, height: 800 });
  const axe1280 = await new AxeBuilder({ page }).withTags(["wcag22aa"]).analyze();
  expect(axe1280.violations, JSON.stringify(axe1280.violations)).toEqual([]);
  const caixaBotao = await avisar.boundingBox();
  const caixaTitulo = await page.getByRole("heading", { name: "E agora?" }).boundingBox();
  expect(caixaBotao && caixaTitulo).toBeTruthy();
  expect(Math.abs(caixaBotao!.x - caixaTitulo!.x)).toBeLessThan(8);

=======
  await expect(page.getByRole("heading", { name: "Agendado! Seu horário está garantido." })).toBeVisible();
>>>>>>> origin/develop
  const codigo = await page.getByLabel("Código", { exact: true }).inputValue();
  expect(codigo.length).toBeGreaterThan(10);
  expect(await statusDaReservaGuest(request, reservaId, codigo)).toBe("CONFIRMADO");

  // consulta pelo código em /reserva (num navegador novo: sem o cookie do visitante)
  const outro = await browser.newContext();
  const consulta = await outro.newPage();
  await consulta.goto("/reserva");
  await consulta.getByLabel("Número da reserva").fill(reservaId);
  await consulta.getByLabel("Código da reserva").fill(codigo);
  await consulta.getByRole("button", { name: "Consultar" }).click();
  await expect(consulta.getByText("Confirmado", { exact: true })).toBeVisible();
  // código errado: o mesmo texto de "não encontramos"
  await consulta.getByLabel("Número da reserva").fill(reservaId);
  await consulta.getByLabel("Código da reserva").fill("codigo-errado");
  await consulta.getByRole("button", { name: "Consultar" }).click();
  await expect(consulta.locator(".aviso-erro")).toContainText("Não encontramos esta reserva.");
  await outro.close();

  // o horário some da disponibilidade
  const d = await (
    await request.get(`${API_URL}/disponibilidade`, { params: { servicoId: horario.servicoId, data: horario.dia } })
  ).json();
  const livres: string[] = d.barbeiros.flatMap((b: { horarios: string[] }) => b.horarios);
  expect(livres).not.toContain(horario.inicio);

  // um segundo visitante no mesmo horário recebe a mensagem de horário ocupado
  const segundo = await browser.newContext();
  const p2 = await segundo.newPage();
  await p2.goto(
    `/agendar/dados?${new URLSearchParams({ servicoId: horario.servicoId, barbeiroId: horario.barbeiroId, inicio: horario.inicio })}`,
  );
  await p2.getByRole("button", { name: "Continuar sem conta" }).click();
  await p2.getByLabel("Nome").fill("Segundo Visitante");
  await p2.getByLabel("E-mail").fill(emailUnico("visitante2"));
  await p2.getByLabel("WhatsApp").fill("11900000004");
  await p2.getByRole("button", { name: "Reservar" }).click();
  await expect(p2).toHaveURL(/\/agendar\?/);
  await expect(p2.getByText("Alguém pegou esse horário agorinha. Escolha outro logo abaixo.")).toBeVisible();
  await segundo.close();
});

test("IP-6 e IP-7: o indicador não causa rolagem horizontal em 320 px com zoom 200%, e o axe passa em 360 e 1280", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.goto("/agendar");
  await conferirPasso(page, 1);
  await page.evaluate(() => {
    document.body.style.zoom = "2";
  });
  const overflow = await page.evaluate(() => {
    const raiz = document.documentElement;
    return { scroll: raiz.scrollWidth, cliente: raiz.clientWidth };
  });
  expect(overflow.scroll, JSON.stringify(overflow)).toBeLessThanOrEqual(overflow.cliente);
  await page.evaluate(() => {
    document.body.style.zoom = "";
  });
  for (const { width, height } of [
    { width: 360, height: 800 },
    { width: 1280, height: 800 },
  ]) {
    await page.setViewportSize({ width, height });
    await page.goto("/agendar");
    const resultado = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
      .analyze();
    expect(resultado.violations, `${width}x${height}: ${JSON.stringify(resultado.violations, null, 2)}`).toEqual([]);
  }
});
