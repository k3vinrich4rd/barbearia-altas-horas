"use client";
// Contador do pagamento (README, "Contador do pagamento"). O tempo vem SEMPRE do `expiraEm` da API, nunca de um número fixo.
// É só um aviso: quem decide é o status vindo da API. role="timer" não fala a cada segundo; uma região
// aria-live à parte avisa a cada minuto, aos 30 segundos e no fim, e também quando o prazo ganha tempo (Fatia 14A).
// "Acrescentar 1 minuto" (D188): o botão só aparece dentro da janela que a API mandou (`prorrogacao`) e enquanto não foi usado.
// A tela decide pelo relógio do aparelho, mas quem decide de verdade é a API (um 422 faz o botão sumir, E9).
import { useEffect, useRef, useState } from "react";
import { formatarContagem } from "@/api/formato";
import type { components } from "@/api/schema";
import { Botao } from "./Botao";
import { TEXTOS } from "@/conteudo/textos";

export type Prorrogacao = NonNullable<components["schemas"]["Agendamento"]["prorrogacao"]>;

/** "1 minuto", "2 minutos" ou, se não for número redondo de minutos, "90 segundos". */
export function descreverTempo(segundos: number): string {
  if (segundos % 60 !== 0) return `${segundos} segundos`;
  const minutos = segundos / 60;
  return `${minutos} ${minutos === 1 ? "minuto" : "minutos"}`;
}

function anuncio(restante: number): string {
  if (restante <= 0) return TEXTOS.prazoZerado;
  if (restante <= 30) return "Faltam 30 segundos para pagar.";
  const minutos = Math.ceil(restante / 60);
  return `Faltam ${minutos} ${minutos === 1 ? "minuto" : "minutos"} para pagar.`;
}

const segundosAte = (alvo: number) => Math.max(0, Math.ceil((alvo - Date.now()) / 1000));

export function Cronometro({
  expiraEm,
  aoZerar,
  prorrogacao,
  acrescentando = false,
  aoAcrescentar,
}: {
  expiraEm: string;
  aoZerar: () => void;
  /** A janela do "Acrescentar 1 minuto" que a API mandou (nula quando não se aplica). */
  prorrogacao?: Prorrogacao | null;
  acrescentando?: boolean;
  aoAcrescentar?: () => void;
}) {
  const alvo = new Date(expiraEm).getTime();
  const [total] = useState(() => Math.max(1, Math.ceil((alvo - Date.now()) / 1000)));
  const [restante, setRestante] = useState(() => segundosAte(alvo));
  const [agora, setAgora] = useState(() => Date.now());
  const [falado, setFalado] = useState(() => anuncio(restante));
  const alvoAnterior = useRef(alvo);

  useEffect(() => {
    const t = setInterval(() => {
      setRestante(segundosAte(alvo));
      setAgora(Date.now());
    }, 1000);
    return () => clearInterval(t);
  }, [alvo]);

  // O prazo ganhou tempo (o 0:30 virou 1:30): o contador muda na hora e a região falada avisa o tempo novo.
  useEffect(() => {
    if (alvoAnterior.current === alvo) return;
    alvoAnterior.current = alvo;
    const novo = segundosAte(alvo);
    setRestante(novo);
    setAgora(Date.now());
    setFalado(anuncio(novo));
  }, [alvo]);

  // Só os marcos entram na região falada: a cada minuto cheio, aos 30 s e no fim.
  useEffect(() => {
    if (restante % 60 === 0 || restante === 30 || restante === 0) setFalado(anuncio(restante));
  }, [restante]);

  useEffect(() => {
    if (restante === 0) aoZerar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restante === 0]);

  const ultimoMinuto = restante > 0 && restante < 60;
  const janelaAberta =
    !!prorrogacao &&
    !prorrogacao.usada &&
    restante > 0 &&
    // +1 s: a API manda o abreEm em segundos inteiros (sem milissegundos), mas decide com o instante exato. Sem a folga, um toque
    // no primeiro segundo da janela podia chegar antes da abertura real e voltar 422 PRORROGACAO_FORA_DA_JANELA (o botão sumia).
    agora >= new Date(prorrogacao.abreEm).getTime() + 1000 &&
    agora < new Date(prorrogacao.fechaEm).getTime();
  const porcento = Math.min(100, Math.round((restante / total) * 100));
  // sem animação: a barra anda em degraus de 10 segundos
  const degrau = Math.min(100, Math.ceil(porcento / 10) * 10);
  return (
    <div className="cronometro">
      <p className="cronometro-linha">
        <span role="timer" aria-label="Tempo para pagar" className="tipo-contador">
          {formatarContagem(restante)}
        </span>
        <strong>para pagar</strong>
      </p>
      <div className="cronometro-barra" aria-hidden="true" data-ultimo={ultimoMinuto || undefined}>
        <div className="cronometro-barra-cheia" data-largura={degrau} />
      </div>
      {ultimoMinuto && (
        <p className="cronometro-ultimo">
          {janelaAberta && prorrogacao
            ? TEXTOS.ultimoMinutoComBotao(`Acrescentar ${descreverTempo(prorrogacao.segundos)}`)
            : TEXTOS.ultimoMinuto}
        </p>
      )}
      {janelaAberta && prorrogacao && aoAcrescentar && (
        <Botao onClick={aoAcrescentar} disabled={acrescentando}>
          {acrescentando ? "Acrescentando…" : `Acrescentar ${descreverTempo(prorrogacao.segundos)}`}
        </Botao>
      )}
      <p aria-live="polite" className="so-leitor">
        {falado}
      </p>
    </div>
  );
}
