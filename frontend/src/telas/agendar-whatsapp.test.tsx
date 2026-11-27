import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { linkDoWhatsapp, preposicaoDoDia } from "@/componentes/ContatoWhatsapp";
import { BARBEIRO, CINCO_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { definirBusca, zerarNavegacao } from "@/testes/navegacao";
import { ANONIMO, CLIENTE, fixarRelogio, renderizar } from "@/testes/render";
import { ReservaDados } from "./ReservaDados";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const NUMERO = "11900000001";
const BARBEIRO_COM_WHATSAPP = { ...BARBEIRO, whatsapp: NUMERO };
const SEXTA = "2026-11-27T07:00:00-03:00";
const SABADO = "2026-11-28T10:00:00-03:00";
const SERVICO = CINCO_SERVICOS[0];

function escolher(
  inicio: string,
  barbeiro: { id: string; nomeExibicao: string; whatsapp?: string | null } = BARBEIRO_COM_WHATSAPP,
) {
  definirBusca(new URLSearchParams({ servicoId: SERVICO.id, barbeiroId: barbeiro.id, inicio }).toString());
  servidor.use(
    http.get(`${BFF}/servicos`, () => HttpResponse.json(CINCO_SERVICOS)),
    http.get(`${BFF}/barbeiros`, () => HttpResponse.json([barbeiro])),
  );
}

const MENSAGEM_SEXTA =
  "Olá, Carlos! Quero marcar Corte Clássico / Social na sexta, 27/11, às 07:00. Esse horário está livre?";

beforeEach(() => {
  zerarNavegacao();
  fixarRelogio("2026-11-26T10:00:00-03:00");
});
afterEach(() => vi.useRealTimers());

describe("pedir o horário pelo WhatsApp", () => {
  test("WA-1 e WA-6: sem sessão, com número do barbeiro, aparecem Entrar, Continuar sem conta e o link com o nome acessível", async () => {
    escolher(SEXTA);
    renderizar(<ReservaDados />, ANONIMO);
    expect(await screen.findByRole("link", { name: "Entrar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar sem conta" })).toBeInTheDocument();
    const link = await screen.findByRole("link", {
      name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)",
    });
    expect(link).toHaveTextContent("Pedir este horário pelo WhatsApp");
    expect(screen.getByText("Pelo WhatsApp")).toBeInTheDocument();
    expect(
      screen.getByText("O horário só fica seu quando o Carlos confirmar. Até lá, outra pessoa pode marcar pelo site."),
    ).toBeInTheDocument();
  });

  test("WA-2: o href começa com wa.me/55 e o número da API, e o texto decodificado é a mensagem com na sexta, 27/11 e às 07:00", async () => {
    escolher(SEXTA);
    renderizar(<ReservaDados />, ANONIMO);
    const link = await screen.findByRole("link", {
      name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)",
    });
    const href = link.getAttribute("href")!;
    expect(href.startsWith(`https://wa.me/55${NUMERO}?text=`)).toBe(true);
    const texto = decodeURIComponent(href.slice(href.indexOf("text=") + 5));
    expect(texto).toBe(MENSAGEM_SEXTA);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  test("WA-4: sem WhatsApp do barbeiro, a opção some e as outras duas continuam", async () => {
    escolher(SEXTA, { ...BARBEIRO, whatsapp: null });
    renderizar(<ReservaDados />, ANONIMO);
    expect(await screen.findByRole("link", { name: "Entrar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar sem conta" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Pedir este horário pelo WhatsApp/ })).toBeNull();
    expect(screen.queryByText("Pelo WhatsApp")).toBeNull();
  });

  test("WA-5: num sábado a mensagem usa 'no sábado, dd/mm'", async () => {
    escolher(SABADO);
    renderizar(<ReservaDados />, ANONIMO);
    const link = await screen.findByRole("link", {
      name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)",
    });
    const href = link.getAttribute("href")!;
    const texto = decodeURIComponent(href.slice(href.indexOf("text=") + 5));
    expect(texto).toBe(
      "Olá, Carlos! Quero marcar Corte Clássico / Social no sábado, 28/11, às 10:00. Esse horário está livre?",
    );
  });

  test("quem está logado vê o mesmo aviso e o link abaixo de Reservar", async () => {
    escolher(SEXTA);
    renderizar(<ReservaDados />, CLIENTE);
    expect(await screen.findByRole("button", { name: "Reservar" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continuar sem conta" })).toBeNull();
    expect(
      screen.getByRole("link", { name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("O horário só fica seu quando o Carlos confirmar. Até lá, outra pessoa pode marcar pelo site."),
    ).toBeInTheDocument();
  });

  test("ao tocar no link, aparece a nota de volta em role=status, sem criar reserva", async () => {
    escolher(SEXTA);
    const posts: string[] = [];
    servidor.use(
      http.post(`${BFF}/agendamentos`, () => {
        posts.push("/agendamentos");
        return HttpResponse.json({}, { status: 201 });
      }),
      http.post(`${BFF}/agendamentos/guest`, () => {
        posts.push("/agendamentos/guest");
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    const { user } = renderizar(<ReservaDados />, ANONIMO);
    const link = await screen.findByRole("link", {
      name: "Pedir este horário pelo WhatsApp (abre o WhatsApp)",
    });
    await user.click(link);
    expect(posts).toEqual([]);
    const nota = await screen.findByRole("status");
    expect(nota).toHaveTextContent("Mandou a mensagem? Agora é com o Carlos: ele confirma na conversa.");
    expect(screen.getByRole("link", { name: "Escolher outro horário" })).toHaveAttribute(
      "href",
      expect.stringContaining("/agendar?"),
    );
  });
});

describe("helpers do pedido pelo WhatsApp", () => {
  test("preposicaoDoDia usa no em sábado e domingo, e na nos outros dias", () => {
    expect(preposicaoDoDia("sábado")).toBe("no");
    expect(preposicaoDoDia("sabado")).toBe("no");
    expect(preposicaoDoDia("domingo")).toBe("no");
    expect(preposicaoDoDia("sexta")).toBe("na");
    expect(preposicaoDoDia("terça")).toBe("na");
  });

  test("linkDoWhatsapp acrescenta o texto só quando ele vem", () => {
    expect(linkDoWhatsapp(NUMERO)).toBe(`https://wa.me/55${NUMERO}`);
    expect(linkDoWhatsapp(NUMERO, "Olá")).toBe(`https://wa.me/55${NUMERO}?text=${encodeURIComponent("Olá")}`);
  });
});
