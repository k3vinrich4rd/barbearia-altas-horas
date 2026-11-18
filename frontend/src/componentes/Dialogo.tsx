"use client";
// Diálogo modal acessível (E17): role="dialog", foco preso, Esc fecha e o foco volta ao botão de origem.
import { useEffect, useId, useRef, type ReactNode } from "react";

const FOCAVEIS = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Dialogo({ titulo, aoFechar, children }: { titulo: string; aoFechar: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const tituloId = useId();
  const origem = useRef<Element | null>(null);

  useEffect(() => {
    origem.current = document.activeElement;
    const primeiro = ref.current?.querySelector<HTMLElement>(FOCAVEIS);
    (primeiro ?? ref.current)?.focus();
    return () => {
      (origem.current as HTMLElement | null)?.focus?.();
    };
  }, []);

  function aoTeclar(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      aoFechar();
      return;
    }
    if (e.key !== "Tab") return;
    const itens = Array.from(ref.current?.querySelectorAll<HTMLElement>(FOCAVEIS) ?? []);
    if (itens.length === 0) {
      e.preventDefault();
      return;
    }
    const primeiro = itens[0];
    const ultimo = itens[itens.length - 1];
    const ativo = document.activeElement;
    if (e.shiftKey && (ativo === primeiro || !ref.current?.contains(ativo))) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && (ativo === ultimo || !ref.current?.contains(ativo))) {
      e.preventDefault();
      primeiro.focus();
    }
  }

  return (
    <div className="dialogo-fundo">
      <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={tituloId} tabIndex={-1} className="dialogo" onKeyDown={aoTeclar}>
        <h2 id={tituloId}>{titulo}</h2>
        {children}
      </div>
    </div>
  );
}
