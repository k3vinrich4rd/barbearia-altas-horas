// Conversor único de erro da API (E8, ADR 0005): tudo vira ErroApi e a tela decide pelo `code`,
// nunca pelo `title` nem pelo `detail`.

/** Código que só existe no front: a API não respondeu (ou respondeu algo que não é problem+json). */
export const API_INDISPONIVEL = "API_INDISPONIVEL";

export interface ErroCampo {
  field: string;
  message: string;
}

export class ErroApi extends Error {
  readonly status: number;
  readonly code: string;
  readonly titulo?: string;
  readonly detalhe?: string;
  readonly traceId?: string;
  readonly erros: ErroCampo[];
  readonly retryAfter?: number;
  /** As extensões do problema (RFC 9457) além dos campos acima, como o `conflitos[]` do FOLGA_COM_RESERVAS (1.12.0). */
  readonly extensoes: Record<string, unknown>;

  constructor(dados: {
    status: number;
    code: string;
    titulo?: string;
    detalhe?: string;
    traceId?: string;
    erros?: ErroCampo[];
    retryAfter?: number;
    extensoes?: Record<string, unknown>;
  }) {
    super(dados.code);
    this.name = "ErroApi";
    this.status = dados.status;
    this.code = dados.code;
    this.titulo = dados.titulo;
    this.detalhe = dados.detalhe;
    this.traceId = dados.traceId;
    this.erros = dados.erros ?? [];
    this.retryAfter = dados.retryAfter;
    this.extensoes = dados.extensoes ?? {};
  }

  /** A API está fora do ar ou acordando (rede, timeout, 502/503 sem problem+json, HTML). */
  get acordando(): boolean {
    return this.code === API_INDISPONIVEL;
  }

  /** Mensagem do campo `field`, se a API mandou uma. */
  doCampo(campo: string): string | undefined {
    return this.erros.find((e) => e.field === campo)?.message;
  }
}

function segundos(valor: string | number | null | undefined): number | undefined {
  if (valor === null || valor === undefined || valor === "") return undefined;
  const n = Number(valor);
  return Number.isFinite(n) && n >= 0 ? Math.ceil(n) : undefined;
}

const CAMPOS_DO_PROBLEMA = new Set(["type", "title", "status", "detail", "instance", "code", "traceId", "errors"]);

/** Monta o ErroApi a partir do status, do corpo já lido (objeto ou texto) e do Retry-After. */
export function montarErro(status: number, corpo: unknown, retryAfter?: string | null): ErroApi {
  const p = typeof corpo === "object" && corpo !== null ? (corpo as Record<string, unknown>) : undefined;
  const code = p && typeof p.code === "string" && p.code ? p.code : undefined;
  if (!code) return new ErroApi({ status, code: API_INDISPONIVEL, retryAfter: segundos(retryAfter) });
  const erros = Array.isArray(p!.errors)
    ? (p!.errors as unknown[]).flatMap((e) => {
        const o = e as Record<string, unknown>;
        return typeof o?.field === "string" && typeof o?.message === "string"
          ? [{ field: o.field, message: o.message }]
          : [];
      })
    : [];
  return new ErroApi({
    status,
    code,
    titulo: typeof p!.title === "string" ? p!.title : undefined,
    detalhe: typeof p!.detail === "string" ? p!.detail : undefined,
    traceId: typeof p!.traceId === "string" ? p!.traceId : undefined,
    erros,
    retryAfter: segundos(retryAfter),
    extensoes: Object.fromEntries(Object.entries(p!).filter(([k]) => !CAMPOS_DO_PROBLEMA.has(k))),
  });
}

/** Lê uma Response de erro (problem+json, HTML ou vazia) e devolve o ErroApi. */
export async function erroDeResposta(resposta: Response): Promise<ErroApi> {
  const texto = await resposta.text().catch(() => "");
  let corpo: unknown = texto;
  try {
    corpo = JSON.parse(texto);
  } catch {
    corpo = texto;
  }
  return montarErro(resposta.status, corpo, resposta.headers.get("retry-after"));
}

/** Falha de rede ou tempo esgotado antes de qualquer resposta. */
export function erroDeRede(): ErroApi {
  return new ErroApi({ status: 0, code: API_INDISPONIVEL });
}

export function comoErroApi(e: unknown): ErroApi {
  return e instanceof ErroApi ? e : erroDeRede();
}
