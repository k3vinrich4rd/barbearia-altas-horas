// Falso de `next/navigation` para os testes de tela. Uso, no topo do arquivo de teste:
//   vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
import { vi } from "vitest";

export const roteador = { push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), back: vi.fn(), prefetch: vi.fn() };
let busca = new URLSearchParams();
let caminho = "/";

export const definirBusca = (consulta: string) => {
  busca = new URLSearchParams(consulta);
};
export const definirCaminho = (c: string) => {
  caminho = c;
};
export const zerarNavegacao = () => {
  Object.values(roteador).forEach((f) => f.mockReset());
  busca = new URLSearchParams();
  caminho = "/";
};

export const modulo = {
  useRouter: () => roteador,
  useSearchParams: () => busca,
  usePathname: () => caminho,
};
