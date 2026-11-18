// Proxy do Next 16 (antes middleware.ts): cabeçalhos de segurança com nonce em toda resposta (E6) e
// encaminhamento de quem não tem sessão (E9). A autorização de verdade continua na API (401, 403, 404).
import { NextResponse, type NextRequest } from "next/server";
import { cabecalhosDeSeguranca, exigeLogin, semCache } from "@/bff/cabecalhos";
import { COOKIE_ACESSO, COOKIE_REFRESH, COOKIE_TROCA } from "@/bff/cookies";

const TROCA = "/conta/trocar-senha";

export function proxy(request: NextRequest): NextResponse {
  const { pathname } = request.nextUrl;
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const seg = cabecalhosDeSeguranca(nonce, process.env.NODE_ENV !== "production");
  const ehApi = pathname.startsWith("/api/");

  let resposta: NextResponse;
  const temSessao = request.cookies.has(COOKIE_ACESSO) || request.cookies.has(COOKIE_REFRESH);
  if (!ehApi && request.method === "GET" && request.cookies.has(COOKIE_TROCA) && temSessao && pathname !== TROCA) {
    resposta = NextResponse.redirect(new URL(TROCA, request.url));
  } else if (!ehApi && exigeLogin(pathname) && !temSessao) {
    const destino = new URL("/entrar", request.url);
    destino.searchParams.set("voltar", pathname + request.nextUrl.search);
    resposta = NextResponse.redirect(destino);
  } else {
    const cabecalhosDaRequisicao = new Headers(request.headers);
    cabecalhosDaRequisicao.set("x-nonce", nonce);
    cabecalhosDaRequisicao.set("content-security-policy", seg.csp);
    resposta = NextResponse.next({ request: { headers: cabecalhosDaRequisicao } });
  }
  resposta.headers.set("content-security-policy", seg.csp);
  for (const [k, v] of Object.entries(seg.fixos)) resposta.headers.set(k, v);
  if (ehApi || semCache(pathname)) resposta.headers.set("cache-control", "no-store");
  return resposta;
}

export const config = {
  matcher: [{ source: "/((?!_next/static|_next/image|assets/|favicon.ico).*)" }],
};
