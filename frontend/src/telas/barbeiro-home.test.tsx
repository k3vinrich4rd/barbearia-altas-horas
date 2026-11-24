import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { BarbeiroDaHome } from "@/componentes/BarbeiroDaHome";
import { BARBEIRO } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const EDUARDO = { ...BARBEIRO, nomeExibicao: "Eduardo Candido" };

describe("bloco do barbeiro na home (Fatia 12)", () => {
  test("A9: com foto e WhatsApp mostra nome, foto com texto alternativo e o botão wa.me só com dígitos; sem WhatsApp, sem botão", async () => {
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...EDUARDO, whatsapp: "11996941689" }])));
    const { unmount } = renderizar(<BarbeiroDaHome />);

    const bloco = (await screen.findByRole("heading", { name: "Quem corta o seu cabelo" })).closest("section")!;
    expect(bloco).toHaveAttribute("id", "barbeiro");
    expect(await within(bloco).findByRole("heading", { name: "Eduardo Candido" })).toBeInTheDocument();
    const foto = within(bloco).getByRole("img");
    expect(foto.getAttribute("alt")?.length ?? 0).toBeGreaterThan(10);
    expect(foto.getAttribute("src") ?? "").toContain("eduardo-candido");
    const whatsapp = within(bloco).getByRole("link", { name: /Falar com Eduardo no WhatsApp/ });
    expect(whatsapp).toHaveAttribute("href", "https://wa.me/5511996941689");
    expect(whatsapp.getAttribute("href")).toMatch(/^https:\/\/wa\.me\/\d+$/);
    expect(whatsapp).toHaveAttribute("target", "_blank");
    expect(whatsapp).toHaveAttribute("rel", "noopener noreferrer");
    unmount();

    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...EDUARDO, whatsapp: null }])));
    renderizar(<BarbeiroDaHome />);
    const semZap = (await screen.findByRole("heading", { name: "Quem corta o seu cabelo" })).closest("section")!;
    expect(await within(semZap).findByRole("heading", { name: "Eduardo Candido" })).toBeInTheDocument();
    expect(within(semZap).queryByRole("link", { name: /WhatsApp/ })).toBeNull();
    expect(within(semZap).getByRole("link", { name: /Agendar com Eduardo/ })).toBeInTheDocument();
  });

  test("A10: 'Agendar com Eduardo' leva a /agendar?barbeiroId=<id> com o barbeiro marcado", async () => {
    servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ ...EDUARDO, whatsapp: "11996941689" }])));
    renderizar(<BarbeiroDaHome />);
    const agendar = await screen.findByRole("link", { name: "Agendar com Eduardo" });
    expect(agendar).toHaveAttribute("href", `/agendar?barbeiroId=${BARBEIRO.id}`);
  });
});
