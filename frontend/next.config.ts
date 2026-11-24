import type { NextConfig } from "next";

// Cabeçalhos de segurança (CSP com nonce, nosniff, Referrer-Policy...) saem do proxy.ts (src/proxy.ts),
// porque a CSP precisa de um nonce novo por requisição (E6).

/** O service worker do Web Push (Fatia 13): nunca em cache, para o navegador sempre buscar a versão nova (E7 da spec). */
export const CABECALHOS_DO_SERVICE_WORKER = [
  { key: "Cache-Control", value: "no-cache, no-store, must-revalidate" },
  { key: "Service-Worker-Allowed", value: "/" },
  { key: "Content-Type", value: "application/javascript; charset=utf-8" },
];

const config: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  // Fotos pelo otimizador do next/image (Fatia 12, A12 e A26): WebP, tamanhos por `sizes`, carregamento preguiçoso.
  images: { formats: ["image/webp"] },
  async headers() {
    return [{ source: "/sw.js", headers: CABECALHOS_DO_SERVICE_WORKER }];
  },
};

export default config;
