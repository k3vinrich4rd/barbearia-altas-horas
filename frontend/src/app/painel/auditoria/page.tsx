import type { Metadata } from "next";
import { ExigePapel } from "@/telas/AcessoNegado";
import { PainelAuditoria } from "@/telas/painel/PainelAuditoria";

export const metadata: Metadata = { title: "Auditoria" };

// Só ADMIN: a API também responde 403 (a guarda do navegador é só conforto).
export default function Pagina() {
  return (
    <ExigePapel papeis={["ADMIN"]}>
      <PainelAuditoria />
    </ExigePapel>
  );
}
