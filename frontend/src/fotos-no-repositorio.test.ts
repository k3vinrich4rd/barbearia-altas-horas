import { readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, test } from "vitest";

// Fatia 16, A20: nenhuma foto de cliente no repositório. Foto de corte (nuca, costas, lateral sem rosto, sem crianças)
// fica num armazenamento externo e entra no site por URL, nunca por commit em docs/assets nem em frontend/public.
// Acrescentar uma imagem aqui exige mudar esta lista no mesmo PR, e a revisão do PR confere que ela não mostra cliente.
const RAIZ = join(__dirname, "..", "..");
const IMAGEM = /\.(jpe?g|png|webp|avif|gif|heic|svg)$/i;

/** Imagens aprovadas em docs/assets: logo, banners, o Eduardo (que autorizou), os prêmios e os recortes sem pessoas. */
const APROVADAS = [
  "agenda-semana.svg",
  "banner-claro.svg",
  "banner-escuro.svg",
  "banner.svg",
  "divisor.svg",
  "eduardo-candido-avatar.png",
  "eduardo-candido.jpg",
  "fachada-sem-tv.jpg",
  "galeria/salao-vitrine-sem-tv.jpg",
  "logo-altas-horas.png",
  "premios/premio-barbeiros-do-brasil-2017.jpg",
  "premios/premio-master-barber-2017.jpg",
];

function imagens(pasta: string, ignorar: string[] = []): string[] {
  return readdirSync(pasta).flatMap((nome) => {
    const caminho = join(pasta, nome);
    if (ignorar.includes(nome)) return [];
    if (statSync(caminho).isDirectory()) return imagens(caminho);
    return IMAGEM.test(nome) ? [caminho] : [];
  });
}

describe("fotos no repositório (Fatia 16)", () => {
  test("A20: nenhuma imagem de cliente: docs/assets só tem as imagens aprovadas e a galeria só o recorte do salão", () => {
    const assets = join(RAIZ, "docs", "assets");
    const achadas = imagens(assets)
      .map((arq) => relative(assets, arq).split("\\").join("/"))
      .sort();
    expect(achadas.filter((arq) => !APROVADAS.includes(arq))).toEqual([]);
    expect(achadas.filter((arq) => arq.startsWith("galeria/"))).toEqual(["galeria/salao-vitrine-sem-tv.jpg"]);
  });

  test("A20: frontend/public não tem imagem própria (public/assets é cópia de docs/assets, fora do git)", () => {
    const publico = join(RAIZ, "frontend", "public");
    expect(imagens(publico, ["assets"]).map((arq) => relative(publico, arq))).toEqual([]);
  });
});
