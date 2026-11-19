import type { Metadata } from "next";
import { TrocarSenha } from "@/telas/TrocarSenha";

export const metadata: Metadata = { title: "Trocar senha" };

export default function Pagina() {
  return <TrocarSenha />;
}
