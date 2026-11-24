import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { ANONIMO, CLIENTE, renderizar } from "@/testes/render";
import { Menu } from "./Menu";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
const desativar = vi.fn();
vi.mock("@/api/push", async (original) => ({
  ...(await original<typeof import("@/api/push")>()),
  desativarNoAparelho: (...a: unknown[]) => desativar(...a),
}));
ligarServidor();

let chamadas: string[];
beforeEach(() => {
  zerarNavegacao();
  desativar.mockReset();
  chamadas = [];
  // o navegador de teste tem service worker e PushManager de mentira, só para a guarda do logout deixar passar
  Object.defineProperty(navigator, "serviceWorker", { value: new EventTarget(), configurable: true });
  Object.defineProperty(window, "PushManager", { value: class {}, configurable: true });
  servidor.use(
    http.post(`${BFF}/auth/logout`, () => {
      chamadas.push("logout");
      return new HttpResponse(null, { status: 204 });
    }),
    http.delete(`${BFF}/admin/notificacoes/inscricao`, async ({ request }) => {
      chamadas.push("delete " + JSON.stringify(await request.json()));
      return new HttpResponse(null, { status: 204 });
    }),
  );
});

describe("sair da conta tira o aparelho (Fatia 13)", () => {
  test("A40: o barbeiro que sai cancela a inscrição no aparelho e manda o DELETE ANTES de encerrar a sessão", async () => {
    desativar.mockResolvedValue("https://fcm.googleapis.com/fcm/send/abc");
    const { user } = renderizar(<Menu />, SESSAO_BARBEIRO);

    await user.click(await screen.findByRole("button", { name: "Sair" }));

    await waitFor(() => expect(chamadas).toHaveLength(2));
    expect(chamadas).toEqual(['delete {"endpoint":"https://fcm.googleapis.com/fcm/send/abc"}', "logout"]);
  });

  test("A40: se tirar o aparelho falha (DELETE 500 ou erro do navegador), o logout acontece do mesmo jeito", async () => {
    desativar.mockResolvedValue("https://fcm.googleapis.com/fcm/send/abc");
    servidor.use(http.delete(`${BFF}/admin/notificacoes/inscricao`, () => new HttpResponse(null, { status: 500 })));
    const { user } = renderizar(<Menu />, SESSAO_BARBEIRO);
    await user.click(await screen.findByRole("button", { name: "Sair" }));
    await waitFor(() => expect(chamadas).toEqual(["logout"]));

    chamadas.length = 0;
    desativar.mockRejectedValue(new Error("o navegador recusou"));
    const outro = renderizar(<Menu />, SESSAO_BARBEIRO);
    await outro.user.click((await screen.findAllByRole("button", { name: "Sair" })).at(-1)!);
    await waitFor(() => expect(chamadas).toEqual(["logout"]));
  });

  test("A40: sem inscrição neste aparelho não há DELETE; cliente (que não tem aparelho) nem tenta", async () => {
    desativar.mockResolvedValue(null);
    const { user } = renderizar(<Menu />, SESSAO_BARBEIRO);
    await user.click(await screen.findByRole("button", { name: "Sair" }));
    await waitFor(() => expect(chamadas).toEqual(["logout"]));

    chamadas.length = 0;
    desativar.mockClear();
    const cliente = renderizar(<Menu />, CLIENTE);
    await cliente.user.click((await screen.findAllByRole("button", { name: "Sair" })).at(-1)!);
    await waitFor(() => expect(chamadas).toEqual(["logout"]));
    expect(desativar).not.toHaveBeenCalled();
    expect(ANONIMO.autenticado).toBe(false);
  });
});
