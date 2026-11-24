import { defineConfig, devices } from "@playwright/test";

// E2E contra a API REAL local (perfil dev + PostgreSQL do compose). O front sobe com `next start` (precisa do build).
// Limites da API elevados só por variável de ambiente (E24); nenhum código de produção é alterado.
const API_URL = process.env.API_URL ?? "http://localhost:8080";

export default defineConfig({
  testDir: "./e2e",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://localhost:${process.env.PORT ?? 3000}`,
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    trace: "retain-on-failure",
    ...devices["Desktop Chrome"],
  },
  webServer: {
    command: "node scripts/servir-e2e.mjs",
    url: `http://localhost:${process.env.PORT ?? 3000}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: { API_URL, NEXT_PUBLIC_PAGAMENTO_SIMULADO: "true", NEXT_PUBLIC_HORIZONTE_DIAS: "30" },
  },
});
