import { expect, test } from "@playwright/test";
import { CPF_DE_TESTE, emailUnico, escolherHorario, horarioLivre, statusDaReservaGuest } from "./apoio";

// A27 (Fatia 14A): o fluxo inteiro com a API real. O prazo é de 3 minutos (o piso do contrato), então o teste espera o último
// minuto de verdade (cerca de 2 minutos) antes de tocar em "Acrescentar 1 minuto". Por isso o timeout é maior que o dos outros.
test.setTimeout(300_000);

const segundosDoContador = async (texto: string | null) => {
  const [min, seg] = (texto ?? "00:00").split(":").map(Number);
  return min * 60 + seg;
};

test("A27: o visitante reserva, gera o Pix, espera o último minuto, acrescenta 1 minuto e a simulação do pagamento confirma a reserva", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  await escolherHorario(page, horario);
  await page.getByRole("button", { name: "Continuar sem conta" }).click();
  await page.getByLabel("Nome").fill("Visitante de Teste");
  await page.getByLabel("E-mail").fill(emailUnico("prorrogacao"));
  await page.getByLabel("WhatsApp").fill("11900000005");
  await page.getByRole("button", { name: "Reservar" }).click();
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  const reservaId = page.url().split("/").pop()!.split("?")[0];

  // Pix gerado, mas ainda não pago
  await page.getByLabel(/Li e aceito/).check();
  await page.getByLabel("CPF").fill(CPF_DE_TESTE);
  await page.getByLabel("Pix").check();
  await page.getByRole("button", { name: "Pagar" }).click();
  const copiaECola = page.getByLabel("Pix copia e cola");
  await expect(copiaECola).toBeVisible();
  const pixAntes = await copiaECola.inputValue();

  // antes do último minuto o botão não existe
  const acrescentar = page.getByRole("button", { name: "Acrescentar 1 minuto" });
  await expect(acrescentar).toHaveCount(0);

  // no último minuto ele aparece (a janela abre quando faltam 60 s, inclusive: o contador mostra 01:00 ou menos)
  await expect(acrescentar).toBeVisible({ timeout: 200_000 });
  const antes = await segundosDoContador(await page.getByRole("timer").textContent());
  expect(antes).toBeLessThanOrEqual(60);
  await acrescentar.click();

  // o contador soma o minuto ao que estava correndo, o botão some, a mensagem recebe o foco e o Pix não muda
  const mensagem = page.getByText("Pronto, mais 1 minuto no contador.");
  await expect(mensagem).toBeVisible();
  await expect(mensagem).toBeFocused();
  await expect(acrescentar).toHaveCount(0);
  const depois = await segundosDoContador(await page.getByRole("timer").textContent());
  expect(depois).toBeGreaterThan(antes + 50);
  expect(await copiaECola.inputValue()).toBe(pixAntes);

  // o Pix que já estava na tela continua valendo: simular o pagamento confirma a reserva
  await page.getByRole("button", { name: "Simular pagamento" }).click();
  await expect(page).toHaveURL(/\/agendar\/confirmacao\//, { timeout: 30_000 });
  await expect(page.getByRole("heading", { name: "Horário confirmado" })).toBeVisible();
  const codigo = await page.getByLabel("Código", { exact: true }).inputValue();
  expect(await statusDaReservaGuest(request, reservaId, codigo)).toBe("CONFIRMADO");
});
