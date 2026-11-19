import type { Metadata } from "next";
import { MinhaConta } from "@/telas/MinhaConta";

export const metadata: Metadata = { title: "Meus dados" };

export default function Pagina() {
  return <MinhaConta />;
}
