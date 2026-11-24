// Proxy do BFF (E4, E5): o navegador só fala com `/api/**`; aqui a rota é conferida contra o contrato, o token da sessão
// (cookie HttpOnly) vira Authorization/X-Guest-Token e a renovação do acesso acontece uma única vez por refresh (R27).
import { ROTAS_COM_TOKEN_GUEST, ROTAS_PERMITIDAS, ROTAS_PUBLICAS } from "@/api/rotas-permitidas";
import {
  COOKIE_ACESSO,
  COOKIE_REFRESH,
  cookieVisitante,
  cookiesApagados,
  cookiesDeSessao,
  lerCookies,
  lerVisitante,
  type Tokens,
} from "./cookies";
import { resolver, type DepsBff, type DepsResolvidas } from "./config";
import { exigeOrigem, ipDoCliente, origemPermitida } from "./origem";
import { apiIndisponivel, naoEncontrado, origemNegada, problema } from "./problema";

interface Padrao {
  metodo: string;
  regex: RegExp;
}

function compilar(lista: readonly string[]): Padrao[] {
  return lista.map((r) => {
    const [metodo, caminho] = r.split(" ");
    const corpo = caminho
      .split("/")
      .map((s) => (s.startsWith("{") ? "[^/]+" : s.replace(/[.*+?^$()|[\]\\]/g, "\\$&")))
      .join("/");
    return { metodo, regex: new RegExp(`^${corpo}$`) };
  });
}

const PERMITIDAS = compilar(ROTAS_PERMITIDAS);
const PUBLICAS = compilar(ROTAS_PUBLICAS);
const COM_GUEST = compilar(ROTAS_COM_TOKEN_GUEST);

const casa = (lista: Padrao[], metodo: string, caminho: string) =>
  lista.some((p) => p.metodo === metodo && p.regex.test(caminho));

/** Segmentos sem `..`, `.`, vazios, barras, contrabarras ou caracteres de controle. */
function caminhoSeguro(segmentos: string[]): string | undefined {
  if (segmentos.length === 0) return undefined;
  for (const s of segmentos) {
    if (!s || s === "." || s === ".." || /[/\\\u0000-\u001f%]/.test(s)) return undefined;
  }
  return "/" + segmentos.join("/");
}

/** Resultado de uma renovação do refresh. */
type Renovacao = { ok: true; tokens: Tokens } | { ok: false; encerrada: boolean; status: number; corpo: string };

const emVoo = new Map<string, { promessa: Promise<Renovacao>; ate: number }>();
const FOLGA_MS = 10_000;

/** Renova com o refresh; chamadas simultâneas com o MESMO refresh compartilham uma só ida à API (R27). */
export function renovar(refresh: string, deps: DepsResolvidas): Promise<Renovacao> {
  const agora = Date.now();
  for (const [k, v] of emVoo) if (v.ate < agora) emVoo.delete(k);
  const existente = emVoo.get(refresh);
  if (existente) return existente.promessa;
  const promessa = (async (): Promise<Renovacao> => {
    try {
      const r = await deps.fetch(`${deps.apiUrl}/auth/refresh`, {
        method: "POST",
        headers: { "content-type": "application/json", accept: "application/json" },
        body: JSON.stringify({ refreshToken: refresh }),
        signal: AbortSignal.timeout(deps.tempoLimiteMs),
      });
      const texto = await r.text();
      if (r.ok) return { ok: true, tokens: JSON.parse(texto) as Tokens };
      let code = "";
      try {
        code = JSON.parse(texto).code ?? "";
      } catch {
        code = "";
      }
      return { ok: false, encerrada: r.status === 401 && code.startsWith("REFRESH_"), status: r.status, corpo: texto };
    } catch {
      return { ok: false, encerrada: false, status: 502, corpo: "" };
    }
  })();
  emVoo.set(refresh, { promessa, ate: agora + FOLGA_MS });
  void promessa.then((r) => {
    if (!r.ok) emVoo.delete(refresh);
  });
  return promessa;
}

function respostaDeRenovacaoFalha(r: Extract<Renovacao, { ok: false }>): Response {
  if (r.encerrada) {
    return new Response(r.corpo, {
      status: 401,
      headers: [
        ["content-type", "application/problem+json"],
        ["cache-control", "no-store"],
        ...cookiesApagados().map((c) => ["set-cookie", c] as [string, string]),
      ],
    });
  }
  return apiIndisponivel();
}

/** "opcional": manda o token da sessão SE houver (o aceite do convite de quem já tem conta, H32); sem sessão, vai sem token. */
type Modo = "publico" | "guest" | "misto" | "bearer" | "opcional";

function modoDaRota(metodo: string, caminho: string): Modo {
  if (casa(PUBLICAS, metodo, caminho)) return "publico";
  if ((metodo === "GET" || metodo === "DELETE") && caminho.startsWith("/agendamentos/guest/")) return "guest";
  if (casa(COM_GUEST, metodo, caminho)) return "misto";
  return "bearer";
}

const CABECALHOS_DE_RESPOSTA = ["content-type", "retry-after", "content-disposition"];

async function chamarApi(
  deps: DepsResolvidas,
  metodo: string,
  caminho: string,
  busca: string,
  corpo: ArrayBuffer | undefined,
  base: Headers,
  auth: { bearer?: string; guest?: string },
): Promise<Response> {
  const h = new Headers(base);
  if (auth.bearer) h.set("authorization", `Bearer ${auth.bearer}`);
  if (auth.guest) h.set("x-guest-token", auth.guest);
  return deps.fetch(`${deps.apiUrl}${caminho}${busca}`, {
    method: metodo,
    headers: h,
    body: corpo && corpo.byteLength > 0 ? corpo : undefined,
    redirect: "manual",
    signal: AbortSignal.timeout(deps.tempoLimiteMs),
  });
}

export interface Encaminhado {
  /** Resposta da API já filtrada (sem Set-Cookie, sem cabeçalho interno), sem os cookies novos. */
  resposta: Response;
  setCookies: string[];
}

/**
 * Chama a API pela sessão do navegador. Usado pelo proxy genérico e por `/api/sessao`.
 * `auth: 'sessao'` → Bearer do cookie (renovando uma vez); 'nenhuma' → sem token.
 */
export async function chamarComSessao(
  request: Request,
  metodo: string,
  caminho: string,
  opcoes: { busca?: string; corpo?: ArrayBuffer; modo: Modo; deps?: DepsBff },
): Promise<Encaminhado> {
  const deps = resolver(opcoes.deps);
  const cookies = lerCookies(request);
  const base = new Headers({ accept: "application/json" });
  const tipo = request.headers.get("content-type");
  if (tipo) base.set("content-type", tipo);
  const chave = request.headers.get("x-idempotency-key");
  if (chave) base.set("x-idempotency-key", chave);
  // o IP real do cliente, para o limite por IP e a auditoria da API não virarem o IP do BFF (D156)
  const ip = ipDoCliente(request);
  if (ip) base.set("x-forwarded-for", ip);

  let bearer: string | undefined;
  let guest: string | undefined;
  let setCookies: string[] = [];
  const modo = opcoes.modo;

  if (modo === "guest" || (modo === "misto" && !cookies[COOKIE_ACESSO] && !cookies[COOKIE_REFRESH])) {
    guest = request.headers.get("x-guest-token") ?? lerVisitante(request)?.token;
  }
  const refresh = cookies[COOKIE_REFRESH];
  const temSessao = Boolean(cookies[COOKIE_ACESSO] || refresh);
  const usaBearer = modo === "bearer" || (modo === "misto" && !guest) || (modo === "opcional" && temSessao);
  if (usaBearer) {
    bearer = cookies[COOKIE_ACESSO];
    if (!bearer && refresh) {
      const r = await renovar(refresh, deps);
      if (!r.ok) return { resposta: respostaDeRenovacaoFalha(r), setCookies: [] };
      bearer = r.tokens.accessToken;
      setCookies = cookiesDeSessao(r.tokens);
    }
  }

  let upstream: Response;
  try {
    upstream = await chamarApi(deps, metodo, caminho, opcoes.busca ?? "", opcoes.corpo, base, { bearer, guest });
    if (upstream.status === 401 && usaBearer && refresh && setCookies.length === 0) {
      const r = await renovar(refresh, deps);
      if (!r.ok) return { resposta: respostaDeRenovacaoFalha(r), setCookies: [] };
      setCookies = cookiesDeSessao(r.tokens);
      upstream = await chamarApi(deps, metodo, caminho, opcoes.busca ?? "", opcoes.corpo, base, {
        bearer: r.tokens.accessToken,
        guest,
      });
    }
  } catch {
    return { resposta: apiIndisponivel(), setCookies: [] };
  }
  return { resposta: await filtrar(upstream), setCookies };
}

/** Só passa adiante o que é seguro: status, corpo e poucos cabeçalhos. Corpo de erro que não é JSON vira 502. */
async function filtrar(upstream: Response): Promise<Response> {
  const tipo = upstream.headers.get("content-type") ?? "";
  const texto = upstream.status === 204 ? "" : await upstream.text().catch(() => "");
  if (upstream.status >= 400) {
    let ehJson = false;
    if (tipo.includes("json")) {
      try {
        JSON.parse(texto);
        ehJson = true;
      } catch {
        ehJson = false;
      }
    }
    if (!ehJson) return apiIndisponivel();
  }
  const h = new Headers({ "cache-control": "no-store" });
  for (const nome of CABECALHOS_DE_RESPOSTA) {
    const v = upstream.headers.get(nome);
    if (v) h.set(nome, v);
  }
  const semCorpo = upstream.status === 204 || upstream.status === 205 || upstream.status === 304;
  return new Response(semCorpo ? null : texto, { status: upstream.status, headers: h });
}

function comCookies(resposta: Response, setCookies: string[]): Response {
  const h = new Headers(resposta.headers);
  for (const c of setCookies) h.append("set-cookie", c);
  return new Response(resposta.body, { status: resposta.status, headers: h });
}

/** Rotas que a própria família `/api/auth/*` do BFF cuida: nunca pelo proxy genérico. */
const PROPRIAS = /^\/auth\//;

export async function encaminhar(request: Request, segmentos: string[], depsEntrada?: DepsBff): Promise<Response> {
  const metodo = request.method.toUpperCase();
  const caminho = caminhoSeguro(segmentos);
  if (!caminho || PROPRIAS.test(caminho) || !casa(PERMITIDAS, metodo, caminho)) return naoEncontrado();
  if (exigeOrigem(metodo) && !origemPermitida(request)) return origemNegada();

  const corpo = metodo === "GET" || metodo === "HEAD" ? undefined : await request.arrayBuffer();
  const busca = new URL(request.url).search;
  const { resposta, setCookies } = await chamarComSessao(request, metodo, caminho, {
    busca,
    corpo,
    modo: modoDaRota(metodo, caminho),
    deps: depsEntrada,
  });
  const pos = await aplicarPos(metodo, caminho, resposta);
  return comCookies(pos.resposta, [...setCookies, ...pos.setCookies]);
}

/**
 * Efeitos depois de certas respostas bem-sucedidas.
 * No 201 do POST /agendamentos/guest (D150): grava ah_visitante e devolve o corpo SEM tokenGuest.
 */
async function aplicarPos(
  metodo: string,
  caminho: string,
  resposta: Response,
): Promise<{ resposta: Response; setCookies: string[] }> {
  if (metodo === "POST" && caminho === "/agendamentos/guest" && resposta.status === 201) {
    try {
      const dados = await resposta.clone().json();
      if (typeof dados?.id === "string" && typeof dados?.tokenGuest === "string") {
        const semToken = { ...dados };
        delete semToken.tokenGuest;
        const corpo = JSON.stringify(semToken);
        const h = new Headers(resposta.headers);
        h.set("cache-control", "no-store");
        h.set("content-length", String(Buffer.byteLength(corpo, "utf8")));
        if (!h.has("content-type")) h.set("content-type", "application/json");
        return {
          resposta: new Response(corpo, { status: resposta.status, headers: h }),
          setCookies: [cookieVisitante(dados.id, dados.tokenGuest)],
        };
      }
    } catch {
      return { resposta, setCookies: [] };
    }
  }
  if (
    resposta.status === 204 &&
    ((metodo === "PUT" && caminho === "/me/senha") || (metodo === "DELETE" && caminho === "/me"))
  ) {
    return { resposta, setCookies: cookiesApagados() };
  }
  return { resposta, setCookies: [] };
}

export { problema };
export type { Modo };
