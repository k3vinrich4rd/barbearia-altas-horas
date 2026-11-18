// Cabeçalhos de segurança de toda resposta (E6, A10) e as regras de navegação do proxy.ts (E9).
export interface Cabecalhos {
  csp: string;
  fixos: Record<string, string>;
}

export function politicaDeConteudo(nonce: string, desenvolvimento: boolean): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${desenvolvimento ? " 'unsafe-eval'" : ""}`,
    "style-src 'self'" + (desenvolvimento ? " 'unsafe-inline'" : ""),
    "img-src 'self' data:",
    "font-src 'self'",
    "connect-src 'self'",
    "frame-ancestors 'none'",
    "form-action 'self'",
    "base-uri 'self'",
    "object-src 'none'",
  ].join("; ");
}

export function cabecalhosDeSeguranca(nonce: string, desenvolvimento = false): Cabecalhos {
  return {
    csp: politicaDeConteudo(nonce, desenvolvimento),
    fixos: {
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "permissions-policy": "",
    },
  };
}

/** Páginas com dado pessoal e toda a API: nada de cache. */
export function semCache(caminho: string): boolean {
  return (
    caminho.startsWith("/api/") ||
    caminho.startsWith("/conta") ||
    caminho.startsWith("/minha-conta") ||
    caminho.startsWith("/meus-agendamentos") ||
    caminho.startsWith("/painel") ||
    caminho.startsWith("/agendar") ||
    caminho.startsWith("/reserva")
  );
}

const PROTEGIDAS = ["/meus-agendamentos", "/minha-conta", "/painel", "/conta"];

export function exigeLogin(caminho: string): boolean {
  return PROTEGIDAS.some((p) => caminho === p || caminho.startsWith(p + "/"));
}
