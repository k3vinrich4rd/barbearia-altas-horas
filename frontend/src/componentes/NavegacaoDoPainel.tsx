"use client";
// Menu do painel (A44): cada item só aparece para quem tem o papel. A autorização de verdade continua na API (403).
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSessao } from "./Sessao";

export const ITENS_DO_PAINEL: { href: string; texto: string; papeis: string[] }[] = [
  { href: "/painel", texto: "Agenda", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/reserva-manual", texto: "Reserva manual", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/pendencias", texto: "Pendências", papeis: ["ADMIN"] },
  { href: "/painel/cadastros", texto: "Cadastros", papeis: ["ADMIN"] },
  { href: "/painel/auditoria", texto: "Auditoria", papeis: ["ADMIN"] },
  { href: "/painel/alcance", texto: "Alcance", papeis: ["ADMIN", "BARBEIRO"] },
];

export function NavegacaoDoPainel() {
  const sessao = useSessao();
  const caminho = usePathname();
  const itens = ITENS_DO_PAINEL.filter((i) => sessao.papeis?.some((p) => i.papeis.includes(p)));
  return (
    <nav aria-label="Painel" className="painel-menu">
      <ul>
        {itens.map((i) => (
          <li key={i.href}>
            <Link href={i.href} aria-current={caminho === i.href ? "page" : undefined}>
              {i.texto}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
