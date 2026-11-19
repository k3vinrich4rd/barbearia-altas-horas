// Datas e dinheiro (ADR 0004, E10). Sempre no fuso da barbearia que a API informa, nunca o do navegador.
export const FUSO_PADRAO = "America/Sao_Paulo";

export function formatarHora(iso: string, fuso: string = FUSO_PADRAO): string {
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: fuso,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(new Date(iso));
}

export function formatarDataCurta(iso: string, fuso: string = FUSO_PADRAO): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: fuso, day: "2-digit", month: "2-digit" }).format(new Date(iso));
}

export function formatarDiaDaSemana(iso: string, fuso: string = FUSO_PADRAO): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: fuso, weekday: "long" })
    .format(new Date(iso))
    .replace("-feira", "");
}

/** "terça, 24/11 às 10:00" */
export function formatarDataEHora(iso: string, fuso: string = FUSO_PADRAO): string {
  return `${formatarDiaDaSemana(iso, fuso)}, ${formatarDataCurta(iso, fuso)} às ${formatarHora(iso, fuso)}`;
}

export function formatarDataLonga(iso: string, fuso: string = FUSO_PADRAO): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: fuso, day: "2-digit", month: "2-digit", year: "numeric" }).format(
    new Date(iso),
  );
}

/** Dia (YYYY-MM-DD) de um instante no fuso da barbearia. */
export function diaNoFuso(data: Date, fuso: string = FUSO_PADRAO): string {
  const p = new Intl.DateTimeFormat("en-CA", {
    timeZone: fuso,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(data);
  return p;
}

/** Soma dias a um YYYY-MM-DD (aritmética de calendário, sem fuso). */
export function somarDias(dia: string, dias: number): string {
  const [a, m, d] = dia.split("-").map(Number);
  const x = new Date(Date.UTC(a, m - 1, d + dias));
  return x.toISOString().slice(0, 10);
}

/** Exibição de centavos (só na tela; o dado nunca vira float). Prefira o `precoFormatado` da API. */
export function formatarCentavos(centavos: number): string {
  const inteiro = Math.trunc(centavos / 100);
  const resto = String(Math.abs(centavos % 100)).padStart(2, "0");
  const milhar = String(inteiro).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `R$ ${milhar},${resto}`;
}

export function formatarPreco(servico: { precoCentavos: number; precoFormatado?: string }): string {
  return servico.precoFormatado ?? formatarCentavos(servico.precoCentavos);
}

/** Segundos viram "mm:ss" */
export function formatarContagem(totalSegundos: number): string {
  const s = Math.max(0, Math.floor(totalSegundos));
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}
