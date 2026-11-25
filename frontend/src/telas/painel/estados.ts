// Forma de cada status da reserva na agenda do painel (data-estado; o CSS desenha, o texto diz o estado).
export const ESTADO_DO_STATUS: Record<string, string> = {
  CONFIRMADO: "confirmado",
  AGUARDANDO_PAGAMENTO: "aguardando",
  CONCLUIDO: "concluido",
  NAO_COMPARECEU: "nao-compareceu",
  CANCELADO: "cancelado",
  EXPIRADO: "expirado",
};
