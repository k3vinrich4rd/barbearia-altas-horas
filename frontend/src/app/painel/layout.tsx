import type { ReactNode } from "react";
import { ExigePapel } from "@/telas/AcessoNegado";

export default function PainelLayout({ children }: { children: ReactNode }) {
  return <ExigePapel papeis={["ADMIN", "BARBEIRO"]}>{children}</ExigePapel>;
}
