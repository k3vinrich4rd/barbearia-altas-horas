import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import type { AmbienteDePush } from "@/api/push";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";
import { SESSAO_BARBEIRO } from "@/testes/painel";
import { PainelNotificacoes } from "./PainelNotificacoes";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
const ativar = vi.fn();
const desativar = vi.fn();
vi.mock("@/api/push", async (original) => ({
  ...(await original<typeof import("@/api/push")>()),
  ativarNoAparelho: (...a: unknown[]) => ativar(...a),
  desativarNoAparelho: (...a: unknown[]) => desativar(...a),
}));
ligarServidor();
beforeEach(() => {
  zerarNavegacao();
  ativar.mockReset();
  desativar.mockReset();
  servidor.use(
    http.get(`${BFF}/admin/notificacoes/chave-publica`, () =>
      HttpResponse.json({ disponivel: true, chavePublica: "BChavePublicaDeTeste" }),
    ),
  );
});

const UA_ANDROID = "Mozilla/5.0 (Linux; Android 14) Chrome/130.0 Mobile Safari/537.36";
const UA_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
const UA_IPHONE_VELHO = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_8 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";

const ambiente = (extra: Partial<AmbienteDePush> = {}): AmbienteDePush => ({
  temServiceWorker: true,
  temPushManager: true,
  temNotification: true,
  userAgent: UA_ANDROID,
  standalone: false,
  permissao: "default",
  inscrito: false,
  ...extra,
});

function abrir(amb: AmbienteDePush) {
  return renderizar(<PainelNotificacoes lerNavegador={() => Promise.resolve(amb)} />, SESSAO_BARBEIRO);
}

describe("ativação das notificações no aparelho (A41)", () => {
  test("A41: navegador sem PushManager: mostra que este navegador não recebe notificações e nenhum botão de ativar", async () => {
    abrir(ambiente({ temPushManager: false }));
    expect(await screen.findByText(/Este navegador não recebe notificações/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ativar/ })).not.toBeInTheDocument();
  });

  test("A41: iPhone fora da Tela de Início: mostra a instrução do iPhone (Compartilhar, Adicionar à Tela de Início)", async () => {
    abrir(ambiente({ userAgent: UA_IPHONE, temPushManager: false, standalone: false }));
    expect(await screen.findByText(/No iPhone, toque em Compartilhar, depois em Adicionar à Tela de Início/)).toBeInTheDocument();
    expect(screen.getByText(/abra o Altas Horas pelo ícone novo/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ativar/ })).not.toBeInTheDocument();
  });

  test("A41: iOS anterior ao 16.4: pede para atualizar o iPhone", async () => {
    abrir(ambiente({ userAgent: UA_IPHONE_VELHO, temPushManager: false }));
    expect(await screen.findByText("Atualize o iPhone para o iOS 16.4 ou mais novo.")).toBeInTheDocument();
  });

  test("A41: permissão negada: explica como liberar e não oferece ativar", async () => {
    abrir(ambiente({ permissao: "denied" }));
    expect(await screen.findByText(/As notificações estão bloqueadas neste aparelho/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ativar/ })).not.toBeInTheDocument();
  });

  test("A41: permissão ainda não pedida: a permissão só é pedida DEPOIS do toque em Ativar, que manda a inscrição à API", async () => {
    let corpo: unknown;
    servidor.use(
      http.post(`${BFF}/admin/notificacoes/inscricao`, async ({ request }) => {
        corpo = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    ativar.mockResolvedValue({ endpoint: "https://fcm.googleapis.com/fcm/send/abc", chaves: { p256dh: "p", auth: "a" } });
    const { user } = abrir(ambiente());

    const botao = await screen.findByRole("button", { name: "Ativar notificações" });
    expect(ativar, "nada é pedido ao navegador antes do toque").not.toHaveBeenCalled();
    await user.click(botao);

    await waitFor(() => expect(corpo).toBeDefined());
    expect(ativar).toHaveBeenCalledWith("BChavePublicaDeTeste");
    expect(corpo).toEqual({ endpoint: "https://fcm.googleapis.com/fcm/send/abc", chaves: { p256dh: "p", auth: "a" } });
    expect(await screen.findByText("Notificações ativadas neste aparelho.")).toBeInTheDocument();
  });

  test("A41: a pessoa recusa no navegador: nada é enviado à API e a tela explica", async () => {
    let chamou = false;
    servidor.use(
      http.post(`${BFF}/admin/notificacoes/inscricao`, () => {
        chamou = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    ativar.mockResolvedValue("negada");
    const { user } = abrir(ambiente());

    await user.click(await screen.findByRole("button", { name: "Ativar notificações" }));

    expect(await screen.findByText(/Você não liberou as notificações/)).toBeInTheDocument();
    expect(chamou).toBe(false);
  });

  test("A41: inscrição ativa: mostra Ativo com 'Enviar um teste' e 'Desativar'; o teste chama a API", async () => {
    let testes = 0;
    servidor.use(
      http.post(`${BFF}/admin/notificacoes/teste`, () => {
        testes++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = abrir(ambiente({ permissao: "granted", inscrito: true }));

    expect(await screen.findByText("Ativo")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Enviar um teste" }));

    await waitFor(() => expect(testes).toBe(1));
    expect(await screen.findByText(/Teste enviado/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Desativar" })).toBeInTheDocument();
  });

  test("A41: desativar cancela no aparelho e manda o endpoint no corpo do DELETE", async () => {
    let corpo: unknown;
    servidor.use(
      http.delete(`${BFF}/admin/notificacoes/inscricao`, async ({ request }) => {
        corpo = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    desativar.mockResolvedValue("https://fcm.googleapis.com/fcm/send/abc");
    const { user } = abrir(ambiente({ permissao: "granted", inscrito: true }));

    await user.click(await screen.findByRole("button", { name: "Desativar" }));

    await waitFor(() => expect(corpo).toEqual({ endpoint: "https://fcm.googleapis.com/fcm/send/abc" }));
  });

  test("A41: sem a chave VAPID na API (disponivel=false) o botão de ativar fica desabilitado e a tela avisa", async () => {
    servidor.use(http.get(`${BFF}/admin/notificacoes/chave-publica`, () => HttpResponse.json({ disponivel: false })));
    abrir(ambiente());
    expect(await screen.findByText(/indisponíveis no momento/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ativar notificações" })).toBeDisabled();
  });
});
