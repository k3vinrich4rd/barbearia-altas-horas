import { expect, test } from "@playwright/test";
import { API_URL, emailUnico, escolherHorario, horarioLivre, pagarComPixESimular, statusDaReservaGuest } from "./apoio";

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

  // serviço, dia e horário → dados (sem CEP) → reservar
  await escolherHorario(page, horario);
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
  const reservaId = page.url().split("/").pop()!.split("?")[0];
  expect(reservaId).toBe(corpoBff.id);
  expect(page.url()).not.toContain("codigo");
  const cookies = await page.context().cookies();
  expect(cookies.some((c) => c.name === "ah_visitante" && c.httpOnly && c.value.length > 0)).toBe(true);

  // aceite da política, CPF de teste, Pix, simular
  await expect(page.getByRole("timer")).toBeVisible();
  await pagarComPixESimular(page);

  // confirmação com o código
  await expect(page.getByRole("heading", { name: "Agendado! Seu horário está garantido." })).toBeVisible();
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
