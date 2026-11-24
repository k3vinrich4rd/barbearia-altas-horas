import { expect, test, type APIRequestContext } from "@playwright/test";
import {
  SENHA_DE_TESTE,
  cadastrarCliente,
  emailUnico,
  entrarComoAdmin,
  escolherHorario,
  horarioLivre,
  pagarComPixESimular,
  reservaPagaDeVisitante,
} from "./apoio";

const EDUARDO = "b0000000-0000-4000-8000-000000000001";
/** O BFF exige Origin igual ao site em toda escrita (CSRF): o navegador manda sozinho, a API de teste do Playwright não. */
const ORIGEM = { origin: "http://localhost:3000" };

/** O dono atende: liga a conta ADMIN (que também tem o papel BARBEIRO) ao cadastro do Eduardo, se ainda não estiver. */
async function ligarEduardoAContaDoDono(request: APIRequestContext) {
  const sessao = await (await request.get("/api/sessao")).json();
  const donoId: string = sessao.usuario.id;
  const lista = (await (await request.get("/api/admin/barbeiros")).json()) as {
    id: string;
    nomeExibicao: string;
    usuarioId: string | null;
    ativo: boolean;
    version: number;
    whatsapp: string | null;
  }[];
  const eduardo = lista.find((b) => b.id === EDUARDO);
  expect(eduardo, "o barbeiro do seed dev").toBeDefined();
  if (eduardo!.usuarioId === donoId) return;
  const r = await request.put("/api/admin/barbeiros/" + EDUARDO, {
    headers: ORIGEM,
    data: {
      nomeExibicao: eduardo!.nomeExibicao,
      usuarioId: donoId,
      ativo: true,
      version: eduardo!.version,
      ...(eduardo!.whatsapp ? { whatsapp: eduardo!.whatsapp } : {}),
    },
  });
  expect(r.status()).toBe(200);
}

// A43 contra a API real (perfil dev, pagamento simulado): um visitante e um cliente com cadastro marcam com o Eduardo e pagam; o
// selo "Novidades" do Eduardo mostra as 2, e o barbeiro convidado, que só tem a própria agenda, não recebe nenhuma.
test("A43: visitante e cliente com cadastro marcam com o Eduardo e pagam; o selo do Eduardo mostra 2 novidades e o do convidado nenhuma", async ({
  page,
  request,
  browser,
}) => {
  await entrarComoAdmin(page);
  await ligarEduardoAContaDoDono(page.request);
  // começa do zero: o que sobrou de rodadas anteriores sai do selo
  expect((await page.request.post("/api/admin/notificacoes/marcar-lidas", { headers: ORIGEM })).status()).toBe(204);

  // 1. o visitante marca e paga (pela tela)
  const horarioDoVisitante = await horarioLivre(request);
  const visitante = await browser.newContext({ baseURL: "http://localhost:3000", locale: "pt-BR" });
  await reservaPagaDeVisitante(await visitante.newPage(), horarioDoVisitante);
  await visitante.close();

  // 2. o cliente com cadastro marca e paga (pela tela), em outro horário
  const horarioDoCliente = await horarioLivre(request, { ignorar: [horarioDoVisitante.inicio] });
  const cliente = await browser.newContext({ baseURL: "http://localhost:3000", locale: "pt-BR" });
  const paginaDoCliente = await cliente.newPage();
  await cadastrarCliente(paginaDoCliente, emailUnico("cliente-aviso"));
  await escolherHorario(paginaDoCliente, horarioDoCliente);
  await paginaDoCliente.getByRole("button", { name: "Reservar" }).click();
  await expect(paginaDoCliente).toHaveURL(/\/agendar\/pagamento\//);
  await pagarComPixESimular(paginaDoCliente);
  await cliente.close();

  // 3. o dono cria o barbeiro convidado DEPOIS das reservas (ele ainda não tem jornada) e o convida
  const nomeDoConvidado = `Convidado Aviso ${Date.now()}`;
  const criado = await page.request.post("/api/admin/barbeiros", { headers: ORIGEM, data: { nomeExibicao: nomeDoConvidado } });
  expect(criado.status()).toBe(201);
  const convidadoId: string = (await criado.json()).id;
  const convite = await page.request.post(`/api/admin/barbeiros/${convidadoId}/convite`, { headers: ORIGEM });
  expect(convite.status()).toBe(201);
  const codigo: string = (await convite.json()).codigo;

  const contextoDoConvidado = await browser.newContext({ baseURL: "http://localhost:3000", locale: "pt-BR" });
  const convidado = await contextoDoConvidado.newPage();
  const emailDoConvidado = emailUnico("barbeiro-aviso");
  const aceite = await contextoDoConvidado.request.post("/api/convite", {
    headers: ORIGEM,
    data: { codigo, email: emailDoConvidado, senha: SENHA_DE_TESTE },
  });
  expect(aceite.status()).toBe(204);
  await convidado.goto("/entrar");
  await convidado.getByLabel("E-mail").fill(emailDoConvidado);
  await convidado.getByLabel("Senha").fill(SENHA_DE_TESTE);
  await convidado.getByRole("button", { name: "Entrar" }).click();
  await expect(convidado).toHaveURL(/\/painel/);

  // 4. o Eduardo: o aviso chega pelo outbox, depois do pagamento; o selo mostra as 2 novidades
  await expect
    .poll(
      async () => {
        const r = await page.request.get("/api/admin/notificacoes");
        return (await r.json()).naoLidas;
      },
      { timeout: 30_000, intervals: [500, 1000, 2000] },
    )
    .toBe(2);
  await page.goto("/painel");
  const selo = page.getByRole("navigation", { name: "Painel" }).getByTestId("selo-novidades");
  await expect(selo).toContainText("2");
  await expect(selo).toContainText("novidades");

  // a lista mostra serviço, dia e hora (e nunca nome, e-mail nem WhatsApp do cliente); abrir marca como lido
  await page.getByRole("navigation", { name: "Painel" }).getByRole("link", { name: /Novidades/ }).click();
  await expect(page.getByRole("heading", { name: "Novidades" })).toBeVisible();
  const itens = page.getByRole("list", { name: "Horários novos" }).getByRole("listitem");
  expect(await itens.count()).toBeGreaterThanOrEqual(2); // sobra de rodadas anteriores também aparece (vale 30 dias)
  await expect(itens.first()).toContainText(/\d{2}\/\d{2} às \d{2}:\d{2}/);
  await expect(page.locator("main")).not.toContainText(/Painel E2E|Cliente de Teste|@|\b119\d{8}\b/);
  await expect(page.getByTestId("selo-novidades")).toHaveCount(0);

  // o aviso do Eduardo não vale para o convidado: nenhuma novidade e nenhuma consulta alheia
  await convidado.goto("/painel");
  await expect(convidado.getByRole("navigation", { name: "Painel" })).toBeVisible();
  await expect(convidado.getByTestId("selo-novidades")).toHaveCount(0);
  const doConvidado = await (await convidado.request.get("/api/admin/notificacoes")).json();
  expect(doConvidado).toEqual({ naoLidas: 0, itens: [] });
  await convidado.goto("/painel/novidades");
  await expect(convidado.getByText("Nenhuma novidade por enquanto.")).toBeVisible();

  // a tela de ativação abre para o barbeiro (o navegador do teste não tem Web Push de verdade: o estado depende dele)
  await convidado.goto("/painel/notificacoes");
  await expect(convidado.getByRole("heading", { name: "Notificações" })).toBeVisible();
  await expect(convidado.getByRole("heading", { name: "Este aparelho" })).toBeVisible();

  // o service worker é servido sem cache e o manifesto do app existe
  const sw = await request.get("http://localhost:3000/sw.js");
  expect(sw.status()).toBe(200);
  expect(sw.headers()["cache-control"]).toContain("no-cache");
  expect(sw.headers()["service-worker-allowed"]).toBe("/");
  const manifesto = await (await request.get("http://localhost:3000/manifest.webmanifest")).json();
  expect(manifesto.start_url).toBe("/painel");
  expect(manifesto.display).toBe("standalone");

  // a API do convidado nunca devolve o aviso do outro (a conta vem do token)
  const outro = await convidado.request.get(`/api/admin/notificacoes?usuarioId=${EDUARDO}&barbeiroId=${EDUARDO}`);
  expect((await outro.json()).itens).toEqual([]);
  await contextoDoConvidado.close();

  // limpeza: desliga o convidado, para os outros E2E (que escolhem "o" barbeiro) não verem um segundo barbeiro ativo
  const lista = (await (await page.request.get("/api/admin/barbeiros")).json()) as {
    id: string;
    usuarioId: string | null;
    version: number;
  }[];
  const dele = lista.find((b) => b.id === convidadoId)!;
  const desligado = await page.request.put(`/api/admin/barbeiros/${convidadoId}`, {
    headers: ORIGEM,
    data: { nomeExibicao: nomeDoConvidado, usuarioId: dele.usuarioId, ativo: false, version: dele.version },
  });
  expect(desligado.status()).toBe(200);
});
