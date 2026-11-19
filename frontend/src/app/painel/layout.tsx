import type { ReactNode } from "react";
import { NavegacaoDoPainel } from "@/componentes/NavegacaoDoPainel";
import { ExigePapel } from "@/telas/AcessoNegado";

export default function PainelLayout({ children }: { children: ReactNode }) {
  return (
    <ExigePapel papeis={["ADMIN", "BARBEIRO"]}>
      <div className="painel">
        <NavegacaoDoPainel />
        <div className="painel-conteudo">{children}</div>
      </div>
    </ExigePapel>
  );
}
