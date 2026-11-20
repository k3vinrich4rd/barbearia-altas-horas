import type { Metadata } from "next";
import { PagamentoSimulado } from "@/telas/PagamentoSimulado";

export const metadata: Metadata = { title: "Pagamento com cartão" };

export default async function Pagina({ params }: { params: Promise<{ cobrancaId: string }> }) {
  const { cobrancaId } = await params;
  return <PagamentoSimulado cobrancaId={cobrancaId} />;
}
