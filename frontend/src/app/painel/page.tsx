import type { Metadata } from "next";
import { Suspense } from "react";
import { PainelAgenda } from "@/telas/painel/PainelAgenda";

export const metadata: Metadata = { title: "Agenda do painel" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">Carregando…</p>}>
      <PainelAgenda />
    </Suspense>
  );
}
