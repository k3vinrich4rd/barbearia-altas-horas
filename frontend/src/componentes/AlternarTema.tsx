"use client";
// Alterna `data-theme` do <html> entre escuro (padrão) e claro. Não grava nada no navegador.
import { useState } from "react";

export function AlternarTema() {
  const [tema, setTema] = useState<"escuro" | "claro">("escuro");
  function alternar() {
    const novo = tema === "escuro" ? "claro" : "escuro";
    document.documentElement.dataset.theme = novo;
    setTema(novo);
  }
  return (
    <button type="button" className="botao botao-secundario" onClick={alternar} aria-pressed={tema === "claro"}>
      Tema {tema === "escuro" ? "claro" : "escuro"}
    </button>
  );
}
