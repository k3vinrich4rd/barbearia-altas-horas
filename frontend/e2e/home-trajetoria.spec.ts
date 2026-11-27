import { expect, test } from "@playwright/test";

// A home conta a história da barbearia (1994, 1997, 2017, hoje) com interações que precisam funcionar no navegador
// de verdade e só com o teclado: linha do tempo, dúvidas em <details>, galeria (visualizador) e o painel de acessibilidade.
test("home: linha do tempo e dúvidas pelo teclado, galeria e tema claro no navegador de verdade", async ({ page }) => {
  const pedidosAudio: string[] = [];
  page.on("request", (r) => {
    if (/\/assets\/audio\/historia\.(opus|mp3|vtt)(\?|$)/.test(r.url())) pedidosAudio.push(r.url());
  });
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Seu horário com o Eduardo, garantido.");
  const trajetoria = page.locator("#trajetoria");
  await expect(
    trajetoria.getByText(
      "Em 1994, o Carlos Eduardo Candido, o Eduardo, começou no ofício aprendendo com o primeiro mestre.",
      { exact: false },
    ),
  ).toBeVisible();
  await expect(
    trajetoria.getByText("O filho, Kevin, criou esta agenda para que cada cliente marque do seu jeito."),
  ).toBeVisible();
  await expect(
    trajetoria.getByText("é só chamar o Eduardo no WhatsApp, que ele mesmo marca o seu horário."),
  ).toBeVisible();
  await expect(trajetoria.getByText("terça a sábado")).toHaveCount(0);
  await expect(page.locator("html")).toHaveAttribute("data-theme", "escuro");

  // linha do tempo: 1997 vem marcado; Tab até 2017 e Enter troca o detalhe anunciado
  const linha = page.getByRole("group", { name: "Escolha um ano da linha do tempo" });
  await expect(linha.getByRole("button", { pressed: true })).toContainText("1997");
  await linha.getByRole("button", { name: /1997/ }).focus();
  await page.keyboard.press("Tab");
  await expect(linha.getByRole("button", { name: /2017/ })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(linha.getByRole("button", { pressed: true })).toContainText("2017");
  await expect(page.locator("#marco-detalhe")).toContainText("2017 · Dois prêmios.");

  // dúvidas: o <summary> abre com Enter, sem JavaScript da página
  const duvida = page.locator("#duvidas details").first();
  await duvida.locator("summary").focus();
  await page.keyboard.press("Enter");
  await expect(duvida).toHaveAttribute("open", "");

  // galeria: a miniatura abre a foto inteira, a seta troca, Esc fecha e o foco volta
  const galeria = page.locator("#galeria");
  await expect(galeria.getByRole("button", { name: /^Ampliar foto:/ })).toHaveCount(18);
  const primeira = galeria.getByRole("button", { name: /^Ampliar foto:/ }).first();
  await primeira.focus();
  await page.keyboard.press("Enter");
  const visualizador = page.getByRole("dialog");
  await expect(visualizador.getByText("Foto 1 de 18")).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(visualizador.getByText("Foto 2 de 18")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(visualizador).toBeHidden();
  await expect(galeria.getByRole("button", { name: /^Ampliar foto:/ }).nth(1)).toBeFocused();

  // VZ-1, VZ-2 e VZ-7: o áudio só baixa ao apertar Ouvir; play, pausa e posição pelo teclado
  expect(pedidosAudio, pedidosAudio.join(" ")).toEqual([]);
  await expect(page.locator("#trajetoria audio")).toHaveCount(0);
  const ouvir = page.getByRole("button", { name: /Ouvir a trajetória/ });
  await ouvir.focus();
  await page.keyboard.press("Enter");
  const audio = page.locator("#trajetoria audio");
  await expect(audio).toHaveAttribute("preload", "none");
  await expect(audio).toHaveAttribute("controls");
  await expect(page.getByRole("button", { name: "Pausar" })).toBeVisible();
  expect(pedidosAudio.some((u) => u.includes("/assets/audio/historia."))).toBe(true);
  await audio.focus();
  await page.keyboard.press("Space");
  await expect(page.getByRole("button", { name: /Ouvir a trajetória/ })).toBeVisible();

  // painel de acessibilidade: tema claro e sem animação mudam o <html> e marcam o botão
  const painel = page.getByRole("region", { name: "Recursos de acessibilidade" });
  await painel.getByRole("button", { name: "Tema claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "claro");
  await expect(painel.getByRole("button", { name: "Tema claro" })).toHaveAttribute("aria-pressed", "true");
  await painel.getByRole("button", { name: "Sem animação" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-movimento", "reduzido");
});

test("o Chromium escolhe o Opus (audio/ogg; codecs=opus) e não baixa o MP3", async ({ page }) => {
  const pedidosAudio: string[] = [];
  page.on("request", (r) => {
    if (/\/assets\/audio\/historia\.(opus|mp3|vtt)(\?|$)/.test(r.url())) pedidosAudio.push(r.url());
  });
  await page.goto("/");
  await page.getByRole("button", { name: /Ouvir a trajetória/ }).click();
  const audio = page.locator("#trajetoria audio");
  await expect(audio.locator('source[type="audio/ogg; codecs=opus"]')).toHaveAttribute(
    "src",
    "/assets/audio/historia.opus",
  );
  await expect.poll(() => pedidosAudio.some((u) => u.includes("/assets/audio/historia.opus"))).toBe(true);
  expect(
    pedidosAudio.filter((u) => u.includes("/assets/audio/historia.mp3")),
    pedidosAudio.join(" "),
  ).toEqual([]);
});

test("os controles nativos só aparecem depois do primeiro Ouvir, sem baixar o áudio antes", async ({ page }) => {
  const pedidosAudio: string[] = [];
  page.on("request", (r) => {
    if (/\/assets\/audio\/historia\.(opus|mp3|vtt)(\?|$)/.test(r.url())) pedidosAudio.push(r.url());
  });
  await page.goto("/");
  await expect(page.locator("#trajetoria audio")).toHaveCount(0);
  expect(pedidosAudio, pedidosAudio.join(" ")).toEqual([]);
  await page.getByRole("button", { name: /Ouvir a trajetória/ }).click();
  const audio = page.locator("#trajetoria audio");
  await expect(audio).toBeVisible();
  await expect(audio).toHaveAttribute("controls");
  expect(pedidosAudio.some((u) => u.includes("/assets/audio/historia."))).toBe(true);
});

test("se o arquivo não carrega, oferece ler com a voz do aparelho e não fala sozinho", async ({ page }) => {
  await page.route(/\/assets\/audio\/historia\.(opus|mp3)(\?|$)/, (route) => route.abort());
  await page.goto("/");
  await page.getByRole("button", { name: /Ouvir a trajetória/ }).click();
  await expect(page.getByRole("status")).toHaveText("Não deu para carregar o áudio. Ler com a voz do aparelho?");
  await expect(page.getByRole("button", { name: "Ler com a voz do aparelho" })).toBeVisible();
  expect(await page.evaluate(() => window.speechSynthesis?.speaking ?? false)).toBe(false);
  await page.getByRole("button", { name: "Ler com a voz do aparelho" }).click();
  await expect(page.getByRole("button", { name: "Pausar" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Ler com a voz do aparelho" })).toHaveCount(0);
});
