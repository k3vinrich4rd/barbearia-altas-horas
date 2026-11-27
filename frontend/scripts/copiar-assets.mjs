// Copia logo, fotos e grafismos de docs/assets/ para public/ (public/ fica fora do Git: sem binário duplicado).
import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
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
// O cpSync só acrescenta: foto que saiu de docs/assets ficaria velha em public/ e continuaria publicada. A galeria e a
// fachada antiga são apagadas antes da cópia, para o site publicar só o que está hoje em docs/assets (27/11/2026).
rmSync(resolve(destino, "galeria"), { recursive: true, force: true });
rmSync(resolve(destino, "fachada.jpg"), { force: true });
cpSync(origem, destino, { recursive: true });
console.log("assets copiados de docs/assets para public/assets");
