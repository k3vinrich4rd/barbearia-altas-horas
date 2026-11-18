import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { Aviso } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { api, chamar } from "@/api/cliente";
import { useEnvio } from "@/api/hooks";
import { BFF, erro, http, ligarServidor, servidor } from "@/testes/msw";

ligarServidor();
beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => vi.useRealTimers());

function Formulario() {
  const envio = useEnvio();
  return (
    <form onSubmit={(e) => { e.preventDefault(); void envio.executar(() => chamar(api().GET("/servicos"))); }}>
      <Botao type="submit" disabled={envio.enviando || envio.esperando}>Enviar</Botao>
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
    </form>
  );
}

describe("limite de tentativas", () => {
  test("A15: 429 com Retry-After desabilita o botão, a contagem anda e o botão volta no fim", async () => {
    servidor.use(http.get(`${BFF}/servicos`, () => erro(429, "LIMITE_EXCEDIDO", {}, { "retry-after": "5" })));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Formulario />);
    await user.click(screen.getByRole("button", { name: "Enviar" }));
    expect(await screen.findByText("Tente de novo em 5 s.", { exact: false })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar" })).toBeDisabled();
    const lerEspera = () => Number(/em (\d+) s/.exec(screen.getByRole("status").textContent ?? "")?.[1]);
    const antes = lerEspera();
    await act(async () => { await vi.advanceTimersByTimeAsync(1100); });
    expect(lerEspera()).toBeLessThan(antes);
    await act(async () => { await vi.advanceTimersByTimeAsync(5200); });
    expect(screen.getByRole("button", { name: "Enviar" })).toBeEnabled();
    expect(screen.queryByText(/Tente de novo em/)).toBeNull();
  });
});
