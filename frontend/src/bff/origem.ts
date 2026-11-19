// Proteção CSRF (E6): além do SameSite=Strict, todo POST/PUT/PATCH/DELETE do BFF exige Origin igual ao site.
const ESCRITA = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export function exigeOrigem(metodo: string): boolean {
  return ESCRITA.has(metodo.toUpperCase());
}

/**
 * `Origin` ausente ou diferente do host do site: false.
 *
 * PREMISSA DE IMPLANTAÇÃO (D144): o host do site vem de `x-forwarded-host` quando existe. Isso só é seguro porque o
 * proxy da frente (a Vercel; um nginx ou Caddy em outra hospedagem) SOBRESCREVE esse cabeçalho com o host real da
 * requisição e descarta o que o navegador mandou. Se um proxy repassasse o `x-forwarded-host` do cliente, um atacante
 * escolheria o host e faria `Origin` e host coincidirem, anulando a verificação de CSRF. Quem expuser o `next start`
 * direto na internet (sem proxy) deve remover essa leitura. O `SameSite=Strict` dos cookies continua valendo.
 */
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
