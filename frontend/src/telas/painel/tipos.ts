// Tipos do painel: todos saem do contrato (E3), nenhum escrito à mão.
import type { components } from "@/api/schema";

type Esquemas = components["schemas"];
export type AgendamentoAdmin = Esquemas["AgendamentoAdmin"];
export type ServicoAdmin = Esquemas["ServicoAdmin"];
export type BarbeiroAdmin = Esquemas["BarbeiroAdmin"];
export type IntervaloJornada = Esquemas["IntervaloJornada"];
export type Folga = Esquemas["Folga"];
export type ConfiguracaoAgenda = Esquemas["ConfiguracaoAgenda"];
export type CobrancaAdmin = Esquemas["CobrancaAdmin"];
export type EventoWebhookAdmin = Esquemas["EventoWebhookAdmin"];
export type PaginaAuditoria = Esquemas["PaginaAuditoria"];
export type AlcancePainel = Esquemas["AlcancePainel"];
export type MinhaJornada = Esquemas["MinhaJornada"];

/** Texto de cada status de cobrança (nunca só o código do contrato). */
export const STATUS_DA_COBRANCA: Record<string, string> = {
  PENDENTE: "Pagamento pendente",
  PAGA: "Paga",
  PAGA_APOS_EXPIRACAO: "Paga depois do prazo",
  CANCELADA: "Cobrança cancelada",
  FALHOU: "Pagamento falhou",
  DEVOLUCAO_PENDENTE: "Devolução pendente",
  DEVOLVIDA: "Devolvida",
};

/** Hora "07:00" no texto do desenho: "7h"; "12:30" vira "12h30". */
export function horaCurta(hhmm: string): string {
  const [h, m] = hhmm.split(":");
  return m === "00" ? `${Number(h)}h` : `${Number(h)}h${m}`;
}

/** Minutos desde a meia-noite de "HH:mm". */
export const emMinutos = (hhmm: string) => {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
};

/** Os intervalos de almoço de um dia ISO: a lacuna entre dois intervalos de trabalho do mesmo dia (A50). */
export function almocosDoDia(jornada: IntervaloJornada[], diaIso: number): { inicio: string; fim: string }[] {
  const doDia = jornada.filter((i) => i.diaSemana === diaIso).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  const lacunas: { inicio: string; fim: string }[] = [];
  for (let i = 1; i < doDia.length; i++)
    if (doDia[i - 1].horaFim < doDia[i].horaInicio)
      lacunas.push({ inicio: doDia[i - 1].horaFim, fim: doDia[i].horaInicio });
  return lacunas;
}

export const NOMES_DOS_DIAS = ["segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"];
