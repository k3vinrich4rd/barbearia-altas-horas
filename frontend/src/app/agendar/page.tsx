import type { Metadata } from "next";
import { Suspense } from "react";
import { Agendar } from "@/telas/Agendar";
import { TEXTOS } from "@/conteudo/textos";

export const metadata: Metadata = { title: "Agendar horário" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">{TEXTOS.carregando}</p>}>
      <Agendar />
    </Suspense>
  );
}
