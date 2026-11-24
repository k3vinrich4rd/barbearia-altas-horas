// Galeria da home (Fatia 12, A11 a A13): 18 fotos com texto alternativo, foto inteira pelo next/image e visualizador por teclado.
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { screen, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { GALERIA } from "@/conteudo/historia";
import { renderizar } from "@/testes/render";
import { Galeria } from "./Galeria";

const raiz = resolve(__dirname, "../../..");

describe("galeria da home", () => {
  test("A11: são 18 fotos (3 atuais e 15 novas), todas com alt não vazio e com o arquivo na pasta de imagens", () => {
    renderizar(<Galeria />);
    expect(GALERIA).toHaveLength(18);
    const fotos = within(screen.getByRole("list")).getAllByRole("img");
    expect(fotos).toHaveLength(18);
    for (const foto of fotos) expect(foto.getAttribute("alt")?.trim().length ?? 0).toBeGreaterThan(20);
    for (const f of GALERIA) {
      expect(existsSync(resolve(raiz, "docs/assets", f.src.replace("/assets/", ""))), f.src).toBe(true);
      expect(f.legenda.trim()).not.toBe("");
    }
    expect(new Set(GALERIA.map((f) => f.src)).size).toBe(18);
    expect(new Set(GALERIA.map((f) => f.alt)).size).toBe(18);
  });

  test("A12: cada foto é do next/image (srcset, sizes, medidas reais), com object-fit: contain e preguiçosa", () => {
    renderizar(<Galeria />);
    const fotos = within(screen.getByRole("list")).getAllByRole("img");
    fotos.forEach((foto, i) => {
      expect(foto.getAttribute("srcset"), `foto ${i + 1}`).toContain("/_next/image");
      expect(foto.getAttribute("sizes"), `foto ${i + 1}`).toBeTruthy();
      expect(foto).toHaveAttribute("width", String(GALERIA[i].largura));
      expect(foto).toHaveAttribute("height", String(GALERIA[i].altura));
      expect(foto).toHaveAttribute("loading", "lazy");
      expect(foto).toHaveClass("galeria-foto");
    });
    // jsdom não aplica CSS: a regra que dá a foto inteira (sem corte) é conferida no arquivo de estilos
    const css = readFileSync(resolve(__dirname, "../estilos/globais.css"), "utf8");
    const regra = css.match(/\.galeria-foto\s*\{[^}]*\}/)?.[0] ?? "";
    expect(regra).toMatch(/object-fit:\s*contain/);
    expect(regra).not.toMatch(/object-fit:\s*cover/);
  });

  test("A13: Enter abre a foto inteira com legenda, as setas trocam, Esc fecha e o foco volta à miniatura", async () => {
    const { user } = renderizar(<Galeria />);
    const botoes = screen.getAllByRole("button", { name: /^Ampliar foto:/ });
    expect(botoes).toHaveLength(18);

    botoes[2].focus();
    await user.keyboard("{Enter}");
    const dialogo = screen.getByRole("dialog");
    expect(dialogo).toHaveAccessibleName(GALERIA[2].legenda);
    expect(within(dialogo).getByRole("img", { name: GALERIA[2].alt })).toBeInTheDocument();
    expect(within(dialogo).getByText("Foto 3 de 18")).toBeInTheDocument();

    await user.keyboard("{ArrowRight}");
    expect(dialogo).toHaveAccessibleName(GALERIA[3].legenda);
    expect(within(dialogo).getByText("Foto 4 de 18")).toBeInTheDocument();
    await user.keyboard("{ArrowLeft}{ArrowLeft}");
    expect(within(dialogo).getByText("Foto 2 de 18")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(botoes[1]).toHaveFocus();

    // por clique e pelos botões: a primeira volta para a última
    await user.click(botoes[0]);
    await user.click(screen.getByRole("button", { name: /Anterior/ }));
    expect(screen.getByText("Foto 18 de 18")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Fechar" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(botoes[17]).toHaveFocus();
  });
});
