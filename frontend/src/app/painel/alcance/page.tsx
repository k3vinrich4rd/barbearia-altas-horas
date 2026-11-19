import type { Metadata } from "next";
import { PainelAlcance } from "@/telas/painel/PainelAlcance";

export const metadata: Metadata = { title: "Alcance por região" };

export default function Pagina() {
  return <PainelAlcance />;
}
