"use client";
// Campo de formulário acessível: label ligado, erro por aria-describedby e aria-invalid.
import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, "id"> {
  id: string;
  rotulo: string;
  erro?: string;
  ajuda?: ReactNode;
}

export const Campo = forwardRef<HTMLInputElement, Props>(function Campo({ id, rotulo, erro, ajuda, ...resto }, ref) {
  const ids = [ajuda ? `${id}-ajuda` : "", erro ? `${id}-erro` : ""].filter(Boolean).join(" ") || undefined;
  return (
    <div className="campo">
      <label htmlFor={id}>{rotulo}</label>
      {ajuda && (
        <p id={`${id}-ajuda`} className="campo-ajuda">
          {ajuda}
        </p>
      )}
      <input ref={ref} id={id} aria-invalid={erro ? true : undefined} aria-describedby={ids} {...resto} />
      {erro && (
        <p id={`${id}-erro`} className="campo-erro">
          <span className="aviso-prefixo">Erro: </span>
          {erro}
        </p>
      )}
    </div>
  );
});
