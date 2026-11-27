import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { renderizar } from "@/testes/render";

// Fatia 16 (A28), ressalva do F4: o Link do Next troca o hash por pushState, sem hashchange. O clique no mesmo
// documento para /#barbeiro, /#trajetoria ou /#historia também precisa levar a "Quem somos".
let rolar: ReturnType<typeof vi.fn>;
let reduzido = false;

beforeEach(() => {
  rolar = vi.fn();
  Element.prototype.scrollIntoView = rolar as unknown as Element["scrollIntoView"];
  vi.stubGlobal("matchMedia", (q: string) => ({ matches: reduzido && q.includes("reduce"), media: q, addEventListener() {}, removeEventListener() {} }));
  vi.stubGlobal("requestAnimationFrame", (f: FrameRequestCallback) => {
    f(0);
    return 0;
  });
  window.history.replaceState(null, "", "/");
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
  test.each(["/#barbeiro", "/#trajetoria", "/#historia"])("%s: vai para #quem-somos, rola suave e leva o foco ao título", (href) => {
    renderizar(<Trajetoria />);
    const a = linkPara(href);
    act(() => {
      fireEvent.click(a);
    });
    expect(window.location.hash).toBe("#quem-somos");
    expect(rolar).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
    expect(screen.getByRole("heading", { level: 2, name: "Quase três décadas na mesma cadeira" })).toHaveFocus();
    a.remove();
  });

  test("com prefers-reduced-motion ou o Sem animação do painel, a rolagem é imediata", () => {
    renderizar(<Trajetoria />);
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

  test("links para outras âncoras, outras páginas ou com Ctrl ficam com o navegador", () => {
    renderizar(<Trajetoria />);
    const servicos = linkPara("/#servicos");
    act(() => {
      fireEvent.click(servicos);
    });
    expect(window.location.hash).toBe("#servicos");
    window.history.replaceState(null, "", "/");
    const comCtrl = linkPara("/#barbeiro");
    const evento = new MouseEvent("click", { bubbles: true, cancelable: true, ctrlKey: true });
    comCtrl.dispatchEvent(evento);
    expect(rolar).not.toHaveBeenCalled();
    servicos.remove();
    comCtrl.remove();
  });
});
