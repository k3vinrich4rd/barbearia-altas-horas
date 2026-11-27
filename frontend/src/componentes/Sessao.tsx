"use client";
// Estado da sessão para o menu e as telas: vem de /api/sessao (o BFF lê o cookie; o token não chega aqui).
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { lerSessao, sair, type EstadoDaSessao } from "@/api/bff";

interface ContextoDaSessao extends EstadoDaSessao {
  carregando: boolean;
  atualizar: () => Promise<void>;
  encerrar: () => Promise<void>;
}

const Contexto = createContext<ContextoDaSessao>({
  autenticado: false,
  carregando: true,
  atualizar: async () => {},
  encerrar: async () => {},
});

export function SessaoProvider({ children, inicial }: { children: ReactNode; inicial?: EstadoDaSessao }) {
  const [estado, setEstado] = useState<EstadoDaSessao>(inicial ?? { autenticado: false });
  const [carregando, setCarregando] = useState(!inicial);

  const atualizar = useCallback(async () => {
    try {
      setEstado(await lerSessao());
    } catch {
      setEstado({ autenticado: false });
    } finally {
      setCarregando(false);
    }
  }, []);

  const encerrar = useCallback(async () => {
    try {
      await sair();
    } finally {
      setEstado({ autenticado: false });
    }
  }, []);

  useEffect(() => {
    if (!inicial) void atualizar();
  }, [inicial, atualizar]);

  // Volta pelo bfcache (Fatia 16, A29): a página reaparece com o estado de antes (talvez de antes do logout).
  // Pergunta de novo ao BFF, sem cache; até a resposta, o que já estava na tela fica, e um erro vira "sem sessão".
  useEffect(() => {
    function aoMostrar(e: PageTransitionEvent) {
      if (e.persisted) void atualizar();
    }
    window.addEventListener("pageshow", aoMostrar);
    return () => window.removeEventListener("pageshow", aoMostrar);
  }, [atualizar]);

  const valor = useMemo(
    () => ({ ...estado, carregando, atualizar, encerrar }),
    [estado, carregando, atualizar, encerrar],
  );
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export const useSessao = () => useContext(Contexto);
