// Configuração do BFF. API_URL só existe no servidor (nunca NEXT_PUBLIC_).
export interface DepsBff {
  /** Para os testes trocarem a rede por um falso. */
  fetch?: typeof fetch;
  apiUrl?: string;
  tempoLimiteMs?: number;
}

export interface DepsResolvidas {
  fetch: typeof fetch;
  apiUrl: string;
  tempoLimiteMs: number;
}

export function resolver(deps: DepsBff = {}): DepsResolvidas {
  return {
    fetch: deps.fetch ?? ((...args) => globalThis.fetch(...args)),
    apiUrl: (deps.apiUrl ?? process.env.API_URL ?? "http://localhost:8080").replace(/\/$/, ""),
    tempoLimiteMs: deps.tempoLimiteMs ?? 10_000,
  };
}
