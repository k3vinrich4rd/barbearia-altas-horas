import type { NextConfig } from "next";

// Cabeçalhos de segurança (CSP com nonce, nosniff, Referrer-Policy...) saem do proxy.ts (src/proxy.ts),
// porque a CSP precisa de um nonce novo por requisição (E6).
const config: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  images: { unoptimized: true },
};

export default config;
