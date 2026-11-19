import type { Metadata } from "next";
import { MeusAgendamentos } from "@/telas/MeusAgendamentos";

export const metadata: Metadata = { title: "Meus agendamentos" };

export default function Pagina() {
  return <MeusAgendamentos />;
}
