"use client";
// Navegação para as seções da home (Fatia 16; revisão do #154). O Link do Next troca o hash por pushState (sem
// hashchange) e, vindo de outra rota, o FocoNoTitulo levava o foco ao h1 do herói: a URL ficava certa e a seção, fora da
// vista. Aqui, na fase de captura (antes do Link), todo clique num link para uma seção da home:
//  - na própria home: troca o endereço, rola até a seção e leva o foco ao título dela;
//  - em outra rota: navega para /#secao e, quando a home monta, faz o mesmo.
// O onClick do link (fechar o menu, por exemplo) continua rodando. Ctrl/⌘-clique, nova aba e outras páginas ficam com o navegador.
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { ancoraDoDestino, ancoraPendente, irParaAncora, marcarAncoraPendente, normalizarHash } from "./ancoras";

/** Espera a seção aparecer (a home pode montar aos poucos) por até 3 s e então vai até ela. */
function irQuandoExistir(hash: string, focar: boolean) {
  if (irParaAncora(hash, { focar })) return () => undefined;
  const observador = new MutationObserver(() => {
    if (irParaAncora(hash, { focar })) observador.disconnect();
  });
  observador.observe(document.body, { childList: true, subtree: true });
  const fim = setTimeout(() => observador.disconnect(), 3000);
  return () => {
    observador.disconnect();
    clearTimeout(fim);
  };
}

export function NavegacaoPorAncora() {
  const caminho = usePathname();
  const roteador = useRouter();

  // Chegou à home por um clique em outra rota: vai até a seção pedida, com foco no título.
  useEffect(() => {
    const hash = ancoraPendente();
    if (!hash || caminho !== "/") return;
    marcarAncoraPendente(null);
    let parar = () => undefined as void;
    // depois do quadro em que a página nova foi pintada (e do onClick do link, que fecha o menu do celular)
    const quadro = requestAnimationFrame(() => {
      parar = irQuandoExistir(hash, true);
    });
    return () => {
      cancelAnimationFrame(quadro);
      parar();
    };
  }, [caminho]);

  useEffect(() => {
    // 1) abriu a página com um hash antigo: troca pelo atual e rola, sem mexer no foco (o navegador cuida da carga)
    function peloEndereco(focar: boolean) {
      if (window.location.pathname !== "/") return;
      const hash = normalizarHash(window.location.hash);
      if (!hash) return;
      if (hash !== window.location.hash) window.history.replaceState(window.history.state, "", hash);
      else if (!focar) return; // hash atual na primeira carga: o navegador já rolou até ele
      irParaAncora(hash, { focar });
    }
    // 2) clique num link para uma seção da home
    function noClique(e: MouseEvent) {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const link = (e.target as Element | null)?.closest?.("a[href]");
      if (
        !(link instanceof HTMLAnchorElement) ||
        (link.target && link.target !== "_self") ||
        link.hasAttribute("download")
      )
        return;
      const destino = new URL(link.href, window.location.href);
      if (destino.origin !== window.location.origin) return;
      const hash = ancoraDoDestino(destino);
      if (!hash) return;
      e.preventDefault();
      if (window.location.pathname === "/") {
        if (window.location.hash !== hash) window.history.pushState(window.history.state, "", hash);
        // depois do onClick do link (o menu do celular fecha e o cabeçalho muda de altura), para rolar até o lugar certo
        requestAnimationFrame(() => irParaAncora(hash, { focar: true }));
        return;
      }
      marcarAncoraPendente(hash);
      roteador.push(`/${hash}`, { scroll: false });
    }
    const aoMudarHash = () => peloEndereco(true);
    peloEndereco(false);
    window.addEventListener("hashchange", aoMudarHash);
    document.addEventListener("click", noClique, true);
    return () => {
      window.removeEventListener("hashchange", aoMudarHash);
      document.removeEventListener("click", noClique, true);
    };
  }, [roteador]);

  return null;
}
