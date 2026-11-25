"use client";
// Estados padrão de uma consulta: carregando, "servidor acordando" (RNF5) e erro com "tentar de novo".
import type { ReactNode } from "react";
import type { Consulta } from "@/api/hooks";
import { Aviso, AvisoDeErro } from "./Aviso";
import { Botao } from "./Botao";

import { TEXTO_ACORDANDO, TEXTO_SEM_RESPOSTA } from "@/api/mensagens";
import { TEXTOS } from "@/conteudo/textos";

// Ficam no catálogo de mensagens (Fatia 14B); reexportados para quem já importava daqui.
export { TEXTO_ACORDANDO, TEXTO_SEM_RESPOSTA };

/** M4 (Fatia 14D): blocos no lugar do texto, para a tela não pular quando os dados chegam (CLS). */
export function Esqueleto() {
  return (
    <div className="esqueleto" role="status" aria-busy="true">
      <span className="so-leitor">{TEXTOS.carregando}</span>
      <span className="esqueleto-bloco" aria-hidden="true" />
      <span className="esqueleto-bloco" aria-hidden="true" />
      <span className="esqueleto-bloco" aria-hidden="true" />
    </div>
  );
}

export function EstadoDaConsulta<T>({
  consulta,
  children,
  esconderErro = false,
}: {
  consulta: Consulta<T>;
  children: (dados: T) => ReactNode;
  /** Para blocos opcionais (alcance na home): falha some em silêncio. */
  esconderErro?: boolean;
}) {
  if (consulta.acordando) return <Aviso tipo="aviso">{TEXTO_ACORDANDO}</Aviso>;
  if (consulta.erro) {
    if (esconderErro) return null;
    return (
      <div>
        <AvisoDeErro erro={consulta.erro} final={TEXTO_SEM_RESPOSTA} focar={false} />
        <Botao onClick={consulta.recarregar}>Tentar de novo</Botao>
      </div>
    );
  }
  if (consulta.carregando || consulta.dados === undefined) return esconderErro ? null : <Esqueleto />;
  return <>{children(consulta.dados)}</>;
}
