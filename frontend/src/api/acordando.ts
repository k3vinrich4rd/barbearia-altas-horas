// Nova tentativa do "servidor acordando" (E8, RNF5): a cada 3 s por até 70 s, uma requisição de cada vez.
import { comoErroApi, ErroApi } from "./erro";

export const INTERVALO_ACORDANDO_MS = 3000;
export const LIMITE_ACORDANDO_MS = 70000;

export interface OpcoesAcordando {
  /** Avisa a tela quando começa e quando termina a espera. */
  aoMudar?: (acordando: boolean) => void;
  intervaloMs?: number;
  limiteMs?: number;
  sinal?: AbortSignal;
}

function esperar(ms: number, sinal?: AbortSignal): Promise<void> {
  return new Promise((resolver, rejeitar) => {
    const t = setTimeout(resolver, ms);
    sinal?.addEventListener(
      "abort",
      () => {
        clearTimeout(t);
        rejeitar(new DOMException("cancelado", "AbortError"));
      },
      { once: true },
    );
  });
}

/**
 * Executa `fn` e, se a API estiver acordando (API_INDISPONIVEL), tenta de novo a cada 3 s até somar 70 s.
 * Só use com chamadas seguras de repetir (GET, ou POST com X-Idempotency-Key). Nunca há duas em paralelo:
 * a próxima só sai depois que a anterior terminou e o intervalo passou.
 */
export async function comAcordando<T>(fn: () => Promise<T>, opcoes: OpcoesAcordando = {}): Promise<T> {
  const intervalo = opcoes.intervaloMs ?? INTERVALO_ACORDANDO_MS;
  const limite = opcoes.limiteMs ?? LIMITE_ACORDANDO_MS;
  let esperado = 0;
  let avisou = false;
  for (;;) {
    try {
      const resultado = await fn();
      if (avisou) opcoes.aoMudar?.(false);
      return resultado;
    } catch (e) {
      const erro: ErroApi = comAcordandoErro(e);
      if (!erro.acordando) {
        if (avisou) opcoes.aoMudar?.(false);
        throw erro;
      }
      if (esperado + intervalo > limite) {
        if (avisou) opcoes.aoMudar?.(false);
        throw erro;
      }
      if (!avisou) {
        avisou = true;
        opcoes.aoMudar?.(true);
      }
      await esperar(intervalo, opcoes.sinal);
      esperado += intervalo;
    }
  }
}

function comAcordandoErro(e: unknown): ErroApi {
  return comoErroApi(e);
}
