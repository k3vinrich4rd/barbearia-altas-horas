// Âncoras da home (Fatia 16: F4 A28, F5 A27 e a revisão do #154). Qualquer link para uma seção da home, pelo menu ou
// por um link antigo, rola até a seção e leva o foco ao título dela (h2 ou h3 com tabindex="-1"), com ou sem troca de
// rota. Sem animação (sistema ou painel), a rolagem é imediata. Nada de style=: só scrollIntoView e focus.

/** Links antigos já divulgados (/#historia, /#barbeiro) e do menu de antes (/#trajetoria) levam a "Quem somos". */
export const HASHES_DE_QUEM_SOMOS = ["#historia", "#trajetoria", "#barbeiro"];

/** Rotas antigas que hoje são uma seção da home (A27: /servicos virou "02 · O que fazemos"). */
const ROTAS_QUE_VIRARAM_SECAO: Record<string, string> = { "/servicos": "#servicos" };

/** Seções e blocos da home que têm título próprio para receber o foco. */
export const ANCORAS_DA_HOME = ["#inicio", "#quem-somos", "#servicos", "#como-marcar", "#duvidas", "#onde-estamos"];

/** Troca o hash antigo pelo atual; devolve null se o hash não é de uma seção da home. */
export function normalizarHash(hash: string): string | null {
  if (HASHES_DE_QUEM_SOMOS.includes(hash)) return "#quem-somos";
  return ANCORAS_DA_HOME.includes(hash) ? hash : null;
}

/** Para um destino de link: o hash da seção da home que ele representa, ou null se não é uma seção da home. */
export function ancoraDoDestino(destino: URL): string | null {
  const daRota = ROTAS_QUE_VIRARAM_SECAO[destino.pathname];
  if (daRota) return daRota;
  if (destino.pathname !== "/" || !destino.hash) return null;
  return normalizarHash(destino.hash);
}

/** Sem animação pelo sistema ou pelo botão "Sem animação" do painel de acessibilidade. */
export function semAnimacao() {
  return (
    document.documentElement.dataset.movimento === "reduzido" ||
    window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true
  );
}

/** O título da seção: o elemento do aria-labelledby, ou o primeiro h2/h3 dentro dela. */
export function tituloDaSecao(secao: HTMLElement): HTMLElement | null {
  const rotulo = secao.getAttribute("aria-labelledby");
  const peloRotulo = rotulo ? document.getElementById(rotulo) : null;
  return peloRotulo ?? secao.querySelector<HTMLElement>("h2, h3");
}

/** Rola até a seção do hash (suave, ou de uma vez sem animação) e, se pedido, leva o foco ao título dela.
 * Devolve false se a seção ainda não está na página. */
export function irParaAncora(hash: string, { focar }: { focar: boolean }): boolean {
  const secao = document.getElementById(hash.slice(1));
  if (!secao) return false;
  secao.scrollIntoView({ behavior: semAnimacao() ? "auto" : "smooth", block: "start" });
  if (focar) {
    const titulo = tituloDaSecao(secao);
    if (titulo) {
      if (!titulo.hasAttribute("tabindex")) titulo.tabIndex = -1;
      titulo.focus({ preventScroll: true });
    }
  }
  return true;
}

/** Âncora pedida por um clique em outra rota, para a home tratar quando montar (e o FocoNoTitulo não mandar o foco ao h1). */
let pendente: string | null = null;
export const marcarAncoraPendente = (hash: string | null) => {
  pendente = hash;
};
export const ancoraPendente = () => pendente;
