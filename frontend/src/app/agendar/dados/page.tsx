import type { Metadata } from "next";
import { Suspense } from "react";
import { ReservaDados } from "@/telas/ReservaDados";
import { TEXTOS } from "@/conteudo/textos";

export const metadata: Metadata = { title: "Seus dados" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">{TEXTOS.carregando}</p>}>
      <ReservaDados />
    </Suspense>
  );
}
