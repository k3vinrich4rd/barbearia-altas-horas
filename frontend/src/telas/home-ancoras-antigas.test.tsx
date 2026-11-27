import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { ancoraPendente, marcarAncoraPendente } from "@/componentes/ancoras";
import { NavegacaoPorAncora } from "@/componentes/NavegacaoPorAncora";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { definirCaminho, roteador, zerarNavegacao } from "@/testes/navegacao";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);

/** A home com o que importa aqui: a navegação por âncora do layout, Quem somos e uma seção de exemplo. */
function Home() {
  return (
    <>
      <NavegacaoPorAncora />
      <Trajetoria />
      <section id="servicos" aria-labelledby="servicos-titulo">
        <h2 id="servicos-titulo" tabIndex={-1}>
          Um barbeiro, todos os serviços
        </h2>
      </section>
      <section id="onde-estamos" aria-labelledby="rodape-onde">
        <h2 id="rodape-onde" tabIndex={-1}>
          Onde estamos
        </h2>
      </section>
    </>
  );
}

// Fatia 16 (A28), ressalva do F4: o Link do Next troca o hash por pushState, sem hashchange. O clique no mesmo
// documento para /#barbeiro, /#trajetoria ou /#historia também precisa levar a "Quem somos".
let rolar: ReturnType<typeof vi.fn>;
let reduzido = false;

beforeEach(() => {
  rolar = vi.fn();
  Element.prototype.scrollIntoView = rolar as unknown as Element["scrollIntoView"];
  vi.stubGlobal("matchMedia", (q: string) => ({
    matches: reduzido && q.includes("reduce"),
    media: q,
    addEventListener() {},
    removeEventListener() {},
  }));
  vi.stubGlobal("requestAnimationFrame", (f: FrameRequestCallback) => {
    f(0);
    return 0;
  });
  window.history.replaceState(null, "", "/");
  zerarNavegacao();
  marcarAncoraPendente(null);
});

afterEach(() => {
  vi.unstubAllGlobals();
  delete document.documentElement.dataset.movimento;
  reduzido = false;
});

function linkPara(href: string) {
  const a = document.createElement("a");
  a.href = href;
  a.textContent = `ir ${href}`;
  // como o Link do Next: o próprio onClick troca o hash sem disparar hashchange, se ninguém impediu antes
  a.addEventListener("click", (e) => {
    if (e.defaultPrevented) return;
    e.preventDefault();
    window.history.pushState(null, "", href);
  });
  document.body.append(a);
  return a;
}

describe("âncoras antigas no clique do mesmo documento", () => {
  test.each(["/#barbeiro", "/#trajetoria", "/#historia"])(
    "%s: vai para #quem-somos, rola suave e leva o foco ao título",
    (href) => {
      renderizar(<Home />);
      const a = linkPara(href);
      act(() => {
        fireEvent.click(a);
      });
      expect(window.location.hash).toBe("#quem-somos");
      expect(rolar).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
      expect(screen.getByRole("heading", { level: 2, name: "Quase três décadas na mesma cadeira" })).toHaveFocus();
      a.remove();
    },
  );

  test("com prefers-reduced-motion ou o Sem animação do painel, a rolagem é imediata", () => {
    renderizar(<Home />);
    reduzido = true;
    const a = linkPara("/#barbeiro");
    act(() => {
      fireEvent.click(a);
    });
    expect(rolar).toHaveBeenLastCalledWith({ behavior: "auto", block: "start" });

    reduzido = false;
    document.documentElement.dataset.movimento = "reduzido";
    window.history.replaceState(null, "", "/");
    act(() => {
      fireEvent.click(a);
    });
    expect(rolar).toHaveBeenLastCalledWith({ behavior: "auto", block: "start" });
    a.remove();
  });

  test.each([
    ["/#servicos", "Um barbeiro, todos os serviços"],
    ["/servicos", "Um barbeiro, todos os serviços"],
    ["/#onde-estamos", "Onde estamos"],
  ])("%s na home: vai para a seção, rola e leva o foco ao h2 dela", (href, titulo) => {
    renderizar(<Home />);
    const a = linkPara(href);
    act(() => {
      fireEvent.click(a);
    });
    expect(window.location.pathname).toBe("/");
    expect(window.location.hash).toBe(href === "/servicos" ? "#servicos" : href.slice(1));
    expect(rolar).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
    expect(screen.getByRole("heading", { level: 2, name: titulo })).toHaveFocus();
    a.remove();
  });

  test("em outra rota, o clique navega para /#secao sem rolar para o topo e deixa a âncora para a home", () => {
    definirCaminho("/privacidade");
    window.history.replaceState(null, "", "/privacidade");
    renderizar(<NavegacaoPorAncora />);
    const a = linkPara("/#servicos");
    act(() => {
      fireEvent.click(a);
    });
    expect(roteador.push).toHaveBeenCalledWith("/#servicos", { scroll: false });
    expect(ancoraPendente()).toBe("#servicos");
    a.remove();
  });

  test("links para outras páginas ou com Ctrl ficam com o navegador", () => {
    renderizar(<Home />);
    const outra = linkPara("/privacidade");
    act(() => {
      fireEvent.click(outra);
    });
    expect(window.location.pathname).toBe("/privacidade");
    window.history.replaceState(null, "", "/");
    const comCtrl = linkPara("/#barbeiro");
    const evento = new MouseEvent("click", { bubbles: true, cancelable: true, ctrlKey: true });
    comCtrl.dispatchEvent(evento);
    expect(rolar).not.toHaveBeenCalled();
    expect(roteador.push).not.toHaveBeenCalled();
    outra.remove();
    comCtrl.remove();
  });
});
