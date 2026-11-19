"use client";
// Contador do pagamento (README, "Contador do pagamento"). O tempo vem SEMPRE do `expiraEm` da API, nunca de um número fixo.
// É só um aviso: quem decide é o status vindo da API. role="timer" não fala a cada segundo; uma região
// aria-live à parte avisa a cada minuto, aos 30 segundos e no fim.
import { useEffect, useState } from "react";
import { formatarContagem } from "@/api/formato";

function anuncio(restante: number): string {
  if (restante <= 0) return "Conferindo o prazo com o servidor.";
  if (restante <= 30) return "Faltam 30 segundos para pagar.";
  const minutos = Math.ceil(restante / 60);
  return `Faltam ${minutos} ${minutos === 1 ? "minuto" : "minutos"} para pagar.`;
}

export function Cronometro({ expiraEm, aoZerar }: { expiraEm: string; aoZerar: () => void }) {
  const alvo = new Date(expiraEm).getTime();
  const [total] = useState(() => Math.max(1, Math.ceil((alvo - Date.now()) / 1000)));
  const [restante, setRestante] = useState(() => Math.max(0, Math.ceil((alvo - Date.now()) / 1000)));
  const [falado, setFalado] = useState(() => anuncio(restante));

  useEffect(() => {
    const t = setInterval(() => setRestante(Math.max(0, Math.ceil((alvo - Date.now()) / 1000))), 1000);
    return () => clearInterval(t);
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
      {ultimoMinuto && <p className="cronometro-ultimo">Falta menos de 1 minuto.</p>}
      <p aria-live="polite" className="so-leitor">
        {falado}
      </p>
    </div>
  );
}
