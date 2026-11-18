"use client";
// Contagem regressiva até `expiraEm` (E12). É só um aviso: quem decide é o status vindo da API.
// Anunciada a leitor de tela só a cada minuto (sem aria-live por segundo).
import { useEffect, useState } from "react";
import { formatarContagem } from "@/api/formato";

export function Cronometro({ expiraEm, aoZerar }: { expiraEm: string; aoZerar: () => void }) {
  const alvo = new Date(expiraEm).getTime();
  const [restante, setRestante] = useState(() => Math.max(0, Math.ceil((alvo - Date.now()) / 1000)));

  useEffect(() => {
    const t = setInterval(() => setRestante(Math.max(0, Math.ceil((alvo - Date.now()) / 1000))), 1000);
    return () => clearInterval(t);
  }, [alvo]);

  useEffect(() => {
    if (restante === 0) aoZerar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restante === 0]);

  const minutos = Math.ceil(restante / 60);
  return (
    <div className="cronometro">
      <p>
        <span>Tempo para pagar: </span>
        <time aria-hidden="true">{formatarContagem(restante)}</time>
      </p>
      {/* Anúncio só a cada minuto: o texto muda uma vez por minuto. */}
      <p role="status" className="so-leitor">
        {restante > 0 ? `Faltam cerca de ${minutos} ${minutos === 1 ? "minuto" : "minutos"} para pagar.` : "Conferindo o prazo com o servidor…"}
      </p>
    </div>
  );
}
