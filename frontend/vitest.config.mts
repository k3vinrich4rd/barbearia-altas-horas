import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: {
    environment: "jsdom",
    environmentOptions: { jsdom: { url: "http://localhost:3000/" } },
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
    testTimeout: 15000,
    coverage: {
      provider: "v8",
      include: ["src/**"],
      exclude: ["src/**/*.test.*", "src/testes/**", "src/api/schema.d.ts"],
      reporter: ["text-summary", "lcov"],
    },
  },
});
