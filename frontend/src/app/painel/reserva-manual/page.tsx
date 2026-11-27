import type { Metadata } from "next";
import { PainelReservaManual } from "@/telas/painel/PainelReservaManual";

export const metadata: Metadata = { title: "Fechar horário combinado no WhatsApp" };

export default function Pagina() {
  return <PainelReservaManual />;
}
