// Código da reserva do visitante (E7, D-7). O BFF guarda `{ reservaId, token }` no cookie HttpOnly `ah_visitante`
// (gravado em proxy.ts quando a reserva é criada). Esta rota devolve o código à tela de confirmação, para
// o visitante copiar, e só se o cookie for da mesma reserva. O código nunca vai em URL, storage nem log.
import { lerVisitante } from "./cookies";
import { naoEncontrado } from "./problema";

export function codigoDaReserva(request: Request): Response {
  const reservaId = new URL(request.url).searchParams.get("reservaId");
  const v = lerVisitante(request);
  if (!reservaId || !v || v.reservaId !== reservaId) return naoEncontrado();
  return new Response(JSON.stringify({ reservaId, codigo: v.token }), {
    status: 200,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
