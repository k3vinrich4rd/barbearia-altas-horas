"use client";
// Barra fixa "Agendar horário" (Fatia 12, E8 e D-7): só no celular (abaixo de 768 px, pelo CSS), com largura total e a
// área segura do aparelho. Some nas telas em que o cliente já está agendando, entrando ou no painel (A6).
import Link from "next/link";
import { usePathname } from "next/navigation";

const SEM_BARRA = [/^\/agendar(\/|$)/, /^\/entrar(\/|$)/, /^\/cadastro(\/|$)/, /^\/painel(\/|$)/];

export const mostraBarraDeAgendar = (caminho: string) => !SEM_BARRA.some((r) => r.test(caminho));

export function BarraAgendar() {
  const caminho = usePathname();
  if (!mostraBarraDeAgendar(caminho)) return null;
  return (
    <div className="barra-agendar">
      <Link href="/agendar" className="botao botao-principal">
        Agendar horário
      </Link>
    </div>
  );
}
