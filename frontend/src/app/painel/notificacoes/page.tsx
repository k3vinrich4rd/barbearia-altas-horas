import type { Metadata } from "next";
import { PainelNotificacoes } from "@/telas/painel/PainelNotificacoes";

export const metadata: Metadata = { title: "Notificações" };

export default function Pagina() {
  return <PainelNotificacoes />;
}
