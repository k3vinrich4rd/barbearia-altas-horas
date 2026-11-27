// Chamadas às rotas PRÓPRIAS do BFF (que não existem na API, então não estão no schema.d.ts):
// login, cadastro, logout, sessão e o código do visitante.
import { comoErroApi, erroDeResposta, erroDeRede } from "./erro";
import { tratarSessaoEncerrada } from "./cliente";

const origem = () => (typeof window === "undefined" ? "http://localhost:3000" : window.location.origin);

async function pedir<T>(caminho: string, init?: RequestInit): Promise<T> {
  let r: Response;
  try {
    r = await fetch(`${origem()}${caminho}`, { ...init, credentials: "same-origin" });
  } catch {
    throw erroDeRede();
  }
  if (!r.ok) {
    const erro = await erroDeResposta(r);
    tratarSessaoEncerrada(erro);
    throw erro;
  }
  return (r.status === 204 ? undefined : await r.json()) as T;
}

const postar = <T>(caminho: string, corpo?: unknown) =>
  pedir<T>(caminho, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });

export interface UsuarioDaSessao {
  id?: string;
  nome?: string;
  papeis?: string[];
}
export interface RespostaLogin {
  usuario: UsuarioDaSessao;
  deveTrocarSenha: boolean;
  sessao?: boolean;
}
export interface EstadoDaSessao {
  autenticado: boolean;
  usuario?: { id: string; nome: string };
  papeis?: string[];
  deveTrocarSenha?: boolean;
}

export const entrar = (email: string, senha: string) => postar<RespostaLogin>("/api/auth/login", { email, senha });
export const cadastrarConta = (corpo: Record<string, unknown>) => postar<RespostaLogin>("/api/auth/cadastro", corpo);
/** Aceita o convite do barbeiro (10B). Sem `senha` quando a conta já existe e está logada (H32). Nunca devolve corpo. */
export const aceitarConvite = (codigo: string, email: string, senha?: string) =>
  postar<void>("/api/convite", { codigo, email, ...(senha === undefined ? {} : { senha }) });
export const sair = () => postar<void>("/api/auth/logout");
export const lerSessao = () => pedir<EstadoDaSessao>("/api/sessao", { cache: "no-store" });
export const codigoDoVisitante = (reservaId: string) =>
  pedir<{ reservaId: string; codigo: string }>(`/api/visitante?reservaId=${encodeURIComponent(reservaId)}`);

export { comoErroApi };
