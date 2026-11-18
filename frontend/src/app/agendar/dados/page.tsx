import type { Metadata } from "next";
import { Suspense } from "react";
import { ReservaDados } from "@/telas/ReservaDados";

export const metadata: Metadata = { title: "Seus dados" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">Carregando…</p>}>
      <ReservaDados />
    </Suspense>
  );
}
