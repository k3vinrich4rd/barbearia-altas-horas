"use client";
// Mudou de rota: o foco vai para o h1 da página nova (A55), salvo quando o destino é uma seção da home. Na primeira carga o navegador cuida do foco.
// Páginas com Suspense só têm o h1 depois de montar: se ele ainda não existe, espera ele aparecer (até 3 s).
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { ancoraPendente } from "./ancoras";

export function FocoNoTitulo() {
  const caminho = usePathname();
  const primeira = useRef(true);
  useEffect(() => {
    if (primeira.current) {
      primeira.current = false;
      return;
    }
    // Veio de um link para uma seção da home: o foco vai ao título da seção (NavegacaoPorAncora), não ao h1 do herói.
    if (ancoraPendente()) return;
    const focar = () => {
      const h1 = document.querySelector<HTMLElement>("main h1");
      if (!h1) return false;
      h1.tabIndex = -1;
      h1.focus();
      return true;
    };
    if (focar()) return;
    const raiz = document.querySelector("main") ?? document.body;
    const observador = new MutationObserver(() => {
      if (focar()) observador.disconnect();
    });
    observador.observe(raiz, { childList: true, subtree: true });
    const fim = setTimeout(() => observador.disconnect(), 3000);
    return () => {
      observador.disconnect();
      clearTimeout(fim);
    };
  }, [caminho]);
  return null;
}
