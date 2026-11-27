"use client";
// Menu do painel (A44): cada item só aparece para quem tem o papel. A autorização de verdade continua na API (403).
import Link from "next/link";
import { usePathname } from "next/navigation";
import { SeloDeNovidades } from "./Novidades";
import { useSessao } from "./Sessao";

export const ITENS_DO_PAINEL: { href: string; texto: string; papeis: string[] }[] = [
  { href: "/painel", texto: "Agenda", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/reserva-manual", texto: "Reserva manual", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/pendencias", texto: "Pendências", papeis: ["ADMIN"] },
  { href: "/painel/cadastros", texto: "Cadastros", papeis: ["ADMIN"] },
  { href: "/painel/folgas", texto: "Férias e folgas", papeis: ["ADMIN"] },
  { href: "/painel/auditoria", texto: "Auditoria", papeis: ["ADMIN"] },
  { href: "/painel/alcance", texto: "Alcance", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/novidades", texto: "Novidades", papeis: ["ADMIN", "BARBEIRO"] },
  { href: "/painel/notificacoes", texto: "Notificações", papeis: ["ADMIN", "BARBEIRO"] },
];

/** O papel ADMIN aparece como "Dono/Admin" (ADR 0013); só texto, o papel no contrato continua ADMIN. */
export function rotuloDoPapel(papeis: string[] | undefined): string | undefined {
  if (papeis?.includes("ADMIN")) return "Dono/Admin";
  if (papeis?.includes("BARBEIRO")) return "Barbeiro";
  return undefined;
}

export function NavegacaoDoPainel() {
  const sessao = useSessao();
  const caminho = usePathname();
  const itens = ITENS_DO_PAINEL.filter((i) => sessao.papeis?.some((p) => i.papeis.includes(p)));
  return (
    <nav aria-label="Painel" className="painel-menu">
      {rotuloDoPapel(sessao.papeis) && <p className="painel-papel">Você entrou como {rotuloDoPapel(sessao.papeis)}</p>}
      <ul>
        {itens.map((i) => (
          <li key={i.href}>
            <Link href={i.href} aria-current={caminho === i.href ? "page" : undefined}>
              {i.texto}
              {i.href === "/painel/novidades" && <SeloDeNovidades />}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
