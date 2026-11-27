import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Página não encontrada" };

export default function NaoEncontrada() {
  return (
    <>
      <h1>Página não encontrada</h1>
      <p>Não encontramos o que você procura.</p>
      <p className="acoes">
        <Link href="/" className="link-solto">
          Voltar para o início
        </Link>
        <Link href="/agendar" className="link-solto">
          Agendar horário
        </Link>
        <Link href="/reserva" className="link-solto">
          Consultar reserva
        </Link>
      </p>
    </>
  );
}
