// Cookies da sessão (E5, ADR 0006). Todos HttpOnly; Secure; SameSite=Strict: o JavaScript da página nunca os lê.
import type { components } from "@/api/schema";

export type Tokens = components["schemas"]["Tokens"];

export const COOKIE_REFRESH = "ah_refresh";
export const COOKIE_ACESSO = "ah_acesso";
export const COOKIE_VISITANTE = "ah_visitante";
/** Presença da sessão (valor "1", sem token): os cookies de token têm Path=/api e /api/auth, então o proxy.ts das PÁGINAS não os vê. */
export const COOKIE_PRESENCA = "ah_sessao";
/** Marca "a senha inicial ainda precisa ser trocada": deixa o proxy.ts mandar toda navegação para a troca (E9). */
export const COOKIE_TROCA = "ah_troca";

// DESVIO da spec (E5/A1 diziam Path=/api/auth): a renovação acontece no proxy de QUALQUER rota /api/**, e o navegador só envia o
// cookie para caminhos dentro do Path dele. Com /api/auth o refresh nunca chegaria em /api/me e a sessão cairia em 15 min.
export const CAMINHO_REFRESH = "/api";
export const CAMINHO_API = "/api";
export const VALIDADE_VISITANTE_S = 24 * 60 * 60;

interface Atributos {
  path: string;
  maxAge?: number;
  expires?: Date;
}

export function serializar(nome: string, valor: string, a: Atributos): string {
  const partes = [`${nome}=${encodeURIComponent(valor)}`, `Path=${a.path}`, "HttpOnly", "Secure", "SameSite=Strict"];
  if (a.maxAge !== undefined) partes.push(`Max-Age=${a.maxAge}`);
  if (a.expires) partes.push(`Expires=${a.expires.toUTCString()}`);
  return partes.join("; ");
}

export function lerCookies(request: Request): Record<string, string> {
  const saida: Record<string, string> = {};
  for (const par of (request.headers.get("cookie") ?? "").split(";")) {
    const i = par.indexOf("=");
    if (i < 0) continue;
    const nome = par.slice(0, i).trim();
    try {
      saida[nome] = decodeURIComponent(par.slice(i + 1).trim());
    } catch {
      saida[nome] = par.slice(i + 1).trim();
    }
  }
  return saida;
}

/** Os Set-Cookie de uma sessão nova (login ou renovação). */
export function cookiesDeSessao(tokens: Tokens, deveTrocarSenha?: boolean): string[] {
  const saida = [
    serializar(COOKIE_REFRESH, tokens.refreshToken, { path: CAMINHO_REFRESH, expires: new Date(tokens.refreshExpiraEm) }),
    serializar(COOKIE_ACESSO, tokens.accessToken, { path: CAMINHO_API, maxAge: tokens.expiraEmSegundos }),
    serializar(COOKIE_PRESENCA, "1", { path: "/", expires: new Date(tokens.refreshExpiraEm) }),
  ];
  if (deveTrocarSenha === true) saida.push(serializar(COOKIE_TROCA, "1", { path: "/", expires: new Date(tokens.refreshExpiraEm) }));
  if (deveTrocarSenha === false) saida.push(apagar(COOKIE_TROCA, "/"));
  return saida;
}

function apagar(nome: string, path: string): string {
  return serializar(nome, "", { path, maxAge: 0, expires: new Date(0) });
}

/** Apaga a sessão inteira (logout, refresh inválido, troca de senha, exclusão da conta). */
export function cookiesApagados(): string[] {
  return [apagar(COOKIE_REFRESH, CAMINHO_REFRESH), apagar(COOKIE_ACESSO, CAMINHO_API), apagar(COOKIE_PRESENCA, "/"), apagar(COOKIE_TROCA, "/")];
}

export function cookieVisitante(reservaId: string, token: string): string {
  const valor = Buffer.from(JSON.stringify({ reservaId, token })).toString("base64url");
  return serializar(COOKIE_VISITANTE, valor, { path: CAMINHO_API, maxAge: VALIDADE_VISITANTE_S });
}

export function lerVisitante(request: Request): { reservaId: string; token: string } | undefined {
  const bruto = lerCookies(request)[COOKIE_VISITANTE];
  if (!bruto) return undefined;
  try {
    const v = JSON.parse(Buffer.from(bruto, "base64url").toString("utf8"));
    return typeof v?.reservaId === "string" && typeof v?.token === "string" ? v : undefined;
  } catch {
    return undefined;
  }
}
