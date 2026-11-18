import type { Metadata } from "next";
import { Privacidade } from "@/telas/Privacidade";

export const metadata: Metadata = { title: "Política de privacidade" };

export default function Pagina() {
  return <Privacidade />;
}
