import type { ReactNode } from "react";
import { NavegacaoDoPainel } from "@/componentes/NavegacaoDoPainel";
import { NovidadesProvider } from "@/componentes/Novidades";
import { ExigePapel } from "@/telas/AcessoNegado";

export default function PainelLayout({ children }: { children: ReactNode }) {
  return (
    <ExigePapel papeis={["ADMIN", "BARBEIRO"]}>
      <NovidadesProvider>
        <div className="painel">
          <NavegacaoDoPainel />
          <div className="painel-conteudo">{children}</div>
        </div>
      </NovidadesProvider>
    </ExigePapel>
  );
}
