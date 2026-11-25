"use client";
// Calendário do mês do painel (Fatia 14C, seção 8 da spec, A48 e A49). AGUARDA DESENHO (seção 11): só componentes e
// tokens que já existem. Segue o padrão de grade de datas do WAI-ARIA: um dia só entra no Tab (tabindex 0), as setas
// movem o foco entre os dias, Home e End vão ao início e ao fim da semana, PageUp e PageDown trocam o mês.
import { useEffect, useRef, type KeyboardEvent } from "react";
import { somarDias } from "@/api/formato";
import { diaIsoDaSemana } from "@/api/formato-painel";
import { Botao } from "@/componentes/Botao";
import type { ResumoDoMes } from "./tipos";

const SEMANA = ["seg", "ter", "qua", "qui", "sex", "sáb", "dom"];
const SEMANA_POR_EXTENSO = ["segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"];
const MESES = [
  "janeiro",
  "fevereiro",
  "março",
  "abril",
  "maio",
  "junho",
  "julho",
  "agosto",
  "setembro",
  "outubro",
  "novembro",
  "dezembro",
];

/** "2026-12" do dia "2026-12-01". */
export const mesDoDia = (dia: string) => dia.slice(0, 7);

/** O mesmo dia no mês anterior ou seguinte (31/01 + 1 mês = 28/02). */
export function somarMeses(dia: string, meses: number): string {
  const [a, m, d] = dia.split("-").map(Number);
  const alvo = new Date(Date.UTC(a, m - 1 + meses, 1));
  const ultimo = new Date(Date.UTC(alvo.getUTCFullYear(), alvo.getUTCMonth() + 1, 0)).getUTCDate();
  return `${alvo.getUTCFullYear()}-${String(alvo.getUTCMonth() + 1).padStart(2, "0")}-${String(Math.min(d, ultimo)).padStart(2, "0")}`;
}

/** "Dezembro de 2026". */
export function nomeDoMes(mes: string): string {
  const [a, m] = mes.split("-").map(Number);
  const nome = MESES[m - 1];
  return `${nome.charAt(0).toUpperCase()}${nome.slice(1)} de ${a}`;
}

/** As partes do nome do dia que só o leitor de tela ouve (o número e as marcas já estão visíveis). */
function partesDoDia(dia: { data: string; reservas: number }) {
  const [, m] = dia.data.split("-").map(Number);
  return {
    semana: SEMANA_POR_EXTENSO[diaIsoDaSemana(dia.data) - 1],
    mes: `de ${MESES[m - 1]}`,
    reservas: dia.reservas === 0 ? "sem reservas" : dia.reservas === 1 ? "reserva" : "reservas",
  };
}

/** Nome completo do dia para o leitor de tela: "terça, 1 de dezembro, 3 reservas, folga". */
export function nomeDoDia(dia: { data: string; reservas: number; folga: boolean; expediente: boolean }): string {
  const [, m, d] = dia.data.split("-").map(Number);
  const partes = [
    SEMANA_POR_EXTENSO[diaIsoDaSemana(dia.data) - 1],
    `${d} de ${MESES[m - 1]}`,
    dia.reservas === 0 ? "sem reservas" : dia.reservas === 1 ? "1 reserva" : `${dia.reservas} reservas`,
  ];
  if (dia.folga) partes.push("folga");
  if (!dia.expediente) partes.push("fechado");
  return partes.join(", ");
}

export function CalendarioDoMes({
  resumo,
  foco,
  selecionado,
  hoje,
  aoFocar,
  aoEscolher,
}: {
  /** O resumo do mês do foco, ou nada enquanto carrega. */
  resumo: ResumoDoMes | undefined;
  /** O dia que tem o foco do teclado (define o mês mostrado). */
  foco: string;
  selecionado: string;
  hoje: string;
  aoFocar: (dia: string) => void;
  aoEscolher: (dia: string) => void;
}) {
  const mes = mesDoDia(foco);
  const grade = useRef<HTMLTableElement>(null);
  const peloTeclado = useRef(false);

  useEffect(() => {
    if (!peloTeclado.current) return;
    const botao = grade.current?.querySelector<HTMLButtonElement>(`[data-dia="${foco}"]`);
    if (botao) {
      botao.focus();
      peloTeclado.current = false;
    }
  }, [foco, resumo]);

  function mover(e: KeyboardEvent<HTMLButtonElement>, dia: string) {
    const semana = diaIsoDaSemana(dia);
    const destino: Record<string, () => string> = {
      ArrowLeft: () => somarDias(dia, -1),
      ArrowRight: () => somarDias(dia, 1),
      ArrowUp: () => somarDias(dia, -7),
      ArrowDown: () => somarDias(dia, 7),
      Home: () => somarDias(dia, 1 - semana),
      End: () => somarDias(dia, 7 - semana),
      PageUp: () => somarMeses(dia, -1),
      PageDown: () => somarMeses(dia, 1),
    };
    const ir = destino[e.key];
    if (!ir) return;
    e.preventDefault();
    peloTeclado.current = true;
    aoFocar(ir());
  }

  const primeiro = `${mes}-01`;
  const vazios = diaIsoDaSemana(primeiro) - 1;
  const dias = resumo?.mes === mes ? resumo.dias : [];
  const semanas: ((typeof dias)[number] | null)[][] = [];
  const celulas = [...Array.from({ length: vazios }, () => null), ...dias];
  for (let i = 0; i < celulas.length; i += 7) {
    const linha = celulas.slice(i, i + 7);
    while (linha.length < 7) linha.push(null);
    semanas.push(linha);
  }

  return (
    <section aria-labelledby="calendario-titulo" className="calendario-do-mes">
      <div className="secao-cabecalho">
        <h2 id="calendario-titulo">{nomeDoMes(mes)}</h2>
        <div className="acoes">
          <Botao onClick={() => aoFocar(somarMeses(foco, -1))}>Mês anterior</Botao>
          <Botao onClick={() => aoFocar(somarMeses(foco, 1))}>Mês seguinte</Botao>
        </div>
      </div>
      <div className="tabela-rolavel">
        <table
          ref={grade}
          className="calendario"
          aria-labelledby="calendario-titulo"
          aria-busy={!dias.length || undefined}
        >
          <thead>
            <tr>
              {SEMANA.map((d, i) => (
                <th key={d} scope="col" abbr={SEMANA_POR_EXTENSO[i]}>
                  {d}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {semanas.map((semana, i) => (
              <tr key={i}>
                {semana.map((dia, j) =>
                  dia ? (
                    <td key={dia.data}>
                      <button
                        type="button"
                        className="calendario-dia"
                        data-dia={dia.data}
                        data-hoje={dia.data === hoje || undefined}
                        data-folga={dia.folga || undefined}
                        data-fechado={!dia.expediente || undefined}
                        aria-pressed={dia.data === selecionado}
                        aria-current={dia.data === hoje ? "date" : undefined}
                        tabIndex={dia.data === foco ? 0 : -1}
                        onKeyDown={(e) => mover(e, dia.data)}
                        onClick={() => aoEscolher(dia.data)}
                      >
                        {/* o nome do botão é o texto visível mais o texto só do leitor, na ordem: "terça, 1 de dezembro, 3
                            reservas, folga" (WCAG 2.5.3: o nome contém o que se vê) */}
                        <span className="so-leitor">{partesDoDia(dia).semana},</span>{" "}
                        <span className="calendario-numero">{Number(dia.data.slice(8))}</span>{" "}
                        <span className="so-leitor">{partesDoDia(dia).mes},</span>{" "}
                        {dia.reservas > 0 && (
                          <>
                            <span className="calendario-contagem">{dia.reservas}</span>{" "}
                          </>
                        )}
                        <span className="so-leitor">{partesDoDia(dia).reservas}</span>
                        {dia.folga && (
                          <>
                            <span className="so-leitor">,</span> <span className="calendario-marca">folga</span>
                          </>
                        )}
                        {!dia.expediente && (
                          <>
                            <span className="so-leitor">,</span> <span className="calendario-marca">fechado</span>
                          </>
                        )}
                      </button>
                    </td>
                  ) : (
                    <td key={`vazio-${i}-${j}`} />
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
