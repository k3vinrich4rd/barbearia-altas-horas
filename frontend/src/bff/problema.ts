// Respostas de erro do próprio BFF, no mesmo formato RFC 9457 da API (ADR 0005).
export function problema(
  status: number,
  code: string,
  titulo: string,
  setCookies: string[] = [],
  extras: Record<string, string> = {},
): Response {
  const cabecalhos = new Headers({
    "content-type": "application/problem+json",
    "cache-control": "no-store",
    ...extras,
  });
  for (const c of setCookies) cabecalhos.append("set-cookie", c);
  return new Response(JSON.stringify({ type: "about:blank", title: titulo, status, code }), {
    status,
    headers: cabecalhos,
  });
}

export const apiIndisponivel = (setCookies: string[] = []) =>
  problema(502, "API_INDISPONIVEL", "API indisponível", setCookies);
export const naoEncontrado = () => problema(404, "RECURSO_NAO_ENCONTRADO", "Não encontrado");
export const origemNegada = () => problema(403, "ACESSO_NEGADO", "Origem não permitida");
