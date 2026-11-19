// Status em texto (nunca só cor ou forma). Os valores vêm do enum StatusReserva do contrato.
export const STATUS_DA_RESERVA: Record<string, string> = {
  AGUARDANDO_PAGAMENTO: "Aguardando pagamento",
  CONFIRMADO: "Confirmado",
  CONCLUIDO: "Concluído",
  NAO_COMPARECEU: "Não compareceu",
  CANCELADO: "Cancelado",
  EXPIRADO: "Expirado",
};
