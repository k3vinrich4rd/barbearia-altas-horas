import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import type { EstadoDaSessao } from "@/api/bff";
import { SessaoProvider } from "@/componentes/Sessao";
import { vi } from "vitest";

export const ANONIMO: EstadoDaSessao = { autenticado: false };
export const CLIENTE: EstadoDaSessao = {
  autenticado: true,
  usuario: { id: "u1", nome: "Cliente Teste" },
  papeis: ["CLIENTE"],
};
export const ADMIN: EstadoDaSessao = {
  autenticado: true,
  usuario: { id: "u2", nome: "Admin Teste" },
  papeis: ["ADMIN"],
};

/** Renderiza a tela dentro da sessão (sem chamar /api/sessao) e devolve o `user` do user-event. */
export function renderizar(
  ui: ReactElement,
  sessao: EstadoDaSessao = ANONIMO,
  opcoesUsuario: Parameters<typeof userEvent.setup>[0] = {},
) {
  const user = userEvent.setup(opcoesUsuario);
  return { user, ...render(<SessaoProvider inicial={sessao}>{ui}</SessaoProvider>) };
}

/** Relógio de teste (spec: vi.setSystemTime). Só a data é falsa: timers continuam reais. */
export function fixarRelogio(iso = "2026-11-18T08:00:00-03:00") {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(iso));
}
