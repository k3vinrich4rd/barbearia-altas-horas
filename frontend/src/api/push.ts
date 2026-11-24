// Web Push do painel no navegador (Fatia 13, D177). Sem dado do cliente: o service worker só recebe serviço, dia e hora.
// O estado da tela sai de uma função pura (`estadoDoPush`) sobre o que o navegador diz, para testar cada caso sem navegador.

export type EstadoDoPush =
  | "carregando"
  | "sem-suporte"
  | "ios-fora-da-tela-inicial"
  | "permissao-negada"
  | "desligado"
  | "ativo";

/** O que o navegador informa. Tudo opcional no tipo porque cada navegador tem um pedaço. */
export interface AmbienteDePush {
  temServiceWorker: boolean;
  temPushManager: boolean;
  temNotification: boolean;
  userAgent: string;
  /** Aberto pelo ícone da Tela de Início (`display-mode: standalone` ou `navigator.standalone`). */
  standalone: boolean;
  permissao: "default" | "granted" | "denied";
  inscrito: boolean;
}

export interface Diagnostico {
  estado: EstadoDoPush;
  /** Quando o estado é `sem-suporte`: iPhone com iOS velho precisa de outra instrução que os outros navegadores. */
  iosAntigo: boolean;
}

export const INSTRUCAO_DO_IPHONE =
  "No iPhone, toque em Compartilhar, depois em Adicionar à Tela de Início, e abra o Altas Horas pelo ícone novo. Só então dá para ativar as notificações.";
export const INSTRUCAO_DO_IOS_ANTIGO = "Atualize o iPhone para o iOS 16.4 ou mais novo.";

export function ehApple(userAgent: string): boolean {
  // iPadOS 13+ se apresenta como Mac; "Mobile" no UA e toque distingue na prática, mas o UA já traz "iPad" nas versões que importam.
  return /iPhone|iPad|iPod/i.test(userAgent);
}

/** A versão do iOS do user agent ("OS 16_4_1" vira 16 e 4), ou `null` quando não é iOS ou não dá para saber. */
export function versaoDoIos(userAgent: string): { principal: number; secundaria: number } | null {
  const m = /OS (\d+)[_.](\d+)/i.exec(userAgent);
  if (!m || !ehApple(userAgent)) return null;
  return { principal: Number(m[1]), secundaria: Number(m[2]) };
}

export function estadoDoPush(amb: AmbienteDePush): Diagnostico {
  const apple = ehApple(amb.userAgent);
  const versao = versaoDoIos(amb.userAgent);
  const iosAntigo = apple && versao !== null && (versao.principal < 16 || (versao.principal === 16 && versao.secundaria < 4));
  if (iosAntigo) return { estado: "sem-suporte", iosAntigo: true };
  // No iPhone o Web Push só existe com o site na Tela de Início; fora dela o PushManager nem aparece.
  if (apple && !amb.standalone) return { estado: "ios-fora-da-tela-inicial", iosAntigo: false };
  if (!amb.temServiceWorker || !amb.temPushManager || !amb.temNotification) return { estado: "sem-suporte", iosAntigo: false };
  if (amb.permissao === "denied") return { estado: "permissao-negada", iosAntigo: false };
  if (amb.permissao === "granted" && amb.inscrito) return { estado: "ativo", iosAntigo: false };
  return { estado: "desligado", iosAntigo: false };
}

/** Lê o navegador de verdade (só no cliente). */
export async function lerAmbiente(): Promise<AmbienteDePush> {
  const temServiceWorker = "serviceWorker" in navigator;
  const temPushManager = "PushManager" in window;
  const temNotification = "Notification" in window;
  const standalone =
    window.matchMedia?.("(display-mode: standalone)").matches === true ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;
  let inscrito = false;
  if (temServiceWorker && temPushManager) {
    try {
      const registro = await navigator.serviceWorker.getRegistration("/sw.js");
      inscrito = !!(await registro?.pushManager.getSubscription());
    } catch {
      inscrito = false;
    }
  }
  return {
    temServiceWorker,
    temPushManager,
    temNotification,
    userAgent: navigator.userAgent,
    standalone,
    permissao: temNotification ? Notification.permission : "default",
    inscrito,
  };
}

/** base64url (a chave pública VAPID) para os bytes que o `subscribe` pede. */
export function chaveParaBytes(base64Url: string): Uint8Array<ArrayBuffer> {
  const preenchida = base64Url.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(base64Url.length / 4) * 4, "=");
  const binario = atob(preenchida);
  const bytes = new Uint8Array(new ArrayBuffer(binario.length));
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return bytes;
}

export interface InscricaoDoAparelho {
  endpoint: string;
  chaves: { p256dh: string; auth: string };
}

/** O `PushSubscription` no formato do contrato, ou erro se o navegador não devolveu as duas chaves. */
export function inscricaoDoContrato(sub: PushSubscription): InscricaoDoAparelho {
  const json = sub.toJSON();
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (!json.endpoint || !p256dh || !auth) throw new Error("inscrição sem chaves");
  return { endpoint: json.endpoint, chaves: { p256dh, auth } };
}

/**
 * Ativar: registra o service worker, pede a permissão (só depois de um toque: quem chama é um botão) e cria a inscrição com
 * a chave pública que a API mandou. Devolve a inscrição para a tela enviar à API, ou `"negada"` se a pessoa recusou.
 */
export async function ativarNoAparelho(chavePublica: string): Promise<InscricaoDoAparelho | "negada"> {
  const registro = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  await navigator.serviceWorker.ready;
  const permissao = await Notification.requestPermission();
  if (permissao !== "granted") return "negada";
  const existente = await registro.pushManager.getSubscription();
  const sub =
    existente ??
    (await registro.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: chaveParaBytes(chavePublica) }));
  return inscricaoDoContrato(sub);
}

/** Desativar: cancela a inscrição no navegador e devolve o endpoint para a tela remover na API. */
export async function desativarNoAparelho(): Promise<string | null> {
  const registro = await navigator.serviceWorker.getRegistration("/sw.js");
  const sub = await registro?.pushManager.getSubscription();
  if (!sub) return null;
  const endpoint = sub.endpoint;
  await sub.unsubscribe();
  return endpoint;
}

/**
 * Sair da conta tira o aparelho dela (a inscrição é da conta: o próximo login no mesmo aparelho é de outra pessoa, E11):
 * cancela a inscrição no navegador e manda o DELETE enquanto a sessão ainda vale. Nunca trava nem derruba o logout: qualquer
 * falha ou demora (mais de 3 s) é ignorada, e a API passa o aparelho para a conta nova de qualquer jeito.
 */
export async function tirarAparelhoNoLogout(
  desativar: () => Promise<string | null>,
  remover: (endpoint: string) => Promise<void>,
  esperaMaximaEmMs = 3000,
): Promise<void> {
  const tarefa = (async () => {
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator) || !("PushManager" in window)) return;
    const endpoint = await desativar();
    if (endpoint) await remover(endpoint);
  })().catch(() => undefined);
  await Promise.race([tarefa, new Promise<void>((r) => setTimeout(r, esperaMaximaEmMs))]);
}
