// Seções interativas da home que contam a história da barbearia (Main.dc.html): linha do tempo, "Ouvir a história",
// galeria e dúvidas. Tudo por teclado e com as mudanças anunciadas (aria-live), como pede o design system.
import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { Duvidas, Galeria, Historia } from "@/componentes/SecoesDaHome";
import { DUVIDAS, GALERIA, MARCOS } from "@/conteudo/historia";
import { renderizar } from "@/testes/render";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("home: história, linha do tempo, galeria e dúvidas", () => {
  test("a linha do tempo abre em 1997, troca de ano por clique ou teclado e anuncia o detalhe", async () => {
    const { user } = renderizar(<Historia />);

    const grupo = screen.getByRole("group", { name: "Escolha um ano da linha do tempo" });
    const anos = within(grupo).getAllByRole("button");
    expect(anos.map((b) => b.textContent)).toEqual(MARCOS.map((m) => `${m.ano}${m.titulo}`));
    expect(within(grupo).getByRole("button", { pressed: true })).toHaveTextContent("1997");

    const detalhe = document.getElementById("marco-detalhe")!;
    expect(detalhe).toHaveAttribute("aria-live", "polite");
    expect(detalhe).toHaveTextContent("1997 · Abre a Altas Horas.");

    await user.click(within(grupo).getByRole("button", { name: /2017/ }));
    expect(within(grupo).getByRole("button", { pressed: true })).toHaveTextContent("2017");
    expect(detalhe).toHaveTextContent(`2017 · ${MARCOS[2].titulo}.`);

    within(grupo).getByRole("button", { name: /1995/ }).focus();
    await user.keyboard("{Enter}");
    expect(detalhe).toHaveTextContent(`1995 · ${MARCOS[0].titulo}.`);
  });

  test("'Ouvir a história' lê em pt-BR, vira 'Parar leitura' e avisa o estado numa região de status", async () => {
    const falar = vi.fn();
    const cancelar = vi.fn();
    vi.stubGlobal("speechSynthesis", { speak: falar, cancel: cancelar });
    vi.stubGlobal(
      "SpeechSynthesisUtterance",
      class {
        lang = "";
        onend: (() => void) | null = null;
        constructor(public text: string) {}
      },
    );
    const { user } = renderizar(<Historia />);

    await user.click(screen.getByRole("button", { name: /Ouvir a história/ }));
    expect(falar).toHaveBeenCalledTimes(1);
    const fala = falar.mock.calls[0][0] as { lang: string; text: string };
    expect(fala.lang).toBe("pt-BR");
    expect(fala.text).toContain("1997");
    const botao = screen.getByRole("button", { name: /Parar leitura/ });
    expect(botao).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("status")).toHaveTextContent("Lendo a história em voz alta.");

    await user.click(botao);
    expect(cancelar).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Ouvir a história/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("status")).toHaveTextContent("Leitura parada.");
  });

  test("sem leitura em voz alta no navegador, o botão avisa em vez de quebrar", async () => {
    vi.stubGlobal("speechSynthesis", undefined);
    const { user } = renderizar(<Historia />);
    await user.click(screen.getByRole("button", { name: /Ouvir a história/ }));
    expect(screen.getByRole("status")).toHaveTextContent("Seu navegador não tem leitura em voz alta.");
  });

  test("a galeria anda para frente e para trás em volta, marca a miniatura e anuncia 'Foto N de M'", async () => {
    const { user } = renderizar(<Galeria />);
    const total = GALERIA.length;
    const legenda = () => screen.getByText(/^Foto \d+ de \d+$/);

    expect(legenda()).toHaveTextContent(`Foto 1 de ${total}`);
    expect(legenda().closest("figcaption")).toHaveAttribute("aria-live", "polite");

    await user.click(screen.getByRole("button", { name: "Foto anterior" }));
    expect(legenda()).toHaveTextContent(`Foto ${total} de ${total}`);
    await user.click(screen.getByRole("button", { name: "Próxima foto" }));
    expect(legenda()).toHaveTextContent(`Foto 1 de ${total}`);

    const miniaturas = within(screen.getByRole("group", { name: "Miniaturas da galeria" })).getAllByRole("button");
    expect(miniaturas).toHaveLength(total);
    await user.click(miniaturas[total - 1]);
    expect(miniaturas[total - 1]).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText(GALERIA[total - 1].legenda)).toBeInTheDocument();
    for (const f of GALERIA) expect(f.alt.length).toBeGreaterThan(10);
  });

  test("as dúvidas são <details> nativos (abrem sem JavaScript), uma pergunta por item", async () => {
    const { user, container } = renderizar(<Duvidas />);
    const itens = container.querySelectorAll("details");
    expect(itens).toHaveLength(DUVIDAS.length);
    const primeiro = itens[0];
    expect(primeiro.open).toBe(false);
    // O teclado (Enter no <summary>) é do navegador: o E2E home-historia.spec.ts confere no Chromium.
    await user.click(within(primeiro as HTMLElement).getByText(DUVIDAS[0].pergunta));
    expect(primeiro.open).toBe(true);
    expect(primeiro).toHaveTextContent(DUVIDAS[0].resposta);
  });
});
