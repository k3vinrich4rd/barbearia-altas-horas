"use client";
// Componente neutro de aviso (D-3, V4): só forma e texto, sem cor nova. O Kevin redesenha depois sem mexer nas telas.
import { useEffect, useRef, type ReactNode } from "react";
import { codigoParaSuporte, mensagemDoErro } from "@/api/mensagens";
import type { ErroApi } from "@/api/erro";

export type TipoDeAviso = "erro" | "sucesso" | "aviso" | "info";

const PREFIXO: Record<TipoDeAviso, string> = { erro: "Erro: ", sucesso: "Pronto: ", aviso: "Atenção: ", info: "" };

export function Aviso({
  tipo = "info",
  children,
  id,
  focar = false,
}: {
  tipo?: TipoDeAviso;
  children: ReactNode;
  id?: string;
  /** Leva o foco ao aviso quando ele aparece (erros de envio). */
  focar?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (focar) ref.current?.focus();
  }, [focar]);
  return (
    <div
      ref={ref}
      id={id}
      role={tipo === "erro" ? "alert" : "status"}
      tabIndex={focar ? -1 : undefined}
      className={`aviso aviso-${tipo}`}
      data-tipo={tipo}
    >
      <span className="aviso-prefixo">{PREFIXO[tipo]}</span>
      {children}
    </div>
  );
}

/** Erro da API como aviso: texto pelo `code` (catálogo) e o código para suporte. */
export function AvisoDeErro({ erro, id, focar = true, final }: { erro: ErroApi; id?: string; focar?: boolean; final?: string }) {
  const suporte = codigoParaSuporte(erro);
  return (
    <Aviso tipo="erro" id={id} focar={focar}>
      {erro.acordando && final ? final : mensagemDoErro(erro)}
      {suporte && <small className="aviso-suporte"> {suporte}</small>}
    </Aviso>
  );
}
