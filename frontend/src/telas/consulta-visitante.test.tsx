import { screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { reserva } from "@/testes/dados";
import { BFF, erro, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { zerarNavegacao } from "@/testes/navegacao";
import { fixarRelogio, renderizar } from "@/testes/render";
import { ConsultaVisitante } from "./ConsultaVisitante";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const ID = reserva().id;
beforeEach(() => {
  zerarNavegacao();
  fixarRelogio();
  window.history.replaceState(null, "", "/reserva");
});
afterEach(() => vi.useRealTimers());

describe("consulta do visitante", () => {
  test("A32: lê o fragmento e o remove do endereço, consulta com X-Guest-Token e usa o mesmo texto para código errado e reserva inexistente", async () => {
    const vistos: (string | null)[] = [];
    servidor.use(
      http.get(`${BFF}/agendamentos/guest/:id`, ({ request, params }) => {
        vistos.push(request.headers.get("x-guest-token"));
        return params.id === ID && request.headers.get("x-guest-token") === "CODIGO-CERTO" ? HttpResponse.json(reserva({ status: "CONFIRMADO", expiraEm: null })) : erro(404, "RECURSO_NAO_ENCONTRADO");
      }),
    );

    // 1) link com fragmento
    window.history.replaceState(null, "", `/reserva#id=${ID}&codigo=CODIGO-CERTO`);
    const a = renderizar(<ConsultaVisitante />);
    expect(await screen.findByText("Confirmado")).toBeInTheDocument();
    expect(vistos).toEqual(["CODIGO-CERTO"]);
    expect(window.location.hash).toBe("");
    expect(window.location.href).not.toContain("CODIGO-CERTO");
    a.unmount();

    // 2) digitando à mão: código errado e id inexistente dão o MESMO texto
    const { user } = renderizar(<ConsultaVisitante />);
    await user.type(screen.getByLabelText("Número da reserva"), ID);
    await user.type(screen.getByLabelText("Código da reserva"), "ERRADO");
    await user.click(screen.getByRole("button", { name: "Consultar" }));
    const errado = (await screen.findByRole("alert")).textContent;
    expect(errado).toContain("Não encontramos esta reserva.");

    await user.clear(screen.getByLabelText("Número da reserva"));
    await user.type(screen.getByLabelText("Número da reserva"), "id-que-nao-existe");
    await user.type(screen.getByLabelText("Código da reserva"), "CODIGO-CERTO");
    await user.click(screen.getByRole("button", { name: "Consultar" }));
    await waitFor(() => expect(vistos).toHaveLength(3));
    expect((await screen.findByRole("alert")).textContent).toBe(errado);
    expect(screen.queryByLabelText("Código da reserva")).toHaveValue(""); // o código digitado não fica no campo
    expect(window.location.href).not.toContain("CODIGO");
  });
});
