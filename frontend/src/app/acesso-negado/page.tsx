import type { Metadata } from "next";
import { AcessoNegado } from "@/telas/AcessoNegado";

export const metadata: Metadata = { title: "Acesso negado" };

export default function Pagina() {
  return <AcessoNegado />;
}
