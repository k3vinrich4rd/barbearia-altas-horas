import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { emailUnico, entrarComoAdmin, SENHA_DE_TESTE } from "./apoio";

// A35 a A38 contra a API real: o dono gera o convite, o barbeiro abre o link (o código sai da barra de endereço), digita o
// próprio e-mail e cria a senha, entra e vê só o painel de barbeiro; o link não serve de novo; o dono desliga e a sessão cai.
test("A36: convite de ponta a ponta: gerar, abrir sem consumir, criar a senha, entrar, o link não serve de novo e desligar derruba a sessão", async ({
  page,
  browser,
}) => {
  const nome = `Convidado E2E ${Date.now()}`;

  // 1. o dono cadastra o barbeiro e gera o convite (sem informar e-mail)
  await entrarComoAdmin(page);
  await page.goto("/painel/cadastros");
  await expect(page.getByText("Você entrou como Dono/Admin")).toBeVisible();
  await page.getByRole("button", { name: "Novo barbeiro" }).click();
  await page.getByLabel("Nome de exibição").fill(nome);
  await page.getByRole("button", { name: "Salvar barbeiro" }).click();
  await expect(page.getByText("Barbeiro salvo.")).toBeVisible();
  await page.getByRole("button", { name: `Gerar convite para ${nome}` }).click();
  const dialogo = page.getByRole("dialog", { name: `Convite de ${nome}` });
  await expect(dialogo.getByRole("button", { name: "Copiar link" })).toBeVisible();
  await expect(dialogo.getByRole("link", { name: "Enviar pelo WhatsApp" })).toBeVisible();
  await expect(dialogo.getByRole("img", { name: /QR Code do convite/ })).toBeVisible();
  const link = await dialogo.getByLabel("Link do convite").inputValue();
  expect(link).toMatch(/\/convite#[A-Za-z0-9_-]{43}$/);
  await dialogo.getByRole("button", { name: "Fechar" }).click();
  await expect(page.getByRole("row").filter({ hasText: nome })).toContainText(/Convite pendente até/);
  await expect(page.locator("body")).not.toContainText(link.split("#")[1]);

  // 2. o barbeiro abre o link: nada é consumido, e o código sai da barra de endereço
  const contexto = await browser.newContext({ baseURL: "http://localhost:3000", locale: "pt-BR" });
  const barbeiro = await contexto.newPage();
  const pedidosComCodigo: string[] = [];
  const codigo = link.split("#")[1];
  barbeiro.on("request", (r) => {
    if (r.url().includes(codigo) || (r.postData() ?? "").includes(codigo))
      pedidosComCodigo.push(r.method() + " " + r.url());
  });
  const resposta = await barbeiro.goto(link);
  expect(resposta?.headers()["referrer-policy"]).toBe("no-referrer");
  expect(resposta?.headers()["cache-control"]).toContain("no-store");
  await expect(barbeiro.getByRole("heading", { name: "Criar minha senha" })).toBeVisible();
  await expect.poll(() => new URL(barbeiro.url()).hash).toBe("");
  expect(pedidosComCodigo).toEqual([]);

  // A38: acessibilidade da tela (axe) nos dois temas
  for (const tema of ["escuro", "claro"]) {
    await barbeiro.evaluate((t) => (document.documentElement.dataset.theme = t), tema);
    const r = await new AxeBuilder({ page: barbeiro }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
    expect(
      r.violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id),
      `axe em /convite no tema ${tema}`,
    ).toEqual([]);
  }

  // 3. cria a senha com o próprio e-mail e entra
  const email = emailUnico("barbeiro-convidado");
  await barbeiro.getByLabel("Seu e-mail").fill(email);
  await barbeiro.getByLabel("Senha", { exact: true }).fill(SENHA_DE_TESTE);
  await barbeiro.getByLabel("Repita a senha").fill(SENHA_DE_TESTE);
  await barbeiro.getByRole("button", { name: "Criar minha senha" }).click();
  await expect(barbeiro).toHaveURL(/\/entrar\?aviso=convite-aceito/);
  await expect(barbeiro.getByText("Senha criada. Entre com seu e-mail e a nova senha.")).toBeVisible();
  await barbeiro.getByLabel("E-mail").fill(email);
  await barbeiro.getByLabel("Senha").fill(SENHA_DE_TESTE);
  await barbeiro.getByRole("button", { name: "Entrar" }).click();
  await expect(barbeiro).toHaveURL(/\/painel/);
  const menu = barbeiro.getByRole("navigation", { name: "Painel" });
  await expect(menu.getByText("Você entrou como Barbeiro")).toBeVisible();
  await expect(menu.getByRole("link", { name: "Agenda" })).toBeVisible();
  await expect(menu.getByRole("link", { name: "Cadastros" })).toHaveCount(0);
  // o barbeiro não lê o cadastro do dono: a API responde 403
  expect((await barbeiro.request.get("/api/admin/barbeiros")).status()).toBe(403);

  // 4. o mesmo link não serve de novo (uma frase só, sem dizer o motivo)
  const outraAba = await (await browser.newContext({ baseURL: "http://localhost:3000", locale: "pt-BR" })).newPage();
  await outraAba.goto(link);
  await outraAba.getByLabel("Seu e-mail").fill(emailUnico("barbeiro-intruso"));
  await outraAba.getByLabel("Senha", { exact: true }).fill(SENHA_DE_TESTE);
  await outraAba.getByLabel("Repita a senha").fill(SENHA_DE_TESTE);
  await outraAba.getByRole("button", { name: "Criar minha senha" }).click();
  await expect(outraAba.getByRole("form", { name: "Criar minha senha" }).getByRole("alert")).toContainText(
    "Este link não vale mais ou não pode ser usado aqui.",
  );
  await expect(outraAba.getByRole("link", { name: "Já tenho conta no site: entrar" })).toBeVisible();

  // 5. o dono vê "Acesso ativo", desliga (com confirmação) e a sessão do barbeiro cai na hora
  await page.reload();
  const linha = page.getByRole("row").filter({ hasText: nome });
  await expect(linha).toContainText("Acesso ativo");
  await linha.getByRole("button", { name: `Editar ${nome}` }).click();
  await page.getByLabel(/Ativo \(aparece/).uncheck();
  await page.getByRole("button", { name: "Salvar barbeiro" }).click();
  await expect(page.getByRole("dialog", { name: `Desligar ${nome}` })).toContainText("mantém o histórico");
  await page.getByRole("button", { name: "Desligar barbeiro" }).click();
  await expect(page.getByText("Barbeiro salvo.")).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: nome })).toContainText("Desligado");
  await barbeiro.reload();
  expect((await barbeiro.request.get("/api/admin/minha-jornada")).status()).toBe(401);
  await expect(barbeiro.getByRole("navigation", { name: "Painel" })).toHaveCount(0);
  await expect(barbeiro.getByRole("navigation", { name: "Conta" }).getByRole("link", { name: "Entrar" })).toBeVisible();

  // a auditoria do dono tem o convite, o aceite e o desligamento, sem e-mail nem código
  await page.goto("/painel/auditoria");
  const tabela = page.getByRole("table");
  for (const acao of ["Convite gerado", "Convite aceito pelo barbeiro", "Barbeiro desligado"])
    await expect(tabela).toContainText(acao);
  await expect(page.locator("main")).not.toContainText(email);
  await expect(page.locator("main")).not.toContainText(codigo);
  await contexto.close();
});
