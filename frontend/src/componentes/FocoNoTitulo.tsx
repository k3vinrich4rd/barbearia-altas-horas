"use client";
// Mudou de rota: o foco vai para o h1 da página nova (A55). Na primeira carga o navegador cuida do foco.
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

export function FocoNoTitulo() {
  const caminho = usePathname();
  const primeira = useRef(true);
  useEffect(() => {
    if (primeira.current) {
      primeira.current = false;
      return;
    }
    const h1 = document.querySelector<HTMLElement>("main h1");
    if (h1) {
      h1.tabIndex = -1;
      h1.focus();
    }
  }, [caminho]);
  return null;
}
