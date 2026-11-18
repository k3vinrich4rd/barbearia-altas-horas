import { readFileSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { CPF_DE_TESTE, SENHA_DE_TESTE, emailUnico, escolherHorario, horarioLivre } from "./apoio";

// A60: nenhum e-mail, WhatsApp, CPF, CEP, token ou código de visitante em console, URL, storages, cabeçalhos legíveis
// nem no log do servidor do Next; os cookies ah_* não aparecem em document.cookie (R30, ADR 0006).
test("A60: nenhum dado pessoal vaza em console, URL, storage, cabeçalhos legíveis, document.cookie ou log do servidor", async ({ page, request }) => {
  const email = emailUnico("privacidade");
  const whatsapp = "11977776666";
  const cpfDigitos = CPF_DE_TESTE.replace(/\D/g, "");
  const cep = "01001-000";
  const segredos = [email, whatsapp, cpfDigitos, CPF_DE_TESTE, cep, "01001000", SENHA_DE_TESTE];

  const consoles: string[] = [];
  const urls: string[] = [];
  const respostas: { url: string; cabecalhos: Record<string, string> }[] = [];
  page.on("console", (m) => consoles.push(m.text()));
  page.on("pageerror", (e) => consoles.push(String(e)));
  page.on("request", (r) => urls.push(r.url()));
  page.on("response", async (r) => respostas.push({ url: r.url(), cabecalhos: await r.allHeaders() }));

  // visitante: reserva com WhatsApp e CEP, pagamento com CPF e confirmação com código
  const horario = await horarioLivre(request);
  await escolherHorario(page, horario);
  await page.getByRole("button", { name: "Continuar sem conta" }).click();
  await page.getByLabel("Nome").fill("Fulano Privacidade");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("WhatsApp").fill(whatsapp);
  await page.locator("#cep").fill(cep);
  await page.getByRole("button", { name: "Reservar" }).click();
  await expect(page).toHaveURL(/\/agendar\/pagamento\//);
  await page.getByLabel(/Li e aceito/).check();
  await page.getByLabel("CPF").fill(CPF_DE_TESTE);
  await page.getByRole("button", { name: "Pagar" }).click();
  await page.getByRole("button", { name: "Simular pagamento" }).click();
  await expect(page).toHaveURL(/\/agendar\/confirmacao\//, { timeout: 30_000 });
  const codigo = await page.getByLabel("Código", { exact: true }).inputValue();
  const reservaId = page.url().split("/").pop()!.split("?")[0];

  // cliente: cadastro com CEP e login
  const email2 = emailUnico("privacidade2");
  segredos.push(email2);
  await page.goto("/cadastro");
  await page.getByLabel("Nome").fill("Cliente Privacidade");
  await page.getByLabel("E-mail").fill(email2);
  await page.getByLabel("WhatsApp").fill(whatsapp);
  await page.getByLabel("Senha").fill(SENHA_DE_TESTE);
  await page.locator("#cep").fill(cep);
  await page.getByLabel("Senha").focus();
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page).toHaveURL(/\/meus-agendamentos/);

  // tokens da sessão: o que o navegador consegue ler não pode conter nenhum
  const cookies = await page.context().cookies();
  const sessao = cookies.filter((c) => c.name.startsWith("ah_"));
  expect(sessao.length).toBeGreaterThanOrEqual(2);
  for (const c of sessao) expect(c.httpOnly, `${c.name} precisa ser HttpOnly`).toBe(true);
  const legivel = await page.evaluate(() => ({ cookie: document.cookie, local: JSON.stringify({ ...localStorage }), sessao: JSON.stringify({ ...sessionStorage }) }));
  expect(legivel.cookie).not.toMatch(/ah_/);
  const tokens = [...sessao.map((c) => decodeURIComponent(c.value)), codigo].filter((t) => t.length > 8);
  for (const alvo of [...segredos, ...tokens]) {
    expect(legivel.cookie, "document.cookie").not.toContain(alvo);
    expect(legivel.local, "localStorage").not.toContain(alvo);
    expect(legivel.sessao, "sessionStorage").not.toContain(alvo);
    for (const u of urls) expect(decodeURIComponent(u), `URL ${u}`).not.toContain(alvo);
    expect(consoles.join("\n"), "console").not.toContain(alvo);
    for (const r of respostas) expect(JSON.stringify(r.cabecalhos).replace(/set-cookie[^,]*/gi, ""), `cabeçalhos de ${r.url}`).not.toContain(alvo);
  }
  expect(page.url()).not.toContain(codigo);
  expect(reservaId.length).toBeGreaterThan(10);

  // nenhum pedido a hosts externos (custo zero, sem Google em tempo de execução)
  for (const u of urls) expect(new URL(u).hostname, u).toBe("localhost");

  // log do servidor do Next
  const log = readFileSync("e2e/.logs/next.log", "utf8");
  for (const alvo of [...segredos, ...tokens]) expect(log, "log do servidor do Next").not.toContain(alvo);
});
