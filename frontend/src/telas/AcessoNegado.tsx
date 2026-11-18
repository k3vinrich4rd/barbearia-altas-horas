"use client";
import Link from "next/link";
import type { ReactNode } from "react";
import { textoDoCodigo } from "@/api/mensagens";
import { useSessao } from "@/componentes/Sessao";

export function AcessoNegado() {
  return (
    <>
      <h1>Acesso negado</h1>
      <p>{textoDoCodigo("ACESSO_NEGADO")}</p>
      <p>
        <Link href="/">Voltar para o início</Link>
      </p>
    </>
  );
}

/** Guarda de papel no navegador. A autorização de verdade continua na API (403). */
export function ExigePapel({ papeis, children }: { papeis: string[]; children: ReactNode }) {
  const sessao = useSessao();
  if (sessao.carregando) return <p role="status">Carregando…</p>;
  if (!sessao.autenticado) {
    return (
      <p>
        <Link href="/entrar">Entre para continuar</Link>
      </p>
    );
  }
  if (!sessao.papeis?.some((p) => papeis.includes(p))) return <AcessoNegado />;
  return <>{children}</>;
}
