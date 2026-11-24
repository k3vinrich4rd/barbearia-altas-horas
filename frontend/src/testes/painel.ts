// Dados de exemplo (fictícios) do painel, no formato do contrato 1.7.0. Nada de e-mail nem CPF: a API não manda.
import type { EstadoDaSessao } from "@/api/bff";
import { BARBEIRO } from "./dados";

export const SESSAO_BARBEIRO: EstadoDaSessao = {
  autenticado: true,
  usuario: { id: "u3", nome: "Carlos Eduardo" },
  papeis: ["BARBEIRO"],
};

export const SERVICO_ID = "6f1c2a8e-0000-4000-8000-000000000002";

/** Um agendamento do painel (`AgendamentoAdmin`). `extra` sobrescreve qualquer campo. */
export const agendamentoAdmin = (extra: Record<string, unknown> = {}) => ({
  id: "a0000000-0000-4000-8000-000000000001",
  status: "CONFIRMADO",
  origem: "GUEST",
  servico: { id: SERVICO_ID, nome: "Corte Degradê / Fade / Navalhado", duracaoMin: 60 },
  barbeiro: BARBEIRO,
  inicio: "2026-11-24T10:00:00-03:00",
  fim: "2026-11-24T11:00:00-03:00",
  precoCentavos: 5500,
  expiraEm: null,
  motivoCancelamento: null,
  observacao: null,
  version: 3,
  cliente: { nome: "Rafael Souza", whatsapp: "11988880001" },
  cobrancaStatus: "PAGA",
  ...extra,
});

/** Terça a sábado, 07:00 às 12:00 e 13:00 às 20:00 (a lacuna é o almoço). */
export const JORNADA_PADRAO = [2, 3, 4, 5, 6].flatMap((diaSemana) => [
  { diaSemana, horaInicio: "07:00", horaFim: "12:00" },
  { diaSemana, horaInicio: "13:00", horaFim: "20:00" },
]);

export const barbeiroAdmin = (extra: Record<string, unknown> = {}) => ({
  ...BARBEIRO,
  whatsapp: null,
  usuarioId: null,
  ativo: true,
  version: 1,
  acesso: "SEM_CONTA",
  conviteExpiraEm: null,
  jornada: JORNADA_PADRAO,
  ...extra,
});

export const servicoAdmin = (extra: Record<string, unknown> = {}) => ({
  id: SERVICO_ID,
  nome: "Corte Degradê / Fade / Navalhado",
  duracaoMin: 60,
  precoCentavos: 5500,
  precoFormatado: "R$ 55,00",
  descricao: null,
  ativo: true,
  version: 2,
  ...extra,
});

export const cobrancaAdmin = (extra: Record<string, unknown> = {}) => ({
  id: "c0000000-0000-4000-8000-000000000001",
  reservaId: "a0000000-0000-4000-8000-000000000001",
  status: "DEVOLUCAO_PENDENTE",
  metodo: "PIX",
  valorCentavos: 6000,
  criadoEm: "2026-11-17T09:00:00-03:00",
  devolvidaEm: null,
  reserva: {
    id: "a0000000-0000-4000-8000-000000000001",
    inicio: "2026-11-19T13:00:00-03:00",
    servico: "Corte Moderno / Visagismo (Tesoura)",
    statusReserva: "CANCELADO",
    motivoCancelamento: "barbeiro em curso",
  },
  contato: { nome: "Lucas P.", whatsapp: "11987654321" },
  ...extra,
});

export const configuracaoAdmin = (extra: Record<string, unknown> = {}) => ({
  fuso: "America/Sao_Paulo",
  intervaloEntreAtendimentosMin: 0,
  granularidadeMin: 30,
  expiracaoPagamentoMin: 3,
  version: 2,
  ...extra,
});
