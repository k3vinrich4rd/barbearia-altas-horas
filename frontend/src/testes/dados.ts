// Dados de exemplo (fictícios) no formato do contrato.
export const servico = (n: number, extra: Record<string, unknown> = {}) => ({
  id: `6f1c2a8e-0000-4000-8000-00000000000${n}`,
  nome: [
    "Corte Clássico / Social",
    "Corte Degradê / Fade / Navalhado",
    "Corte Moderno / Visagismo (Tesoura)",
    "Barba Completa / Desenhada",
    "Acabamento (Pezinho) / Sobrancelha",
  ][n - 1],
  duracaoMin: 60,
  precoCentavos: [4500, 5500, 6000, 3000, 2000][n - 1],
  precoFormatado: ["R$ 45,00", "R$ 55,00", "R$ 60,00", "R$ 30,00", "R$ 20,00"][n - 1],
  ...extra,
});

export const CINCO_SERVICOS = [1, 2, 3, 4, 5].map((n) => servico(n));

export const BARBEIRO = { id: "b0000000-0000-4000-8000-000000000001", nomeExibicao: "Carlos Eduardo" };

export const reserva = (extra: Record<string, unknown> = {}) => ({
  id: "r0000000-0000-4000-8000-000000000001",
  status: "AGUARDANDO_PAGAMENTO",
  origem: "GUEST",
  servico: { id: servico(2).id, nome: servico(2).nome, duracaoMin: 60 },
  barbeiro: BARBEIRO,
  inicio: "2026-11-24T10:00:00-03:00",
  fim: "2026-11-24T11:00:00-03:00",
  precoCentavos: 5500,
  precoFormatado: "R$ 55,00",
  expiraEm: "2026-11-18T08:03:00-03:00",
  motivoCancelamento: null,
  version: 0,
  ...extra,
});

export const POLITICA_PAGAMENTO = {
  versao: 1,
  texto:
    "Após o pagamento antecipado de 100%, não há cancelamento, remarcação ou estorno em caso de desistência ou falta.",
};

export const POLITICA_PRIVACIDADE = {
  versao: "1.0",
  textoConsentimento: "Aceito receber ofertas da barbearia pelo WhatsApp.",
  politica: "# Política\n\nTexto.\n\n- item um\n- item dois",
  atualizadaEm: "2026-11-10T12:00:00-03:00",
};

/** CPF válido só para teste (dígitos verificadores corretos, não é de ninguém). */
export const CPF_DE_TESTE = "529.982.247-25";
