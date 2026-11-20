// A conta da grade de horas da agenda (A1 a A4): linhas de 30 minutos, cada reserva no bloco certo e, quando duas reservas
// se sobrepõem (barbeiros diferentes em "Todos", ou um horário que expirou e foi reservado de novo), cada uma na sua faixa.
// Só números: o desenho vem do CSS por data-linha, data-span e data-faixa (nenhum style= no TSX, A56).
import { formatarHora } from "@/api/formato";
import { emMinutos } from "./tipos";

export const MINUTOS_POR_LINHA = 30;
/** A janela do canvas: 07:00 às 20:00 (26 linhas). Cresce, de hora em hora, se uma reserva cair fora dela. */
export const INICIO_PADRAO = 7 * 60;
export const FIM_PADRAO = 20 * 60;
/** Os limites que o CSS conhece (uma regra por valor): o dia inteiro tem 48 linhas. */
export const MAXIMO_DE_LINHAS = 48;
export const MAXIMO_DE_LINHAS_POR_BLOCO = 8;
export const MAXIMO_DE_FAIXAS = 6;

export interface LinhaDaGrade {
  linha: number;
  /** "07:00" nas horas cheias; nulo nas meias horas (o canvas só rotula a hora cheia). */
  rotulo: string | null;
  cheia: boolean;
}

export interface BlocoDaGrade<T> {
  item: T;
  linha: number;
  linhas: number;
  faixa: number;
  /** "09:00" e "10:00", no fuso da barbearia. */
  inicio: string;
  fim: string;
}

export interface AlmocoDaGrade {
  inicio: string;
  fim: string;
  linha: number;
  linhas: number;
}

export interface Grade<T> {
  linhas: LinhaDaGrade[];
  blocos: BlocoDaGrade<T>[];
  almocos: AlmocoDaGrade[];
  /** Quantas faixas lado a lado o dia precisa (1 quando ninguém se sobrepõe). */
  faixas: number;
}

const doisDigitos = (n: number) => String(n).padStart(2, "0");
const hhmm = (minutos: number) => `${doisDigitos(Math.floor(minutos / 60))}:${doisDigitos(minutos % 60)}`;
const limitar = (n: number, de: number, ate: number) => Math.min(ate, Math.max(de, n));

interface Entrada<T> {
  item: T;
  inicio: number;
  fim: number;
}

export function montarGrade<T extends { inicio: string; fim: string }>(
  itens: T[],
  almocos: { inicio: string; fim: string }[],
): Grade<T> {
  const reservas: Entrada<T>[] = itens
    .map((item) => {
      const inicio = emMinutos(formatarHora(item.inicio));
      const fim = emMinutos(formatarHora(item.fim));
      return { item, inicio, fim: fim > inicio ? fim : inicio + MINUTOS_POR_LINHA };
    })
    .sort((a, b) => a.inicio - b.inicio || a.fim - b.fim);
  const intervalos = almocos.map((a) => ({ inicio: emMinutos(a.inicio), fim: emMinutos(a.fim) }));

  const todos = [...reservas, ...intervalos];
  const primeiro = Math.min(INICIO_PADRAO, ...todos.map((e) => Math.floor(e.inicio / 60) * 60));
  const ultimo = Math.max(FIM_PADRAO, ...todos.map((e) => Math.ceil(e.fim / 60) * 60));
  const inicioDaJanela = limitar(primeiro, 0, 24 * 60 - 60);
  const totalDeLinhas = limitar(Math.ceil((ultimo - inicioDaJanela) / MINUTOS_POR_LINHA), 2, MAXIMO_DE_LINHAS);

  const linhas: LinhaDaGrade[] = Array.from({ length: totalDeLinhas }, (_, i) => {
    const minutos = inicioDaJanela + i * MINUTOS_POR_LINHA;
    const cheia = minutos % 60 === 0;
    return { linha: i + 1, rotulo: cheia ? hhmm(minutos) : null, cheia };
  });
  const linhaDe = (minutos: number) =>
    limitar(Math.floor((minutos - inicioDaJanela) / MINUTOS_POR_LINHA) + 1, 1, totalDeLinhas);
  const quantas = (de: number, ate: number, linha: number) =>
    limitar(Math.ceil((ate - de) / MINUTOS_POR_LINHA), 1, Math.min(MAXIMO_DE_LINHAS_POR_BLOCO, totalDeLinhas - linha + 1));

  // faixas: a primeira em que a reserva anterior já terminou; senão abre uma nova
  const fimDasFaixas: number[] = [];
  const blocos: BlocoDaGrade<T>[] = reservas.map((r) => {
    let faixa = fimDasFaixas.findIndex((fim) => fim <= r.inicio);
    if (faixa === -1) faixa = fimDasFaixas.length;
    fimDasFaixas[faixa] = r.fim;
    const linha = linhaDe(r.inicio);
    return {
      item: r.item,
      linha,
      linhas: quantas(r.inicio, r.fim, linha),
      faixa: Math.min(faixa + 1, MAXIMO_DE_FAIXAS),
      inicio: hhmm(r.inicio),
      fim: hhmm(r.fim),
    };
  });

  const blocosDeAlmoco: AlmocoDaGrade[] = almocos.map((a, i) => {
    const linha = linhaDe(intervalos[i].inicio);
    return { inicio: a.inicio, fim: a.fim, linha, linhas: quantas(intervalos[i].inicio, intervalos[i].fim, linha) };
  });

  return {
    linhas,
    blocos,
    almocos: blocosDeAlmoco,
    faixas: Math.min(Math.max(1, fimDasFaixas.length), MAXIMO_DE_FAIXAS),
  };
}
