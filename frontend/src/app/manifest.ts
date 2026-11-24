import type { MetadataRoute } from "next";

// Manifesto do app (Fatia 13, D177): é o que deixa o iPhone adicionar o site à Tela de Início, condição do Web Push no iOS.
// Cores dos tokens (design/tokens.json): fundo preto e texto branco da marca. Ícones: enquanto o Kevin não entrega os
// ícones desenhados (D-6), valem o logotipo existente e o mesmo arquivo nos tamanhos 192, 512 e "maskable".
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Barbearia Altas Horas",
    short_name: "Altas Horas",
    description: "Painel da barbearia: agenda e avisos de horários novos.",
    lang: "pt-BR",
    start_url: "/painel",
    scope: "/",
    display: "standalone",
    background_color: "#0A0A0A",
    theme_color: "#0A0A0A",
    icons: [
      { src: "/assets/logo-altas-horas.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/assets/logo-altas-horas.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/assets/logo-altas-horas.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
