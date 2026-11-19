import type { Metadata } from "next";
import { Suspense } from "react";
import { Entrar } from "@/telas/Entrar";

export const metadata: Metadata = { title: "Entrar" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">Carregando…</p>}>
      <Entrar />
    </Suspense>
  );
}
