import type { Metadata } from "next";
import { ConsultaVisitante } from "@/telas/ConsultaVisitante";

export const metadata: Metadata = { title: "Consultar reserva" };

export default function Pagina() {
  return <ConsultaVisitante />;
}
