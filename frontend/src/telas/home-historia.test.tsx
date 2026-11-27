// Seções interativas da home que contam a trajetória da barbearia (Main.dc.html): linha do tempo, "Ouvir a trajetória",
// prêmios e dúvidas (a galeria tem o galeria.test.tsx). Tudo por teclado e com as mudanças anunciadas (aria-live), como pede o design system.
import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { Duvidas, Trajetoria } from "@/componentes/SecoesDaHome";
import { DUVIDAS, MARCOS } from "@/conteudo/historia";
import { renderizar } from "@/testes/render";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("home: trajetória, linha do tempo e dúvidas", () => {
  test('A3: a Trajetória tem id="trajetoria" ligado ao <h2> por aria-labelledby, não existe id="historia" e os prêmios estão no mesmo bloco', () => {
    const { container } = renderizar(<Trajetoria />);
    const secao = container.querySelector("#trajetoria")!;
    expect(secao).not.toBeNull();
    const titulo = within(secao as HTMLElement).getByRole("heading", { level: 2 });
    expect(secao.getAttribute("aria-labelledby")).toBe(titulo.id);
    expect(titulo).toHaveTextContent("Quase três décadas na mesma cadeira");
    expect(container.querySelector("#historia")).toBeNull();
    expect(container.querySelector("#premios")).toBeNull();
    expect(within(secao as HTMLElement).getByRole("heading", { name: "Linha do tempo" })).toBeInTheDocument();
    expect(within(secao as HTMLElement).getByText("Prêmio Barbeiros do Brasil")).toBeInTheDocument();
    expect(within(secao as HTMLElement).getByText("Prêmio Master Barber · Êxito Profissional")).toBeInTheDocument();
  });

  test("a linha do tempo abre em 1997, troca de ano por clique ou teclado e anuncia o detalhe", async () => {
    const { user } = renderizar(<Trajetoria />);

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

    within(grupo).getByRole("button", { name: /1994/ }).focus();
    await user.keyboard("{Enter}");
    expect(detalhe).toHaveTextContent(`1994 · ${MARCOS[0].titulo}.`);
  });

  test("VZ-1 e VZ-2: o áudio tem preload none, o botão toca o arquivo do site e vira Pausar", async () => {
    const play = vi.fn().mockResolvedValue(undefined);
    const pause = vi.fn();
    vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(play);
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(pause);
    const { user, container } = renderizar(<Trajetoria />);
    const audio = container.querySelector("audio")!;
    expect(audio).toHaveAttribute("preload", "none");
    expect(audio.querySelector("source")).toBeNull();
    expect(audio.querySelector("track")).toBeNull();

    await user.click(screen.getByRole("button", { name: /Ouvir a trajetória/ }));
    expect(play).toHaveBeenCalledTimes(1);
    expect(audio.querySelector('source[type="audio/opus"]')).toHaveAttribute("src", "/assets/audio/historia.opus");
    expect(audio.querySelector('source[type="audio/mpeg"]')).toHaveAttribute("src", "/assets/audio/historia.mp3");
    expect(audio.querySelector("track")).toHaveAttribute("src", "/assets/audio/historia.vtt");
    expect(audio.querySelector("track")).toHaveAttribute("kind", "captions");
    const botao = await screen.findByRole("button", { name: "Pausar" });
    expect(botao).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("status")).toHaveTextContent("Lendo a trajetória em voz alta.");

    await user.click(botao);
    expect(pause).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Ouvir a trajetória/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("status")).toHaveTextContent("Leitura parada.");
  });

  test("VZ-5: se o arquivo não carrega, avisa e usa a voz do aparelho", async () => {
    vi.spyOn(HTMLMediaElement.prototype, "play").mockRejectedValue(new Error("falha"));
    const falar = vi.fn();
    vi.stubGlobal("speechSynthesis", { speak: falar, cancel: vi.fn() });
    vi.stubGlobal(
      "SpeechSynthesisUtterance",
      class {
        lang = "";
        onend: (() => void) | null = null;
        constructor(public text: string) {}
      },
    );
    const { user } = renderizar(<Trajetoria />);
    await user.click(screen.getByRole("button", { name: /Ouvir a trajetória/ }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Não deu para carregar o áudio. Ler com a voz do aparelho?",
    );
    expect(falar).toHaveBeenCalledTimes(1);
    const fala = falar.mock.calls[0][0] as { lang: string; text: string };
    expect(fala.lang).toBe("pt-BR");
    expect(fala.text).toContain("1997");
    expect(screen.getByRole("button", { name: "Pausar" })).toHaveAttribute("aria-pressed", "true");
  });

  test("as dúvidas são <details> nativos (abrem sem JavaScript), uma pergunta por item", async () => {
    const { user, container } = renderizar(<Duvidas />);
    const itens = container.querySelectorAll("details");
    expect(itens).toHaveLength(DUVIDAS.length);
    const primeiro = itens[0];
    expect(primeiro.open).toBe(false);
    // O teclado (Enter no <summary>) é do navegador: o E2E home-trajetoria.spec.ts confere no Chromium.
    await user.click(within(primeiro as HTMLElement).getByText(DUVIDAS[0].pergunta));
    expect(primeiro.open).toBe(true);
    expect(primeiro).toHaveTextContent(DUVIDAS[0].resposta);
  });
});
