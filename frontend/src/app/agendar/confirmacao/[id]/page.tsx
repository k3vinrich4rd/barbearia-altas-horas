import type { Metadata } from "next";
import { Confirmacao } from "@/telas/Confirmacao";

export const metadata: Metadata = { title: "Reserva confirmada" };

export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Confirmacao reservaId={id} />;
}
