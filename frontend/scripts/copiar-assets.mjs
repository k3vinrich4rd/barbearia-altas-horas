// Copia logo, fotos e grafismos de docs/assets/ para public/ (public/ fica fora do Git: sem binário duplicado).
import { cpSync, existsSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const origem = resolve(raiz, "../docs/assets");
const destino = resolve(raiz, "public/assets");
if (!existsSync(origem)) {
  console.error(`pasta de assets não encontrada: ${origem}`);
  process.exit(1);
}
mkdirSync(destino, { recursive: true });
cpSync(origem, destino, { recursive: true });
console.log("assets copiados de docs/assets para public/assets");
