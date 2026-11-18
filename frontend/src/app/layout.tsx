import type { Metadata } from "next";
import type { ReactNode } from "react";
import "@fontsource/playfair-display/700.css";
import "@/estilos/tokens.css";
import "@/estilos/globais.css";
import { AlternarTema } from "@/componentes/AlternarTema";
import { FocoNoTitulo } from "@/componentes/FocoNoTitulo";
import { Menu } from "@/componentes/Menu";
import { SessaoProvider } from "@/componentes/Sessao";

export const metadata: Metadata = {
  title: { default: "Barbearia Altas Horas", template: "%s · Barbearia Altas Horas" },
  description: "Agende seu horário na Barbearia Altas Horas, em Osasco, desde 1997.",
};

// A CSP usa um nonce novo por requisição (proxy.ts): as páginas precisam ser renderizadas a cada pedido.
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" data-theme="escuro">
      <body>
        <a href="#conteudo" className="pular-conteudo">
          Ir para o conteúdo
        </a>
        <SessaoProvider>
          <Menu />
          <FocoNoTitulo />
          <main id="conteudo">{children}</main>
        </SessaoProvider>
        <footer className="rodape">
          <span>Barbearia Altas Horas · Jardim Bonança, Osasco · desde 1997</span>
          <a href="/privacidade">Política de privacidade</a>
          <AlternarTema />
        </footer>
      </body>
    </html>
  );
}
