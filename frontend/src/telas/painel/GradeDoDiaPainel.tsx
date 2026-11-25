"use client";
// Grade do dia do painel (Fatia 14C, seção 8 da spec, A50 e A52). AGUARDA DESENHO (seção 11): só componentes e tokens que
// já existem. Uma tabela com o horário no cabeçalho da linha e o barbeiro no cabeçalho da coluna. Cada célula diz o estado
// em TEXTO (Livre, o cliente, Almoço, Folga), nunca só pela cor. Com "Todos" no celular, um barbeiro por vez (seletor);
// a partir de 1024 px, lado a lado (CSS).
import Link from "next/link";
import { useState } from "react";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import { Selecao } from "@/componentes/CamposDoPainel";
import { ESTADO_DO_STATUS } from "./estados";
import { horaCurta, type AgendamentoAdmin, type GradeDoDia } from "./tipos";

type Coluna = GradeDoDia["colunas"][number];

function celula(coluna: Coluna, inicio: string, data: string, visivel: boolean) {
  const v = { "data-visivel": visivel || undefined };
  const horario = coluna.horarios.find((h) => h.inicio === inicio);
  if (!horario) return <td key={coluna.barbeiroId} {...v} data-estado="fora" />;
  if (horario.situacao === "OCUPADO") {
    const reserva = coluna.agendamentos.find((a) => a.id === horario.agendamentoId);
    return (
      <td key={coluna.barbeiroId} {...v} data-estado={reserva ? ESTADO_DO_STATUS[reserva.status] : "ocupado"}>
        {reserva ? <Reserva reserva={reserva} /> : "Ocupado"}
      </td>
    );
  }
  if (horario.situacao === "ALMOCO")
    return (
      <td key={coluna.barbeiroId} {...v} className="agenda-almoco" data-estado="almoco">
        Almoço
      </td>
    );
  if (horario.situacao === "FOLGA")
    return (
      <td key={coluna.barbeiroId} {...v} data-estado="folga">
        Folga
      </td>
    );
  const destino = `/painel/reserva-manual?${new URLSearchParams({ barbeiroId: coluna.barbeiroId, data, hora: inicio })}`;
  return (
    <td key={coluna.barbeiroId} {...v} data-estado="livre">
      Livre{" "}
      <Link href={destino} aria-label={`Marcar aqui: ${coluna.nomeExibicao}, ${horaCurta(inicio)}`}>
        Marcar aqui
      </Link>
    </td>
  );
}

function Reserva({ reserva }: { reserva: AgendamentoAdmin }) {
  return (
    <>
      <strong>{reserva.cliente.nome}</strong> · {reserva.servico?.nome} <EtiquetaDeStatus status={reserva.status} />
      {reserva.prorrogacao?.usada && <span className="selo-origem">Mais 1 minuto usado</span>}
    </>
  );
}

export function GradeDoDiaPainel({ grade, admin }: { grade: GradeDoDia; admin: boolean }) {
  const [visivel, setVisivel] = useState(grade.colunas[0]?.barbeiroId ?? "");
  const inicios = [...new Set(grade.colunas.flatMap((c) => c.horarios.map((h) => h.inicio)))].sort();
  const variasColunas = grade.colunas.length > 1;

  if (inicios.length === 0) return <p>Sem expediente neste dia.</p>;
  return (
    <>
      {admin && variasColunas && (
        <div className="grade-um-por-vez">
          <Selecao
            id="grade-barbeiro"
            rotulo="Ver a coluna de"
            value={visivel}
            onChange={(e) => setVisivel(e.target.value)}
          >
            {grade.colunas.map((c) => (
              <option key={c.barbeiroId} value={c.barbeiroId}>
                {c.nomeExibicao}
              </option>
            ))}
          </Selecao>
        </div>
      )}
      <div className="tabela-rolavel">
        <table className="grade-do-dia" data-varias-colunas={variasColunas || undefined}>
          <caption className="so-leitor">Horários do dia por barbeiro</caption>
          <thead>
            <tr>
              <th scope="col">Horário</th>
              {grade.colunas.map((c) => (
                <th key={c.barbeiroId} scope="col" data-visivel={c.barbeiroId === visivel || undefined}>
                  {c.nomeExibicao}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {inicios.map((inicio) => (
              <tr key={inicio}>
                <th scope="row">{inicio}</th>
                {grade.colunas.map((c) => celula(c, inicio, grade.data, c.barbeiroId === visivel))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
