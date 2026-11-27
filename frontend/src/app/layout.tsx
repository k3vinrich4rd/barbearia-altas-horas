import type { Metadata } from "next";
import { Playfair_Display } from "next/font/google";
import type { ReactNode } from "react";
import "@/estilos/tokens.css";
import "@/estilos/globais.css";
import "@/estilos/navegacao.css";
import { BarraAgendar } from "@/componentes/BarraAgendar";
import { FocoNoTitulo } from "@/componentes/FocoNoTitulo";
import { Menu } from "@/componentes/Menu";
import { NavegacaoPorAncora } from "@/componentes/NavegacaoPorAncora";
import { PainelDeAcessibilidade } from "@/componentes/PainelDeAcessibilidade";
import { Rodape } from "@/componentes/Rodape";
import { SessaoProvider } from "@/componentes/Sessao";

// D-1 (design system v2, 18/11/2026): Playfair Display 700, servida pelo nosso domínio no build (nenhum pedido ao Google
// em tempo de execução), com display: swap. A variável alimenta `--font-display` em globais.css; Georgia é a reserva.
const playfair = Playfair_Display({ weight: "700", subsets: ["latin"], display: "swap", variable: "--fonte-playfair" });

export const metadata: Metadata = {
  title: { default: "Barbearia Altas Horas", template: "%s · Barbearia Altas Horas" },
  description: "Agende seu horário na Barbearia Altas Horas, em Osasco, desde 1997.",
};

// A CSP usa um nonce novo por requisição (proxy.ts): as páginas precisam ser renderizadas a cada pedido.
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" data-theme="escuro" className={playfair.variable}>
      <body>
        <a href="#conteudo" className="pular-conteudo">
          Pular para o conteúdo
        </a>
        <div className="xadrez" aria-hidden="true" />
        <SessaoProvider>
          <PainelDeAcessibilidade />
          <Menu />
          <FocoNoTitulo />
          <NavegacaoPorAncora />
          <main id="conteudo">{children}</main>
        </SessaoProvider>
        <Rodape />
        <BarraAgendar />
      </body>
    </html>
  );
}
