// @vitest-environment node
import { describe, expect, test } from "vitest";
import { apiFalsa, cookiesDe, json, problemaApi, requisicao, valorDoCookie } from "@/testes/bff";
import { encaminhar } from "./proxy";
import { codigoDaReserva } from "./visitante";

describe("código do visitante no BFF", () => {
  test("A8: grava ah_visitante HttpOnly, paga com X-Guest-Token do cookie e outra reserva dá 404 como na API", async () => {
    const api = apiFalsa((c) => {
      if (c.caminho === "/agendamentos/guest" && c.metodo === "POST") return json({ id: "reserva-1", status: "AGUARDANDO_PAGAMENTO", tokenGuest: "CODIGO-SECRETO" }, 201);
      if (c.caminho === "/pagamentos/cobrancas") return json({ id: "cob-1", reservaId: "reserva-1", status: "PENDENTE", valorCentavos: 5500 }, 201);
      if (c.caminho.startsWith("/agendamentos/guest/")) return c.caminho.endsWith("/reserva-1") && c.headers.get("x-guest-token") === "CODIGO-SECRETO" ? json({ id: "reserva-1" }) : problemaApi(404, "RECURSO_NAO_ENCONTRADO");
      return problemaApi(404, "RECURSO_NAO_ENCONTRADO");
    });

    const criada = await encaminhar(requisicao("/api/agendamentos/guest", { metodo: "POST", corpo: { nome: "Fulano" } }), ["agendamentos", "guest"], api.deps);
    expect(criada.status).toBe(201);
    expect((await criada.json()).tokenGuest).toBe("CODIGO-SECRETO");
    const cookie = cookiesDe(criada).ah_visitante;
    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("SameSite=Strict");
    expect(cookie).toContain("Secure");

    const cookies = { ah_visitante: valorDoCookie(cookie) };
    const cobranca = await encaminhar(requisicao("/api/pagamentos/cobrancas", { metodo: "POST", cookies, corpo: { reservaId: "reserva-1" }, headers: { "x-idempotency-key": "k1" } }), ["pagamentos", "cobrancas"], api.deps);
    expect(cobranca.status).toBe(201);
    const paga = api.chamadas.find((c) => c.caminho === "/pagamentos/cobrancas")!;
    expect(paga.headers.get("x-guest-token")).toBe("CODIGO-SECRETO");
    expect(paga.headers.get("authorization")).toBeNull();
    expect(paga.headers.get("x-idempotency-key")).toBe("k1");

    const outra = await encaminhar(requisicao("/api/agendamentos/guest/reserva-2", { cookies }), ["agendamentos", "guest", "reserva-2"], api.deps);
    expect(outra.status).toBe(404);
  });

  test("a confirmação lê o código do cookie só para a mesma reserva", async () => {
    const api = apiFalsa(() => json({ id: "reserva-1", tokenGuest: "CODIGO-SECRETO" }, 201));
    const criada = await encaminhar(requisicao("/api/agendamentos/guest", { metodo: "POST", corpo: {} }), ["agendamentos", "guest"], api.deps);
    const cookies = { ah_visitante: valorDoCookie(cookiesDe(criada).ah_visitante) };
    const certo = codigoDaReserva(requisicao("/api/visitante?reservaId=reserva-1", { cookies }));
    expect(await certo.json()).toEqual({ reservaId: "reserva-1", codigo: "CODIGO-SECRETO" });
    expect(codigoDaReserva(requisicao("/api/visitante?reservaId=outra", { cookies })).status).toBe(404);
    expect(codigoDaReserva(requisicao("/api/visitante?reservaId=reserva-1")).status).toBe(404);
  });
});
