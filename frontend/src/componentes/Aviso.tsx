"use client";
// Mensagens do design system (README, "Mensagens"): cada tipo tem forma, ícone e palavra próprios, nunca só cor.
//   erro   → caixa de contorno 2px em `erro`, ícone de alerta, prefixo "Erro:" e role="alert"
//   aviso  → contorno tracejado de 1px em `aviso`, ícone de informação e role="status"
//   sucesso→ bloco invertido (sucesso-fundo/sucesso-texto), ícone de visto e role="status"
import { useEffect, useRef, type ReactNode } from "react";
import { codigoParaSuporte, mensagemDoErro } from "@/api/mensagens";
import type { ErroApi } from "@/api/erro";
import { Icone, type NomeDeIcone } from "./Icone";

export type TipoDeAviso = "erro" | "sucesso" | "aviso" | "info";

const ICONE: Record<TipoDeAviso, NomeDeIcone> = { erro: "alerta", aviso: "info", info: "info", sucesso: "sucesso" };

export function Aviso({
  tipo = "info",
  children,
  id,
  focar = false,
}: {
  tipo?: TipoDeAviso;
  children: ReactNode;
  id?: string;
  /** Leva o foco ao aviso quando ele aparece (erro de página, sucesso). */
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
      <Icone nome={ICONE[tipo]} tamanho="medio" />
      <div className="aviso-texto">
        {tipo === "erro" && <span className="aviso-prefixo">Erro: </span>}
        {children}
      </div>
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
