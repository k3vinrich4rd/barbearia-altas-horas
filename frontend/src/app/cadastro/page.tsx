import type { Metadata } from "next";
import { Suspense } from "react";
import { Cadastro } from "@/telas/Cadastro";

export const metadata: Metadata = { title: "Criar conta" };

export default function Pagina() {
  return (
    <Suspense fallback={<p role="status">Carregando…</p>}>
      <Cadastro />
    </Suspense>
  );
}
