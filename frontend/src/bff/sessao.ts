// Rotas próprias do BFF para a sessão (spec, "Rotas do próprio BFF"): login, cadastro, logout e /api/sessao.
// Os tokens só passam por aqui: vão para cookies HttpOnly e nunca para o corpo devolvido ao navegador.
import { COOKIE_ACESSO, COOKIE_REFRESH, cookiesApagados, cookiesDeSessao, lerCookies, type Tokens } from "./cookies";
import { resolver, type DepsBff, type DepsResolvidas } from "./config";
import { ipDoCliente, origemPermitida } from "./origem";
import { apiIndisponivel, origemNegada, problema } from "./problema";
import { chamarComSessao } from "./proxy";

const JSON_PROBLEMA = "application/problem+json";

function json(corpo: unknown, status: number, setCookies: string[] = []): Response {
  const h = new Headers({ "content-type": "application/json", "cache-control": "no-store" });
  for (const c of setCookies) h.append("set-cookie", c);
  return new Response(JSON.stringify(corpo), { status, headers: h });
}

/** Repassa o problem+json da API (ou 502 se a resposta não for JSON). */
async function passarErro(r: Response): Promise<Response> {
  const tipo = r.headers.get("content-type") ?? "";
  const texto = await r.text().catch(() => "");
  try {
    JSON.parse(texto);
  } catch {
    return apiIndisponivel();
  }
  if (!tipo.includes("json")) return apiIndisponivel();
  const h = new Headers({ "content-type": JSON_PROBLEMA, "cache-control": "no-store" });
  const espera = r.headers.get("retry-after");
  if (espera) h.set("retry-after", espera);
  return new Response(texto, { status: r.status, headers: h });
}

async function lerCorpo(request: Request): Promise<{ texto: string; json: Record<string, unknown> } | undefined> {
  const texto = await request.text();
  try {
    const j = JSON.parse(texto);
    return j && typeof j === "object" ? { texto, json: j as Record<string, unknown> } : undefined;
  } catch {
    return undefined;
  }
}

/** Cabeçalhos das chamadas próprias do BFF: leva o IP real do cliente (D156) para o limite por IP da API. */
function cabecalhosDaApi(request: Request, extra: Record<string, string> = {}): Record<string, string> {
  const ip = ipDoCliente(request);
  return { accept: "application/json", ...extra, ...(ip ? { "x-forwarded-for": ip } : {}) };
}

async function chamarLogin(deps: DepsResolvidas, request: Request, texto: string): Promise<Response> {
  return deps.fetch(`${deps.apiUrl}/auth/login`, {
    method: "POST",
    headers: cabecalhosDaApi(request, { "content-type": "application/json" }),
    body: texto,
    signal: AbortSignal.timeout(deps.tempoLimiteMs),
  });
}

function respostaDeSessao(tokens: Tokens, status = 200, extra: Record<string, unknown> = {}): Response {
  const deve = tokens.deveTrocarSenha === true;
  return json({ usuario: tokens.usuario, deveTrocarSenha: deve, ...extra }, status, cookiesDeSessao(tokens, deve));
}

export async function login(request: Request, depsEntrada?: DepsBff): Promise<Response> {
  if (!origemPermitida(request)) return origemNegada();
  const deps = resolver(depsEntrada);
  const corpo = await lerCorpo(request);
  if (!corpo) return problema(400, "JSON_MALFORMADO", "JSON malformado");
  try {
    const r = await chamarLogin(deps, request, corpo.texto);
    if (!r.ok) return passarErro(r);
    return respostaDeSessao((await r.json()) as Tokens);
  } catch {
    return apiIndisponivel();
  }
}

/** Cadastro: a API não devolve tokens, então depois do 201 o BFF faz o login com as mesmas credenciais. */
export async function cadastro(request: Request, depsEntrada?: DepsBff): Promise<Response> {
  if (!origemPermitida(request)) return origemNegada();
  const deps = resolver(depsEntrada);
  const corpo = await lerCorpo(request);
  if (!corpo) return problema(400, "JSON_MALFORMADO", "JSON malformado");
  try {
    const r = await deps.fetch(`${deps.apiUrl}/auth/cadastro`, {
      method: "POST",
      headers: cabecalhosDaApi(request, { "content-type": "application/json" }),
      body: corpo.texto,
      signal: AbortSignal.timeout(deps.tempoLimiteMs),
    });
    if (r.status !== 201) return passarErro(r);
    try {
      const l = await chamarLogin(deps, request, JSON.stringify({ email: corpo.json.email, senha: corpo.json.senha }));
      if (l.ok) return respostaDeSessao((await l.json()) as Tokens, 201, { sessao: true });
    } catch {
      // só o login falhou: a conta existe, a tela manda para /entrar
    }
    return json({ sessao: false }, 201);
  } catch {
    return apiIndisponivel();
  }
}

/**
 * Aceite do convite do barbeiro (Fatia 10B): `POST /api/convite`. Não existe na API do navegador de propósito: o BFF repassa
 * o corpo para `POST /auth/convite` (com o token da sessão, se houver, para quem já tem conta, H32) e NUNCA devolve o código
 * nem dado nenhum: só o status. Se o aceite soma o papel BARBEIRO a uma conta logada, a sessão local cai, para o token novo
 * (com o papel) vir do login.
 */
export async function convite(request: Request, depsEntrada?: DepsBff): Promise<Response> {
  if (!origemPermitida(request)) return origemNegada();
  const corpo = await lerCorpo(request);
  if (!corpo) return problema(400, "JSON_MALFORMADO", "JSON malformado");
  const cookies = lerCookies(request);
  const comSessao = Boolean(cookies[COOKIE_ACESSO] || cookies[COOKIE_REFRESH]);
  const { resposta, setCookies } = await chamarComSessao(request, "POST", "/auth/convite", {
    corpo: new TextEncoder().encode(corpo.texto).buffer as ArrayBuffer,
    modo: "opcional",
    deps: depsEntrada,
  });
  const h = new Headers(resposta.headers);
  h.set("cache-control", "no-store");
  for (const c of setCookies) h.append("set-cookie", c);
  if (resposta.status === 204 && comSessao) for (const c of cookiesApagados()) h.append("set-cookie", c);
  return new Response(resposta.status === 204 ? null : resposta.body, { status: resposta.status, headers: h });
}

/** Logout: a sessão local SEMPRE cai, mesmo com a API fora do ar. */
export async function logout(request: Request, depsEntrada?: DepsBff): Promise<Response> {
  if (!origemPermitida(request)) return origemNegada();
  const deps = resolver(depsEntrada);
  const refresh = lerCookies(request)[COOKIE_REFRESH];
  if (refresh) {
    try {
      await deps.fetch(`${deps.apiUrl}/auth/logout`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh }),
        signal: AbortSignal.timeout(deps.tempoLimiteMs),
      });
    } catch {
      // segue: apagar os cookies é o que importa para o navegador
    }
  }
  const h = new Headers({ "cache-control": "no-store" });
  for (const c of cookiesApagados()) h.append("set-cookie", c);
  return new Response(null, { status: 204, headers: h });
}

/** Estado da sessão para o menu e a proteção de rota. Não devolve token. */
export async function sessao(request: Request, depsEntrada?: DepsBff): Promise<Response> {
  const cookies = lerCookies(request);
  if (!cookies[COOKIE_ACESSO] && !cookies[COOKIE_REFRESH]) return json({ autenticado: false }, 200);
  const { resposta, setCookies } = await chamarComSessao(request, "GET", "/me", { modo: "bearer", deps: depsEntrada });
  if (resposta.status === 401) {
    const h = new Headers({ "content-type": "application/json", "cache-control": "no-store" });
    for (const c of cookiesApagados()) h.append("set-cookie", c);
    return new Response(JSON.stringify({ autenticado: false }), { status: 200, headers: h });
  }
  if (!resposta.ok) return resposta;
  const perfil = (await resposta.json()) as { id: string; nome: string; papeis: string[]; deveTrocarSenha?: boolean };
  return json(
    {
      autenticado: true,
      usuario: { id: perfil.id, nome: perfil.nome },
      papeis: perfil.papeis,
      deveTrocarSenha: perfil.deveTrocarSenha === true,
    },
    200,
    setCookies,
  );
}
