// Para onde ir quando a renovação da sessão falha (A4) e como ler `voltar` sem abrir redirecionamento (E9).

/** `voltar` só vale se for caminho interno: começa com `/` e não com `//` nem `/\`. Senão, o padrão. */
export function voltarSeguro(voltar: string | null | undefined, padrao: string): string {
  if (!voltar || !voltar.startsWith("/") || voltar.startsWith("//") || voltar.startsWith("/\\")) return padrao;
  return voltar;
}

export const AVISO_SESSAO_ENCERRADA = "sessao";

/** Caminho do login depois de a sessão terminar, com o aviso "sua sessão terminou, entre de novo". */
export function destinoAposSessaoEncerrada(caminhoAtual: string): string {
  const voltar = voltarSeguro(caminhoAtual, "/meus-agendamentos");
  return `/entrar?voltar=${encodeURIComponent(voltar)}&aviso=${AVISO_SESSAO_ENCERRADA}`;
}
