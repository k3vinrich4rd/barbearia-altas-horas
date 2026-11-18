"use client";
import { Botao } from "@/componentes/Botao";

export default function Erro({ reset }: { error: Error; reset: () => void }) {
  return (
    <>
      <h1>Algo deu errado</h1>
      <p>Algo deu errado do nosso lado. Tente de novo.</p>
      <Botao onClick={reset}>Tentar de novo</Botao>
    </>
  );
}
