"use client";
// Seleção e texto longo no mesmo molde do Campo: rótulo sempre visível, ajuda e erro ligados por aria-describedby.
import type { ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { Icone } from "./Icone";

function ligacoes(id: string, ajuda: ReactNode, erro?: string) {
  return [ajuda ? `${id}-ajuda` : "", erro ? `${id}-erro` : ""].filter(Boolean).join(" ") || undefined;
}

function Rodape({ id, ajuda, erro }: { id: string; ajuda?: ReactNode; erro?: string }) {
  return (
    <>
      {ajuda && (
        <p id={`${id}-ajuda`} className="campo-ajuda">
          {ajuda}
        </p>
      )}
      {erro && (
        <p id={`${id}-erro`} className="campo-erro">
          <Icone nome="alerta" />
          <span>
            <span className="aviso-prefixo">Erro: </span>
            {erro}
          </span>
        </p>
      )}
    </>
  );
}

interface PropsDaSelecao extends Omit<SelectHTMLAttributes<HTMLSelectElement>, "id"> {
  id: string;
  rotulo: string;
  erro?: string;
  ajuda?: ReactNode;
}

export function Selecao({ id, rotulo, erro, ajuda, children, ...resto }: PropsDaSelecao) {
  return (
    <div className="campo">
      <label htmlFor={id}>{rotulo}</label>
      <select id={id} aria-invalid={erro ? true : undefined} aria-describedby={ligacoes(id, ajuda, erro)} {...resto}>
        {children}
      </select>
      <Rodape id={id} ajuda={ajuda} erro={erro} />
    </div>
  );
}

interface PropsDoTexto extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "id"> {
  id: string;
  rotulo: string;
  erro?: string;
  ajuda?: ReactNode;
  /** Mostra "n/máximo" embaixo do campo. */
  contador?: boolean;
}

export function AreaDeTexto({ id, rotulo, erro, ajuda, contador, maxLength, value, ...resto }: PropsDoTexto) {
  return (
    <div className="campo">
      <label htmlFor={id}>{rotulo}</label>
      <textarea
        id={id}
        rows={3}
        maxLength={maxLength}
        value={value}
        aria-invalid={erro ? true : undefined}
        aria-describedby={ligacoes(id, ajuda, erro)}
        {...resto}
      />
      {contador && maxLength !== undefined && (
        <p className="campo-ajuda" aria-live="off">
          {String(value ?? "").length}/{maxLength}
        </p>
      )}
      <Rodape id={id} ajuda={ajuda} erro={erro} />
    </div>
  );
}
