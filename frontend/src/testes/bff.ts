// Apoio dos testes do BFF: Request/Response de verdade e uma API falsa que registra cada chamada.
import { vi } from "vitest";

export const SITE = "http://localhost:3000";

export interface Chamada {
  url: string;
  caminho: string;
  metodo: string;
  headers: Headers;
  corpo: string;
}

export type ManipuladorApi = (chamada: Chamada) => Response | Promise<Response>;

export function apiFalsa(manipulador: ManipuladorApi) {
  const chamadas: Chamada[] = [];
  const fetchFalso = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const url = String(entrada);
    const chamada: Chamada = {
      url,
      caminho: new URL(url).pathname,
      metodo: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      corpo: init?.body ? Buffer.from(init.body as ArrayBuffer).toString("utf8") : "",
    };
    chamadas.push(chamada);
    return manipulador(chamada);
  });
  return {
    chamadas,
    deps: { fetch: fetchFalso as unknown as typeof fetch, apiUrl: "http://api.interna:8080" },
    quantas: (caminho: string) => chamadas.filter((c) => c.caminho === caminho).length,
  };
}

export const json = (corpo: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(status === 204 ? null : JSON.stringify(corpo), { status, headers: { "content-type": "application/json", ...headers } });

export const problemaApi = (status: number, code: string, extra: Record<string, unknown> = {}, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify({ type: "about:blank", title: "t", status, code, traceId: "trace-9", ...extra }), {
    status,
    headers: { "content-type": "application/problem+json", ...headers },
  });

export const tokensDeExemplo = (extra: Record<string, unknown> = {}) => ({
  accessToken: "jwt.acesso.exemplo",
  tipo: "Bearer",
  expiraEmSegundos: 900,
  refreshToken: "refresh-exemplo-1",
  refreshExpiraEm: "2026-11-25T12:00:00Z",
  usuario: { id: "u1", nome: "Cliente Teste", papeis: ["CLIENTE"] },
  deveTrocarSenha: false,
  ...extra,
});

export function requisicao(
  caminho: string,
  opcoes: { metodo?: string; origem?: string | null; cookies?: Record<string, string>; corpo?: unknown; headers?: Record<string, string> } = {},
): Request {
  const h = new Headers(opcoes.headers);
  const origem = opcoes.origem === undefined ? SITE : opcoes.origem;
  if (origem) h.set("origin", origem);
  if (opcoes.cookies) h.set("cookie", Object.entries(opcoes.cookies).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("; "));
  if (opcoes.corpo !== undefined) h.set("content-type", "application/json");
  return new Request(`${SITE}${caminho}`, {
    method: opcoes.metodo ?? "GET",
    headers: h,
    body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
  });
}

/** Valores dos Set-Cookie de uma resposta, por nome. */
export function cookiesDe(resposta: Response): Record<string, string> {
  const saida: Record<string, string> = {};
  for (const linha of resposta.headers.getSetCookie()) saida[linha.split("=")[0]] = linha;
  return saida;
}

/** Valor puro (decodificado) de um cookie gravado, para devolver na próxima requisição. */
export function valorDoCookie(linha: string): string {
  return decodeURIComponent(linha.split(";")[0].split("=").slice(1).join("="));
}
