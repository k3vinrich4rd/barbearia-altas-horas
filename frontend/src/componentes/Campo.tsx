"use client";
// Campo de formulário acessível: rótulo sempre visível, erro por aria-describedby e aria-invalid (README, "Formulários").
import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { Icone } from "./Icone";

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
      <input ref={ref} id={id} aria-invalid={erro ? true : undefined} aria-describedby={ids} {...resto} />
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
    </div>
  );
});
