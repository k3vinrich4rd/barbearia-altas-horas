// Proteção CSRF (E6): além do SameSite=Strict, todo POST/PUT/PATCH/DELETE do BFF exige Origin igual ao site.
const ESCRITA = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export function exigeOrigem(metodo: string): boolean {
  return ESCRITA.has(metodo.toUpperCase());
}

/** `Origin` ausente ou diferente do host do site: false. */
export function origemPermitida(request: Request): boolean {
  const origem = request.headers.get("origin");
  if (!origem) return false;
  const hostDoSite = request.headers.get("x-forwarded-host") ?? new URL(request.url).host;
  try {
    return new URL(origem).host === hostDoSite;
  } catch {
    return false;
  }
}
