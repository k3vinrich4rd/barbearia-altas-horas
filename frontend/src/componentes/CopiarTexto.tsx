"use client";
import { useState } from "react";
import { Botao } from "./Botao";

/** Mostra um texto (copia e cola, código da reserva) com o botão "Copiar" que anuncia "copiado". */
export function CopiarTexto({
  texto,
  rotulo,
  id,
  botao = "Copiar",
}: {
  texto: string;
  rotulo: string;
  id: string;
  botao?: string;
}) {
  const [copiado, setCopiado] = useState(false);
  async function copiar() {
    try {
      await navigator.clipboard.writeText(texto);
      setCopiado(true);
    } catch {
      // sem permissão da área de transferência: o texto continua visível para copiar à mão
      setCopiado(false);
    }
  }
  return (
    <div className="copiar">
      <label htmlFor={id}>{rotulo}</label>
      <input id={id} readOnly value={texto} className="copiar-texto" onFocus={(e) => e.currentTarget.select()} />
      <Botao onClick={copiar}>{botao}</Botao>
      <span role="status" className="copiar-aviso">
        {copiado ? "Copiado" : ""}
      </span>
    </div>
  );
}
