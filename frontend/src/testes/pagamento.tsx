// Apoio dos testes de pagamento: monta a tela com a reserva do visitante e preenche o formulário.
import { screen } from "@testing-library/react";
import { vi } from "vitest";
import { POLITICA_PAGAMENTO, reserva } from "./dados";
import { BFF, http, HttpResponse, servidor } from "./msw";
import { renderizar } from "./render";
import { Pagamento } from "@/telas/Pagamento";
import { CPF_DE_TESTE } from "./dados";

export const cobrancaPix = (extra: Record<string, unknown> = {}) => ({
  id: "c0000000-0000-4000-8000-000000000001",
  reservaId: reserva().id,
  status: "PENDENTE",
  metodo: "PIX",
  valorCentavos: 5500,
  pix: { copiaECola: "SIMULADO-0001-ABCDEF", qrCodeBase64: null, expiraEm: reserva().expiraEm },
  cartao: null,
  cpfMascarado: "***.***.***-25",
  ...extra,
});

/** Handlers da API para a reserva do visitante e a política. `status` pode mudar durante o teste. */
export function api(opcoes: { status?: () => string; politica?: object; expiraEm?: string } = {}) {
  const leituras = { reserva: 0 };
  servidor.use(
    http.get(`${BFF}/politica-pagamento`, () => HttpResponse.json(opcoes.politica ?? POLITICA_PAGAMENTO)),
    http.get(`${BFF}/agendamentos/guest/:id`, () => {
      leituras.reserva++;
      return HttpResponse.json(
        reserva({
          status: opcoes.status?.() ?? "AGUARDANDO_PAGAMENTO",
          ...(opcoes.expiraEm ? { expiraEm: opcoes.expiraEm } : {}),
        }),
      );
    }),
  );
  return leituras;
}

export const montar = (opcoesUsuario: Parameters<typeof renderizar>[2] = {}) =>
  renderizar(<Pagamento reservaId={reserva().id} />, { autenticado: false }, opcoesUsuario);

export async function preencher(user: ReturnType<typeof renderizar>["user"], cpf = CPF_DE_TESTE) {
  await screen.findByText(POLITICA_PAGAMENTO.texto.slice(0, 30), { exact: false });
  await user.click(screen.getByLabelText(/Li e aceito/));
  await user.type(screen.getByLabelText("CPF"), cpf);
}

export const espiarConsole = () =>
  (["log", "info", "warn", "error"] as const).map((m) => vi.spyOn(console, m).mockImplementation(() => {}));
