import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Página não encontrada" };

export default function NaoEncontrada() {
  return (
    <>
      <h1>Página não encontrada</h1>
      <p>Não encontramos o que você procura.</p>
      <p className="acoes">
        <Link href="/">Voltar para o início</Link>
        <Link href="/agendar">Agendar horário</Link>
        <Link href="/reserva">Consultar reserva</Link>
      </p>
    </>
  );
}
