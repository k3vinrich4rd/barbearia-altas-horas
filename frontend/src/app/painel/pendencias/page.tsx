import type { Metadata } from "next";
import { ExigePapel } from "@/telas/AcessoNegado";
import { PainelPendencias } from "@/telas/painel/PainelPendencias";

export const metadata: Metadata = { title: "Pendências de pagamento" };

// Só ADMIN: a API também responde 403 (a guarda do navegador é só conforto).
export default function Pagina() {
  return (
    <ExigePapel papeis={["ADMIN"]}>
      <PainelPendencias />
    </ExigePapel>
  );
}
