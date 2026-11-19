// Formatos do painel: instante no fuso da barbearia, semana, preço em centavos e máscara do WhatsApp.
import { FUSO_PADRAO, somarDias } from "./formato";

/**
 * Dia (YYYY-MM-DD) e hora (HH:mm) na parede do fuso da barbearia viram o instante ISO-8601 com o deslocamento daquele dia
 * ("2026-11-24T15:00:00-03:00"). Nunca usa o fuso do navegador (E10).
 */
export function instanteNoFuso(dia: string, hora: string, fuso: string = FUSO_PADRAO): string {
  const [a, m, d] = dia.split("-").map(Number);
  const [h, min] = hora.split(":").map(Number);
  const palpite = new Date(Date.UTC(a, m - 1, d, h, min));
  const nome =
    new Intl.DateTimeFormat("en-US", { timeZone: fuso, timeZoneName: "longOffset" })
      .formatToParts(palpite)
      .find((p) => p.type === "timeZoneName")?.value ?? "GMT";
  const deslocamento = nome === "GMT" ? "+00:00" : nome.replace("GMT", "");
  return `${dia}T${hora}:00${deslocamento}`;
}

/** Segunda-feira da semana de um YYYY-MM-DD (a semana do painel vai de segunda a domingo). */
export function segundaDaSemana(dia: string): string {
  const [a, m, d] = dia.split("-").map(Number);
  const diaDaSemana = new Date(Date.UTC(a, m - 1, d)).getUTCDay(); // 0 = domingo
  return somarDias(dia, -((diaDaSemana + 6) % 7));
}

/** Dia da semana ISO (1 = segunda ... 7 = domingo) de um YYYY-MM-DD. */
export function diaIsoDaSemana(dia: string): number {
  const [a, m, d] = dia.split("-").map(Number);
  return ((new Date(Date.UTC(a, m - 1, d)).getUTCDay() + 6) % 7) + 1;
}

/** "Terça-feira, 24 de novembro" de um YYYY-MM-DD, sem depender do fuso do navegador. */
export function formatarDiaPorExtenso(dia: string): string {
  const [a, m, d] = dia.split("-").map(Number);
  const texto = new Intl.DateTimeFormat("pt-BR", {
    timeZone: "UTC",
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date(Date.UTC(a, m - 1, d)));
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** "24/11/2026" de um YYYY-MM-DD. */
export function formatarDiaCurto(dia: string): string {
  const [a, m, d] = dia.split("-");
  return `${d}/${m}/${a}`;
}

/**
 * Preço digitado ("55,90", "1.234,5", "55") em centavos inteiros, só com inteiros (E3: nunca float).
 * Devolve `null` para texto, zero e negativo.
 */
export function centavosDoTexto(texto: string): number | null {
  const limpo = texto.replace(/^R\$\s*/, "").trim();
  const m = /^(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/.exec(limpo);
  if (!m) return null;
  const reais = Number(m[1].replace(/\./g, ""));
  const centavos = Number((m[2] ?? "").padEnd(2, "0"));
  const total = reais * 100 + centavos;
  return Number.isSafeInteger(total) && total > 0 ? total : null;
}

/** Os centavos como o campo de preço mostra ("5590" vira "55,90"), sem float. */
export function textoDeCentavos(centavos: number): string {
  return `${Math.trunc(centavos / 100)},${String(centavos % 100).padStart(2, "0")}`;
}

/** Só os números de um telefone. */
export const soNumeros = (texto: string) => texto.replace(/\D/g, "");

/** Máscara do WhatsApp enquanto digita: (11) 91234-5678 (celular) ou (11) 1234-5678; o 55 do país fica de fora. */
export function mascararWhatsapp(texto: string): string {
  let n = soNumeros(texto);
  if (n.length > 11 && n.startsWith("55")) n = n.slice(2);
  n = n.slice(0, 11);
  if (n.length <= 2) return n.length ? `(${n}` : "";
  const ddd = n.slice(0, 2);
  const resto = n.slice(2);
  const corte = resto.length > 8 ? 5 : 4;
  return resto.length <= corte ? `(${ddd}) ${resto}` : `(${ddd}) ${resto.slice(0, corte)}-${resto.slice(corte)}`;
}
