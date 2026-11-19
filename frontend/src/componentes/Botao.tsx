import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variante = "principal" | "secundario";

export function Botao({
  variante = "secundario",
  children,
  ...resto
}: { variante?: Variante; children: ReactNode } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" className={`botao botao-${variante}`} {...resto}>
      {children}
    </button>
  );
}

/** Navegação com cara de botão (continua sendo link). */
export function LinkBotao({
  href,
  children,
  variante = "secundario",
}: {
  href: string;
  children: ReactNode;
  variante?: Variante;
}) {
  return (
    <Link href={href} className={`botao botao-${variante}`}>
      {children}
    </Link>
  );
}
