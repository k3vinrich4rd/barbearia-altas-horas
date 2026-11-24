import type { Metadata } from "next";
import { PainelNovidades } from "@/telas/painel/PainelNovidades";

export const metadata: Metadata = { title: "Novidades" };

export default function Pagina() {
  return <PainelNovidades />;
}
