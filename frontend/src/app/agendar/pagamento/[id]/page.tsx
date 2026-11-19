import type { Metadata } from "next";
import { Pagamento } from "@/telas/Pagamento";

export const metadata: Metadata = { title: "Pagamento" };

export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Pagamento reservaId={id} />;
}
