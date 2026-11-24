import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { ErroApi } from "@/api/erro";
import { Aviso, AvisoDeErro } from "./Aviso";
import { ContatoWhatsapp } from "./ContatoWhatsapp";
import { Cronometro } from "./Cronometro";
import { EtiquetaDeStatus } from "./EtiquetaDeStatus";
import { Icone } from "./Icone";
import { PainelDeAcessibilidade } from "./PainelDeAcessibilidade";

// Verificações dos critérios visuais V# da 7A (desenhados pelo Kevin no canvas): cada peça do design system tem forma,
// palavra e papel de acessibilidade próprios. Não entram no conferir-spec.sh (só os A# entram).

describe("V4 · mensagens: forma, ícone e palavra, nunca só cor", () => {
  test("V4: erro vira role=alert com prefixo 'Erro:', aviso e sucesso viram role=status, e o ícone é decorativo", () => {
    const { container } = render(
      <>
        <Aviso tipo="erro">Muitas tentativas.</Aviso>
        <Aviso tipo="aviso">Preencha à mão.</Aviso>
        <Aviso tipo="sucesso">Horário confirmado.</Aviso>
      </>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Erro: Muitas tentativas.");
    expect(screen.getAllByRole("status")).toHaveLength(2);
    expect(container.querySelector(".aviso-erro")).toBeInTheDocument();
    expect(container.querySelector(".aviso-aviso")).toBeInTheDocument();
    expect(container.querySelector(".aviso-sucesso")).toBeInTheDocument();
    for (const svg of container.querySelectorAll("svg")) expect(svg).toHaveAttribute("aria-hidden", "true");
    render(
      <AvisoDeErro
        erro={new ErroApi({ status: 429, code: "LIMITE_EXCEDIDO", retryAfter: 12, traceId: "t-1" })}
        focar={false}
      />,
    );
    expect(screen.getByText(/Tente de novo em 12 segundos/)).toBeInTheDocument();
  });
});

describe("V6 · status da reserva por forma e por palavra", () => {
  test("V6: cada status tem uma etiqueta com o nome escrito e um estado visual próprio", () => {
    const { container } = render(
      <ul>
        {["AGUARDANDO_PAGAMENTO", "CONFIRMADO", "CONCLUIDO", "NAO_COMPARECEU", "CANCELADO", "EXPIRADO"].map((s) => (
          <li key={s}>
            <EtiquetaDeStatus status={s} />
          </li>
        ))}
      </ul>,
    );
    const textos = [...container.querySelectorAll(".etiqueta")].map((e) => e.textContent);
    expect(textos).toEqual([
      "Aguardando pagamento",
      "Confirmado",
      "Concluído",
      "Não compareceu",
      "Cancelado",
      "Expirado",
    ]);
    const formas = [...container.querySelectorAll(".etiqueta")].map((e) => e.getAttribute("data-status"));
    expect(new Set(formas).size).toBe(6); // seis estados, seis formas (CSS por data-status)
    expect(container.querySelectorAll(".etiqueta svg").length).toBe(4); // visto, visto, ✕ e relógio
  });
});

// um segundo por vez: assim o React roda os efeitos entre os ticks, como no navegador
async function passar(segundos: number) {
  for (let i = 0; i < segundos; i++)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
}

describe("V3 · contador do pagamento", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());

  test("V3: o tempo vem do expiraEm, o último minuto muda a forma da barra e ganha texto, e o leitor de tela ouve só nos marcos", async () => {
    vi.setSystemTime(new Date("2026-11-18T08:00:00-03:00"));
    const aoZerar = vi.fn();
    const { container } = render(<Cronometro expiraEm="2026-11-18T08:03:00-03:00" aoZerar={aoZerar} />);
    expect(screen.getByRole("timer", { name: "Tempo para pagar" })).toHaveTextContent("03:00");
    expect(container.querySelector("[aria-live=polite]")).toHaveTextContent("Faltam 3 minutos para pagar.");
    expect(screen.queryByText("Menos de 1 minuto para pagar.")).toBeNull();

    // 20 s depois: o relógio andou, mas o texto falado não mudou (sem anúncio por segundo)
    await passar(20);
    expect(screen.getByRole("timer")).toHaveTextContent("02:40");
    expect(container.querySelector("[aria-live=polite]")).toHaveTextContent("Faltam 3 minutos para pagar.");

    // marco de minuto cheio
    await passar(40);
    expect(container.querySelector("[aria-live=polite]")).toHaveTextContent("Faltam 2 minutos para pagar.");

    // último minuto: barra listrada e texto
    await passar(70);
    expect(screen.getByText("Menos de 1 minuto para pagar.")).toBeInTheDocument();
    expect(container.querySelector(".cronometro-barra")).toHaveAttribute("data-ultimo", "true");

    // 30 s e fim
    await passar(30);
    expect(container.querySelector("[aria-live=polite]")).toHaveTextContent("Faltam 30 segundos para pagar.");
    await passar(31);
    expect(screen.getByRole("timer")).toHaveTextContent("00:00");
    expect(aoZerar).toHaveBeenCalledTimes(1);
  });
});

describe("V12 · ícones", () => {
  test("V12: ícones são decorativos (aria-hidden) e sempre acompanham uma palavra", () => {
    const { container } = render(
      <button type="button">
        <Icone nome="lixeira" /> Excluir minha conta
      </button>,
    );
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByRole("button", { name: "Excluir minha conta" })).toBeInTheDocument();
  });
});

describe("V1 · WhatsApp e acessibilidade da home", () => {
  test("V1: o link do WhatsApp vem do número público da API e some sem número; o painel de acessibilidade usa aria-pressed", async () => {
    const { rerender, container } = render(
      <ContatoWhatsapp barbeiro={{ nomeExibicao: "Carlos Eduardo", whatsapp: "5511900000000" }} />,
    );
    const link = screen.getByRole("link", { name: /Falar com Carlos no WhatsApp \(abre o WhatsApp\)/ });
    expect(link).toHaveAttribute("href", "https://wa.me/5511900000000");
    rerender(<ContatoWhatsapp barbeiro={{ nomeExibicao: "Carlos Eduardo", whatsapp: null }} />);
    expect(container).toBeEmptyDOMElement();

    const user = userEvent.setup();
    render(<PainelDeAcessibilidade />);
    const contraste = screen.getByRole("button", { name: "Alto contraste" });
    expect(contraste).toHaveAttribute("aria-pressed", "false");
    await user.click(contraste);
    expect(contraste).toHaveAttribute("aria-pressed", "true");
    expect(document.documentElement.dataset.contraste).toBe("alto");
    await user.click(screen.getByRole("button", { name: "Tema claro" }));
    expect(document.documentElement.dataset.theme).toBe("claro");
    await user.click(screen.getByRole("button", { name: "Aumentar o tamanho do texto" }));
    expect(document.body.style.zoom).toBe("1.1");
  });
});
