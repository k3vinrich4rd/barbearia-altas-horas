// Ícones de interface do desenho (Estados.dc.html): traço fino, cantos arredondados do próprio desenho, na cor do texto ao lado.
// Decorativos: sempre aria-hidden. O sentido vem do texto escrito ao lado (nunca só do ícone).
import type { ReactNode } from "react";

const CAMINHOS: Record<string, ReactNode> = {
  alerta: (
    <>
      <path d="M12 3 2 21h20L12 3z" />
      <path d="M12 10v5" />
      <path d="M12 18h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 11v6" />
      <path d="M12 7h.01" />
    </>
  ),
  sucesso: (
    <>
      <circle cx="12" cy="12" r="10" />
      <path d="m7.5 12.5 3 3 6-6.5" />
    </>
  ),
  visto: <path d="m5 12.5 4.5 4.5L19 7" />,
  x: (
    <>
      <path d="M6 6l12 12" />
      <path d="M18 6 6 18" />
    </>
  ),
  relogio: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  lixeira: (
    <>
      <path d="M4 7h16" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M6 7l1 13h10l1-13" />
      <path d="M9 7V4h6v3" />
    </>
  ),
  conversa: <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 8.6 8.6 0 0 1-3.8-.9L3 20.5l1.6-4.9A8.4 8.4 0 1 1 21 11.5z" />,
  copiar: (
    <>
      <rect x="9" y="9" width="11" height="11" rx="1" />
      <path d="M5 15V5a1 1 0 0 1 1-1h10" />
    </>
  ),
  voz: (
    <>
      <path d="M4 10v4h4l5 4V6L8 10H4z" />
      <path d="M16.5 9a4 4 0 0 1 0 6" />
    </>
  ),
};

export type NomeDeIcone = keyof typeof CAMINHOS;

export function Icone({ nome, tamanho = "pequeno" }: { nome: NomeDeIcone; tamanho?: "pequeno" | "medio" | "grande" }) {
  return (
    <svg
      className={`icone icone-${tamanho}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {CAMINHOS[nome]}
    </svg>
  );
}
