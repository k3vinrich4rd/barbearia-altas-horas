"use client";
// Barra fixa "Agendar horário" (Fatia 12, E8 e D-7): só no celular (abaixo de 768 px, pelo CSS), com largura total e a
// área segura do aparelho. Some nas telas de tarefa, no painel e em /reserva.
// Fatia 16, F2 (A1 e A2): na home ela fica escondida e `inert` enquanto o herói ou "Como marcar" estão na tela, que já têm
// o botão de agendar; assim nunca aparecem dois "Agendar horário" juntos. Mostrar e esconder é por atributo e classe (CSP:
// nada de style=), com transição de transform e opacity pelos tokens --mov-*, zerada pelo "Sem animação".
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ehPaginaDeTarefa } from "./tipoDePagina";

const SEM_BARRA = [/^\/painel(\/|$)/, /^\/reserva(\/|$)/];

/** Seções da home que já têm o botão de agendar: enquanto uma delas aparece, a barra some. */
export const SECOES_QUE_ESCONDEM_A_BARRA = ["inicio", "como-marcar"];

/** Faixa da tela que conta para esconder a barra: o meio, sem 20% em cima e 20% embaixo. */
export const MARGEM_DO_OBSERVADOR = "-20% 0px -20% 0px";

export const mostraBarraDeAgendar = (caminho: string) =>
  !ehPaginaDeTarefa(caminho) && !SEM_BARRA.some((r) => r.test(caminho));

function useSecaoComAgendarNaTela(ativo: boolean) {
  // Na home a barra começa escondida (o herói está na tela no primeiro quadro); sem IntersectionObserver, ela aparece.
  const [naTela, setNaTela] = useState(ativo);
  useEffect(() => {
    if (!ativo) return;
    const alvos = SECOES_QUE_ESCONDEM_A_BARRA.map((id) => document.getElementById(id)).filter(
      (e): e is HTMLElement => e !== null,
    );
    if (alvos.length === 0 || typeof IntersectionObserver === "undefined") {
      queueMicrotask(() => setNaTela(false));
      return;
    }
    const visiveis = new Map<Element, boolean>();
    // Só conta a faixa do meio da tela (20% de margem em cima e embaixo): uma sobra de "Como marcar" no alto da tela,
    // com "Onde estamos" ocupando o resto, não esconde a barra (sem a galeria, o rodapé vem logo depois de "Como marcar").
    const observador = new IntersectionObserver(
      (entradas) => {
        for (const e of entradas) visiveis.set(e.target, e.isIntersecting);
        setNaTela([...visiveis.values()].some(Boolean));
      },
      { rootMargin: MARGEM_DO_OBSERVADOR },
    );
    alvos.forEach((a) => observador.observe(a));
    return () => observador.disconnect();
  }, [ativo]);
  return ativo && naTela;
}

export function BarraAgendar() {
  const caminho = usePathname();
  const escondida = useSecaoComAgendarNaTela(caminho === "/");
  if (!mostraBarraDeAgendar(caminho)) return null;
  return (
    <aside className="barra-agendar" aria-label="Agendar horário" data-escondida={escondida} inert={escondida}>
      <Link href="/agendar" className="botao botao-principal">
        Agendar horário
      </Link>
    </aside>
  );
}
