// Sobe o `next start` para os E2E guardando a saída do servidor em e2e/.logs/next.log,
// para o teste A60 conferir que nenhum dado pessoal vai para o log do servidor do Next.
import { spawn } from "node:child_process";
import { createWriteStream, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), "..");
mkdirSync(resolve(raiz, "e2e/.logs"), { recursive: true });
const log = createWriteStream(resolve(raiz, "e2e/.logs/next.log"), { flags: "w" });
const filho = spawn(process.execPath, [resolve(raiz, "node_modules/next/dist/bin/next"), "start", "-p", process.env.PORT ?? "3000"], {
  cwd: raiz,
  env: process.env,
});
filho.stdout.pipe(log, { end: false });
filho.stderr.pipe(log, { end: false });
filho.on("exit", (codigo) => process.exit(codigo ?? 0));
for (const sinal of ["SIGINT", "SIGTERM"]) process.on(sinal, () => filho.kill(sinal));
