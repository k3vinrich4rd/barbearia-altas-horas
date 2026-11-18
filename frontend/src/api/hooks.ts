"use client";
// Hooks de dados: consulta com "servidor acordando" (E8) e envio com espera do 429 (A15).
import { useCallback, useEffect, useRef, useState } from "react";
import { comAcordando } from "./acordando";
import { comoErroApi, ErroApi } from "./erro";

export interface Consulta<T> {
  dados?: T;
  erro?: ErroApi;
  carregando: boolean;
  acordando: boolean;
  recarregar: () => void;
}

/** GET com nova tentativa quando a API está acordando. Refaz quando `chave` muda. */
export function useConsulta<T>(buscar: () => Promise<T>, chave: unknown[], ativo = true): Consulta<T> {
  const [estado, setEstado] = useState<{ dados?: T; erro?: ErroApi; carregando: boolean; acordando: boolean }>({
    carregando: ativo,
    acordando: false,
  });
  const [versao, setVersao] = useState(0);
  const buscarRef = useRef(buscar);
  useEffect(() => {
    buscarRef.current = buscar;
  });

  useEffect(() => {
    if (!ativo) return;
    const controle = new AbortController();
    setEstado({ carregando: true, acordando: false });
    comAcordando(() => buscarRef.current(), {
      sinal: controle.signal,
      aoMudar: (acordando) => {
        if (!controle.signal.aborted) setEstado((e) => ({ ...e, acordando }));
      },
    }).then(
      (dados) => {
        if (!controle.signal.aborted) setEstado({ dados, carregando: false, acordando: false });
      },
      (e) => {
        if (controle.signal.aborted || (e instanceof Error && e.name === "AbortError")) return;
        setEstado({ erro: comoErroApi(e), carregando: false, acordando: false });
      },
    );
    return () => controle.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ativo, versao, ...chave]);

  const recarregar = useCallback(() => setVersao((v) => v + 1), []);
  return { ...estado, recarregar };
}

/** Contagem regressiva em segundos (Retry-After). */
export function useEspera() {
  const [restante, setRestante] = useState(0);
  const contando = restante > 0;
  useEffect(() => {
    if (!contando) return;
    const t = setInterval(() => setRestante((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(t);
  }, [contando]);
  return { restante, iniciar: (segundos: number) => setRestante(Math.max(0, Math.ceil(segundos))) };
}

/**
 * Envio de formulário: um de cada vez (duplo clique não reserva duas vezes), guarda o erro e, no 429,
 * desabilita o botão até acabar o Retry-After.
 */
export function useEnvio() {
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<ErroApi | undefined>();
  const emCurso = useRef(false);
  const espera = useEspera();

  const executar = useCallback(
    async <R>(acao: () => Promise<R>): Promise<R | undefined> => {
      if (emCurso.current) return undefined;
      emCurso.current = true;
      setEnviando(true);
      setErro(undefined);
      try {
        return await acao();
      } catch (e) {
        const erroApi = comoErroApi(e);
        setErro(erroApi);
        if (erroApi.status === 429 || erroApi.code === "LIMITE_EXCEDIDO") espera.iniciar(erroApi.retryAfter ?? 5);
        return undefined;
      } finally {
        emCurso.current = false;
        setEnviando(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return {
    enviando,
    erro,
    limparErro: () => setErro(undefined),
    esperando: espera.restante > 0,
    restante: espera.restante,
    executar,
  };
}
