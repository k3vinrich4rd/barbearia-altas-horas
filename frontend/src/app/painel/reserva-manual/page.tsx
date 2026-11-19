import type { Metadata } from "next";
import { PainelReservaManual } from "@/telas/painel/PainelReservaManual";

export const metadata: Metadata = { title: "Reserva manual" };

export default function Pagina() {
  return <PainelReservaManual />;
}
