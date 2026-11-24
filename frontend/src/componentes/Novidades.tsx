"use client";
// "Novidades" do painel (Fatia 13, A42): o selo do menu e a lista. A contagem vem da API (`naoLidas`) e se atualiza SEM polling:
// ao abrir o painel, ao voltar o foco para a aba e quando o service worker avisa que chegou um push (postMessage).
// Sem nome nem WhatsApp do cliente: o aviso só tem serviço, dia e hora. AGUARDA DESENHO (D148): layout neutro, só com tokens.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { api, chamar } from "@/api/cliente";
import type { components } from "@/api/schema";

export type AvisoDoPainel = components["schemas"]["AvisoDoPainel"];

interface Valor {
  naoLidas: number;
  itens: AvisoDoPainel[];
  carregado: boolean;
  recarregar: () => Promise<void>;
  /** Marca tudo como lido na API e zera o selo. Devolve `false` se a API recusou. */
  marcarLidas: () => Promise<boolean>;
}

const SEM_PROVEDOR: Valor = {
  naoLidas: 0,
  itens: [],
  carregado: false,
  recarregar: async () => {},
  marcarLidas: async () => false,
};

const Contexto = createContext<Valor>(SEM_PROVEDOR);

export function useNovidades(): Valor {
  return useContext(Contexto);
}

export function NovidadesProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<{ naoLidas: number; itens: AvisoDoPainel[]; carregado: boolean }>({
    naoLidas: 0,
    itens: [],
    carregado: false,
  });
  const vivo = useRef(true);

  const recarregar = useCallback(async () => {
    try {
      const r = await chamar(api().GET("/admin/notificacoes"));
      if (vivo.current) setEstado({ naoLidas: r.naoLidas, itens: r.itens, carregado: true });
    } catch {
      // O selo é um extra: falha da API não derruba o painel nem mostra erro; a lista mostra o seu próprio estado.
      if (vivo.current) setEstado((e) => ({ ...e, carregado: true }));
    }
  }, []);

  const marcarLidas = useCallback(async () => {
    try {
      await chamar(api().POST("/admin/notificacoes/marcar-lidas"));
    } catch {
      return false;
    }
    if (vivo.current) setEstado((e) => ({ ...e, naoLidas: 0, itens: e.itens.map((i) => ({ ...i, lido: true })) }));
    return true;
  }, []);

  useEffect(() => {
    vivo.current = true;
    void recarregar();
    const aoVoltar = () => {
      if (document.visibilityState === "visible") void recarregar();
    };
    window.addEventListener("focus", aoVoltar);
    document.addEventListener("visibilitychange", aoVoltar);
    // O service worker avisa as abas abertas quando chega um push: atualiza o selo sem polling.
    const aoReceber = (evento: MessageEvent) => {
      if (evento.data && typeof evento.data === "object" && evento.data.tipo === "novidade") void recarregar();
    };
    const sw = typeof navigator !== "undefined" ? navigator.serviceWorker : undefined;
    sw?.addEventListener("message", aoReceber);
    return () => {
      vivo.current = false;
      window.removeEventListener("focus", aoVoltar);
      document.removeEventListener("visibilitychange", aoVoltar);
      sw?.removeEventListener("message", aoReceber);
    };
  }, [recarregar]);

  const valor = useMemo(() => ({ ...estado, recarregar, marcarLidas }), [estado, recarregar, marcarLidas]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

/** O selo do menu: número E palavra para leitor de tela (nunca só o número solto). Some quando não há novidade. */
export function SeloDeNovidades() {
  const { naoLidas } = useNovidades();
  if (naoLidas <= 0) return null;
  return (
    <span className="selo-novidades" data-testid="selo-novidades">
      <span aria-hidden="true">{naoLidas > 99 ? "99+" : naoLidas}</span>
      <span className="so-leitor"> {naoLidas === 1 ? "novidade" : "novidades"}</span>
    </span>
  );
}
