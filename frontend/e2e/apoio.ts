// Apoio dos E2E: dados únicos por teste, escolha de horário pela própria API e conferências direto na API real.
import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const API_URL = process.env.API_URL ?? "http://localhost:8080";
/** CPF só de teste (dígitos verificadores corretos, não é de ninguém). */
export const CPF_DE_TESTE = "529.982.247-25";
export const SENHA_DE_TESTE = "senha-e2e-bem-longa-2026";

let contador = 0;
export const emailUnico = (prefixo = "e2e") => `${prefixo}-${Date.now()}-${++contador}@exemplo.test`;

export interface Horario {
  servicoId: string;
  barbeiroId: string;
  dia: string;
  inicio: string;
  hora: string;
}

function somar(dias: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + dias);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

/** Primeiro dia (a partir de amanhã) com horário livre, escolhido pela própria API, e um horário dele (sem data fixa). */
export async function horarioLivre(request: APIRequestContext, opcoes: { ignorar?: string[] } = {}): Promise<Horario> {
  const servicos = await (await request.get(`${API_URL}/servicos`)).json();
  const servicoId: string = servicos[0].id;
  for (let n = 1; n <= 28; n++) {
    const dia = somar(n);
    const r = await request.get(`${API_URL}/disponibilidade`, { params: { servicoId, data: dia } });
    if (!r.ok()) continue;
    const d = await r.json();
    const barbeiro = d.barbeiros.find((b: { horarios: string[] }) =>
      b.horarios.some((h) => !opcoes.ignorar?.includes(h)),
    );
    if (!barbeiro) continue;
    const livres: string[] = barbeiro.horarios.filter((h: string) => !opcoes.ignorar?.includes(h));
    // um horário do meio do dia, para não colidir com outros testes sempre no primeiro
    const inicio = livres[Math.floor(livres.length / 2)];
    return { servicoId, barbeiroId: barbeiro.barbeiroId, dia, inicio, hora: inicio.slice(11, 16) };
  }
  throw new Error("a API não tem nenhum horário livre nos próximos 28 dias");
}

/** Escolhe serviço, dia e horário na tela /agendar. */
export async function escolherHorario(page: Page, h: Horario) {
  await page.goto("/agendar");
  await page.getByRole("radio").first().check();
  await page.locator("#data").fill(h.dia);
  await page.getByRole("button", { name: h.hora, exact: true }).first().click();
  await expect(page).toHaveURL(/\/agendar\/dados\?/);
}

export async function pagarComPixESimular(page: Page) {
  await page.getByLabel(/Li e aceito/).check();
  await page.getByLabel("CPF").fill(CPF_DE_TESTE);
  await page.getByLabel("Pix").check();
  await page.getByRole("button", { name: "Pagar" }).click();
  await expect(page.getByLabel("Pix copia e cola")).toBeVisible();
  await page.getByRole("button", { name: "Simular pagamento" }).click();
  await expect(page).toHaveURL(/\/agendar\/confirmacao\//, { timeout: 30_000 });
}

export async function cadastrarCliente(
  page: Page,
  email = emailUnico("cliente"),
  extra: { cep?: string; marketing?: boolean } = {},
) {
  await page.goto("/cadastro");
  await page.getByLabel("Nome").fill("Cliente de Teste");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("WhatsApp").fill("11900000001");
  await page.getByLabel("Senha").fill(SENHA_DE_TESTE);
  if (extra.cep) {
    await page.locator("#cep").fill(extra.cep);
    await page.getByLabel("Senha").focus(); // sai do campo: a busca do CEP roda ao sair
  }
  if (extra.marketing) await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page).toHaveURL(/\/meus-agendamentos/);
  return email;
}

/** Cria uma reserva de visitante direto na API real (sem UI), para telas que precisam de uma reserva pronta. */
export async function reservaDeVisitanteNaApi(request: APIRequestContext, h: Horario) {
  const r = await request.post(`${API_URL}/agendamentos/guest`, {
    data: {
      servicoId: h.servicoId,
      barbeiroId: h.barbeiroId,
      inicio: h.inicio,
      nome: "Visitante de Teste",
      email: emailUnico("visitante"),
      whatsapp: "11900000002",
    },
  });
  expect(r.status()).toBe(201);
  return (await r.json()) as { id: string; tokenGuest: string };
}

export async function statusDaReservaGuest(request: APIRequestContext, id: string, token: string) {
  const r = await request.get(`${API_URL}/agendamentos/guest/${id}`, { headers: { "X-Guest-Token": token } });
  return (await r.json()).status as string;
}
