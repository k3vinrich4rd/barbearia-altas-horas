"use client";
// A lista "Novidades" (Fatia 13, A42): os horários novos que o pagamento confirmou, do mais novo ao mais antigo. Sem nome nem
// WhatsApp do cliente: o nome o barbeiro vê abrindo a agenda do dia. Abrir a lista marca tudo como lido, mas os itens que eram
// novos continuam destacados enquanto a lista está aberta. AGUARDA DESENHO (D148): layout neutro, só com tokens.
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { diaNoFuso, formatarDataEHora } from "@/api/formato";
import { Aviso } from "@/componentes/Aviso";
import { useNovidades } from "@/componentes/Novidades";

/** "há 5 min", "há 2 h", "há 3 dias". */
export function haQuantoTempo(iso: string, agora: Date): string {
  const minutos = Math.max(0, Math.round((agora.getTime() - new Date(iso).getTime()) / 60_000));
  if (minutos < 1) return "agora há pouco";
  if (minutos < 60) return `há ${minutos} min`;
  const horas = Math.round(minutos / 60);
  if (horas < 24) return `há ${horas} h`;
  const dias = Math.round(horas / 24);
  return `há ${dias} ${dias === 1 ? "dia" : "dias"}`;
}

export function PainelNovidades() {
  const { itens, carregado, naoLidas, marcarLidas } = useNovidades();
  const [eramNovos, setEramNovos] = useState<Set<string> | null>(null);
  const jaMarcou = useRef(false);

  // Abrir a lista é lê-la: guarda quais eram novos (para destacar) e só então marca tudo como lido na API.
  useEffect(() => {
    if (!carregado || jaMarcou.current) return;
    jaMarcou.current = true;
    setEramNovos(new Set(itens.filter((i) => !i.lido).map((i) => i.id)));
    if (naoLidas > 0) void marcarLidas();
  }, [carregado, itens, naoLidas, marcarLidas]);

  const agora = new Date();
  return (
    <>
      <h1>Novidades</h1>
      <p>Horários novos com pagamento confirmado. Os avisos ficam aqui por 30 dias.</p>
      {!carregado ? (
        <p role="status">Carregando…</p>
      ) : itens.length === 0 ? (
        <p>Nenhuma novidade por enquanto.</p>
      ) : (
        <ul className="lista-novidades" aria-label="Horários novos">
          {itens.map((i) => (
            <li key={i.id}>
              {eramNovos?.has(i.id) && (
                <span className="etiqueta" data-status="confirmado">
                  Novo
                </span>
              )}{" "}
              <strong>{i.servico}</strong>
              <br />
              <span>{formatarDataEHora(i.inicio)}</span> · <small>{haQuantoTempo(i.criadoEm, agora)}</small>{" "}
              <Link href={`/painel?data=${diaNoFuso(new Date(i.inicio))}`}>Ver na agenda</Link>
            </li>
          ))}
        </ul>
      )}
      {carregado && itens.length >= 20 && <Aviso tipo="info">Mostrando os 20 avisos mais recentes.</Aviso>}
    </>
  );
}
