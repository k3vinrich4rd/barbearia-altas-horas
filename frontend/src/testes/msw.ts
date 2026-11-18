// Servidor falso (MSW) SÓ para os testes unitários (spec, E22). Os E2E usam a API real.
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";

export { http, HttpResponse, delay } from "msw";

export const servidor = setupServer();
/** Base do BFF no navegador de teste (jsdom em http://localhost:3000). */
export const BFF = "http://localhost:3000/api";

export function ligarServidor() {
  beforeAll(() => servidor.listen({ onUnhandledRequest: "error" }));
  // padrão de todas as telas: a lista de barbeiros (a home e o agendamento a consultam); cada teste pode sobrepor
  beforeEach(() => servidor.use(http.get(`${BFF}/barbeiros`, () => HttpResponse.json([{ id: "b0000000-0000-4000-8000-000000000001", nomeExibicao: "Carlos Eduardo" }]))));
  afterEach(() => servidor.resetHandlers());
  afterAll(() => servidor.close());
}

/** Resposta problem+json (RFC 9457) no formato da API. */
export function erro(status: number, code: string, extra: Record<string, unknown> = {}, headers: Record<string, string> = {}) {
  return HttpResponse.json(
    { type: "about:blank", title: "qualquer título", status, code, traceId: "trace-123", ...extra },
    { status, headers: { "content-type": "application/problem+json", ...headers } },
  );
}
