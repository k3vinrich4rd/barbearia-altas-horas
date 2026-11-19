// Cliente tipado da API, sempre pelo BFF do próprio site (`/api/**`). Nenhum tipo de resposta escrito à mão:
// tudo vem de schema.d.ts, gerado do docs/contrato/openapi.yaml (E3).
import createClient from "openapi-fetch";
import type { paths } from "./schema";
import { comoErroApi, erroDeRede, montarErro, type ErroApi } from "./erro";
import { irPara } from "./navegacao";
import { destinoAposSessaoEncerrada } from "./sessao-encerrada";

function base(): string {
  return typeof window === "undefined" ? "http://localhost:3000/api" : `${window.location.origin}/api`;
}

export function criarCliente(baseUrl: string = base()) {
  // `fetch` resolvido na hora da chamada (e não na criação) para os testes poderem interceptar.
  return createClient<paths>({ baseUrl, fetch: (req) => globalThis.fetch(req) });
}

let instancia: ReturnType<typeof criarCliente> | undefined;
/** Cliente do navegador. */
export function api() {
  instancia ??= criarCliente();
  return instancia;
}

interface Resultado<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/**
 * Desembrulha o resultado do openapi-fetch: devolve o corpo de sucesso ou lança ErroApi.
 * Falha de rede e tempo esgotado viram API_INDISPONIVEL. Sessão encerrada (REFRESH_*) leva ao login.
 */
export async function chamar<T>(promessa: Promise<Resultado<T>>): Promise<T> {
  let r: Resultado<T>;
  try {
    r = await promessa;
  } catch (e) {
    if (e instanceof Error && e.name === "AbortError") throw e;
    throw erroDeRede();
  }
  if (!r.response.ok) {
    const erro = montarErro(r.response.status, r.error, r.response.headers.get("retry-after"));
    tratarSessaoEncerrada(erro);
    throw erro;
  }
  return r.data as T;
}

export function tratarSessaoEncerrada(erro: ErroApi): void {
  if (typeof window === "undefined") return;
  if (erro.code === "REFRESH_INVALIDO" || erro.code === "REFRESH_REUTILIZADO") {
    irPara(destinoAposSessaoEncerrada(window.location.pathname + window.location.search));
  } else if (erro.code === "TROCA_DE_SENHA_OBRIGATORIA" && window.location.pathname !== "/conta/trocar-senha") {
    irPara("/conta/trocar-senha");
  }
}

export { comoErroApi };
