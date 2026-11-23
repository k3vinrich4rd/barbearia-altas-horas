import type { Metadata } from "next";
import { Convite } from "@/telas/Convite";

// O código do convite vai no fragmento (#): o servidor nunca o recebe. A página não tem cache e sai sem Referer (proxy.ts).
export const metadata: Metadata = { title: "Criar minha senha", robots: { index: false, follow: false } };

export default function Pagina() {
  return <Convite />;
}
