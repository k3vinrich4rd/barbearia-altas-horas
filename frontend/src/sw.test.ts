import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, test, vi } from "vitest";
import config from "../next.config";

// O service worker é um arquivo JS puro em public/sw.js: o teste o executa com um `self` falso e dispara os eventos.
const CODIGO = readFileSync(resolve(__dirname, "../public/sw.js"), "utf-8");

type Ouvinte = (evento: Record<string, unknown>) => void;

function montar() {
  const ouvintes: Record<string, Ouvinte> = {};
  const abas: { url: string; focus: ReturnType<typeof vi.fn>; postMessage: ReturnType<typeof vi.fn> }[] = [];
  const self = {
    addEventListener: (tipo: string, fn: Ouvinte) => (ouvintes[tipo] = fn),
    skipWaiting: vi.fn(),
    registration: { showNotification: vi.fn(() => Promise.resolve()) },
    clients: {
      claim: vi.fn(() => Promise.resolve()),
      matchAll: vi.fn(() => Promise.resolve(abas)),
      openWindow: vi.fn(() => Promise.resolve(null)),
    },
  };
  new Function("self", CODIGO)(self);
  return { ouvintes, self, abas };
}

async function empurrar(ouvintes: Record<string, Ouvinte>, data: unknown) {
  const esperas: Promise<unknown>[] = [];
  ouvintes.push({
    data: data === undefined ? null : data,
    waitUntil: (p: Promise<unknown>) => esperas.push(p),
  });
  await Promise.all(esperas);
}

const payloadDoA23 = {
  titulo: "Altas Horas",
  texto: "Novo horário: Corte Degradê, qui 27/11 às 10:00",
  link: "/painel",
  tag: "reserva-1",
};

describe("service worker do Web Push (Fatia 13)", () => {
  let m: ReturnType<typeof montar>;
  beforeEach(() => {
    m = montar();
  });

  test("A40: push com o payload do aviso mostra a notificação com o título e o texto, com a tag da reserva", async () => {
    await empurrar(m.ouvintes, { json: () => payloadDoA23 });
    expect(m.self.registration.showNotification).toHaveBeenCalledTimes(1);
    const [titulo, opcoes] = m.self.registration.showNotification.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(titulo).toBe("Altas Horas");
    expect(opcoes.body).toBe("Novo horário: Corte Degradê, qui 27/11 às 10:00");
    expect(opcoes.tag).toBe("reserva-1");
    expect(opcoes.data).toEqual({ link: "/painel" });
  });

  test("A40: payload ilegível (ou vazio) mostra o texto padrão, nunca deixa de mostrar a notificação", async () => {
    await empurrar(m.ouvintes, {
      json: () => {
        throw new SyntaxError("não é JSON");
      },
    });
    await empurrar(m.ouvintes, undefined);
    expect(m.self.registration.showNotification).toHaveBeenCalledTimes(2);
    for (const chamada of m.self.registration.showNotification.mock.calls as unknown as [string, Record<string, unknown>][]) {
      expect(chamada[0]).toBe("Altas Horas");
      expect(chamada[1].body).toBe("Novo horário no painel");
      expect(chamada[1].data).toEqual({ link: "/painel" });
    }
  });

  test("A40: o link só pode ser um caminho do próprio site (um link de fora vira /painel)", async () => {
    await empurrar(m.ouvintes, { json: () => ({ ...payloadDoA23, link: "https://exemplo.mal/phishing" }) });
    await empurrar(m.ouvintes, { json: () => ({ ...payloadDoA23, link: "//exemplo.mal" }) });
    for (const chamada of m.self.registration.showNotification.mock.calls as unknown as [string, Record<string, unknown>][]) {
      expect(chamada[1].data).toEqual({ link: "/painel" });
    }
  });

  test("A40/A42: o push avisa as abas abertas (postMessage) para atualizar o selo sem polling", async () => {
    const aba = { url: "http://localhost:3000/painel", focus: vi.fn(), postMessage: vi.fn() };
    m.abas.push(aba);
    await empurrar(m.ouvintes, { json: () => payloadDoA23 });
    expect(aba.postMessage).toHaveBeenCalledWith({ tipo: "novidade" });
  });

  test("A40: o clique na notificação foca a aba do painel se existir", async () => {
    const aba = { url: "http://localhost:3000/painel/novidades", focus: vi.fn(() => Promise.resolve()), postMessage: vi.fn() };
    m.abas.push({ url: "http://localhost:3000/", focus: vi.fn(), postMessage: vi.fn() }, aba);
    const fechar = vi.fn();
    const esperas: Promise<unknown>[] = [];
    m.ouvintes.notificationclick({
      notification: { close: fechar, data: { link: "/painel" } },
      waitUntil: (p: Promise<unknown>) => esperas.push(p),
    });
    await Promise.all(esperas);
    expect(fechar).toHaveBeenCalled();
    expect(aba.focus).toHaveBeenCalled();
    expect(m.self.clients.openWindow).not.toHaveBeenCalled();
  });

  test("A40: o clique sem aba do painel aberta abre /painel", async () => {
    m.abas.push({ url: "http://localhost:3000/", focus: vi.fn(), postMessage: vi.fn() });
    const esperas: Promise<unknown>[] = [];
    m.ouvintes.notificationclick({
      notification: { close: vi.fn(), data: { link: "/painel" } },
      waitUntil: (p: Promise<unknown>) => esperas.push(p),
    });
    await Promise.all(esperas);
    expect(m.self.clients.openWindow).toHaveBeenCalledWith("/painel");
  });

  test("A40: o arquivo é servido sem cache e com escopo da raiz; não guarda página nem usa console", async () => {
    const regras = (await config.headers?.()) ?? [];
    const sw = regras.find((r) => r.source === "/sw.js");
    const valor = (chave: string) => sw?.headers.find((h) => h.key.toLowerCase() === chave.toLowerCase())?.value;
    expect(valor("Cache-Control")).toContain("no-cache");
    expect(valor("Service-Worker-Allowed")).toBe("/");
    // sem cache de página, sem rota offline: nada de `caches`, `fetch` interceptado nem console (R30)
    expect(CODIGO).not.toMatch(/\bcaches\b/);
    expect(CODIGO).not.toMatch(/addEventListener\(\s*["']fetch["']/);
    expect(CODIGO).not.toMatch(/console\./);
  });
});
