import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescript from "eslint-config-next/typescript";

const config = [
  ...coreWebVitals,
  ...typescript,
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "playwright-report/**",
      "test-results/**",
      "coverage/**",
      "public/**",
      "src/api/schema.d.ts",
      "next-env.d.ts",
    ],
  },
  {
    rules: {
      // O front não usa console fora dos testes: dado pessoal em log é proibido (R30).
      "no-console": "error",
      // Carregar dados e sincronizar com o navegador a partir de efeitos é o padrão deste front (hooks.ts).
      "react-hooks/set-state-in-effect": "off",
    },
  },
  {
    files: [
      "scripts/**",
      "e2e/**",
      "**/*.test.ts",
      "**/*.test.tsx",
      "src/testes/**",
      "vitest.*.ts",
      "playwright.config.ts",
    ],
    rules: { "no-console": "off" },
  },
];

export default config;
