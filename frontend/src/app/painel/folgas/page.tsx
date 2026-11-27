import type { Metadata } from "next";
import { Suspense } from "react";
import { TEXTOS } from "@/conteudo/textos";
import { ExigePapel } from "@/telas/AcessoNegado";
import { PainelFolgas } from "@/telas/painel/PainelFolgas";

export const metadata: Metadata = { title: "Férias e folgas" };

// Só ADMIN (Fatia 15, C13): a API também responde 403 (a guarda do navegador é só conforto). O barbeiro escolhido fica na
// URL (?barbeiro=), por isso o Suspense.
export default function Pagina() {
  return (
    <ExigePapel papeis={["ADMIN"]}>
      <Suspense fallback={<p role="status">{TEXTOS.carregando}</p>}>
        <PainelFolgas />
      </Suspense>
    </ExigePapel>
  );
}
