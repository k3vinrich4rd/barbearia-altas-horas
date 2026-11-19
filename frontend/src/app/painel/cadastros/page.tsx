import type { Metadata } from "next";
import { ExigePapel } from "@/telas/AcessoNegado";
import { PainelCadastros } from "@/telas/painel/PainelCadastros";

export const metadata: Metadata = { title: "Cadastros" };

// Só ADMIN: a API também responde 403 (a guarda do navegador é só conforto).
export default function Pagina() {
  return (
    <ExigePapel papeis={["ADMIN"]}>
      <PainelCadastros />
    </ExigePapel>
  );
}
