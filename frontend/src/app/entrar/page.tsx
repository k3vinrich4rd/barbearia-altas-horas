import type { Metadata } from "next";
import { Suspense } from "react";
import { Entrar } from "@/telas/Entrar";
import { TEXTOS } from "@/conteudo/textos";

export const metadata: Metadata = { title: "Entrar" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">{TEXTOS.carregando}</p>}>
      <Entrar />
    </Suspense>
  );
}
