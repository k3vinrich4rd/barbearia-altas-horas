// Galeria da home (Fatia 12, A11 a A13, revista em 27/11/2026): só fotos sem rosto e sem criança. Com 1 foto, destaque
// largo sem visualizador; com 2 ou mais, grade com a foto inteira pelo next/image e visualizador por teclado.
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { screen, within } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { FOTO_DO_SALAO, GALERIA, type FotoDaGaleria } from "@/conteudo/historia";
import { SEIS_SERVICOS } from "@/testes/dados";
import { BFF, http, HttpResponse, ligarServidor, servidor } from "@/testes/msw";
import { renderizar } from "@/testes/render";
import { Galeria } from "./Galeria";
import { Menu } from "./Menu";

vi.mock("next/navigation", async () => (await import("@/testes/navegacao")).modulo);
ligarServidor();

const raiz = resolve(__dirname, "../../..");
const arquivo = (src: string) => resolve(raiz, "docs/assets", src.replace("/assets/", ""));

/** As únicas fotos aprovadas (27/11/2026): nenhum rosto, nenhuma criança, nenhuma TV com gente, nenhum reflexo. */
const APROVADAS = ["/assets/galeria/salao-vitrine-sem-tv.jpg"];

/** Fotos que saíram do site e da versão atual do repositório (privacidade, 27/11/2026). */
const QUE_SAIRAM = [
  "galeria/eduardo-em-atendimento.jpg",
  "galeria/corte-topete-parede-do-logo.jpg",
  "galeria/corte-degrade-com-desenho.jpg",
  "galeria/corte-social-grisalho-na-regua.jpg",
  "galeria/corte-infantil-cacheado-com-degrade.jpg",
  "galeria/corte-franja-curta-jovem.jpg",
  "galeria/platinado-cliente-de-oculos.jpg",
  "galeria/corte-infantil-risca-lateral.jpg",
  "galeria/corte-cacheado-volume.jpg",
  "galeria/platinado-cliente-e-eduardo.jpg",
  "galeria/corte-topo-volumoso-com-degrade.jpg",
  "galeria/fade-perfil-capa-altas-horas.jpg",
  "galeria/barba-cheia-desenhada.jpg",
  "galeria/degrade-parede-do-logo.jpg",
  "galeria/degrade-baixo-alinhamento-na-cadeira.jpg",
  "galeria/franja-reta-alinhada.jpg",
  "galeria/degrade-grisalho-penteado-para-tras.jpg",
  "galeria/salao-vitrine-com-logo.jpg",
  "fachada.jpg",
];

// Lista de exemplo para a grade (2 ou mais fotos): arquivos que já existem e não mostram cliente (o logo e a foto do
// Eduardo). Só existe no teste; a galeria publicada é a GALERIA (vazia no cenário A).
const TRES: readonly FotoDaGaleria[] = [
  FOTO_DO_SALAO,
  {
    src: "/assets/logo-altas-horas.png",
    legenda: "O logo",
    alt: "Logo redondo da Barbearia Altas Horas, preto e branco",
    largura: 512,
    altura: 512,
  },
  {
    src: "/assets/eduardo-candido.jpg",
    legenda: "O barbeiro",
    alt: "Eduardo Candido, de camisa branca e gravata preta, olhando para a câmera",
    largura: 480,
    altura: 480,
  },
];

describe("galeria da home", () => {
  test("A11: só as fotos aprovadas, sem rosto e sem criança; as que saíram não existem mais na pasta de imagens", () => {
    // cenário A (Fatia 16): nenhuma foto de corte aprovada; a do salão fica fora da galeria
    expect(GALERIA).toEqual([]);
    expect(FOTO_DO_SALAO.src).toBe(APROVADAS[0]);
    for (const f of [FOTO_DO_SALAO, ...GALERIA]) {
      expect(existsSync(arquivo(f.src)), f.src).toBe(true);
      expect(f.alt.trim().length, f.src).toBeGreaterThan(20);
      expect(f.legenda.trim()).not.toBe("");
    }
    for (const nome of QUE_SAIRAM) expect(existsSync(resolve(raiz, "docs/assets", nome)), nome).toBe(false);
  });

  test("A12: com 1 foto, destaque largo pelo next/image (srcset, sizes, medidas reais), preguiçoso e sem botão", () => {
    renderizar(<Galeria fotos={[FOTO_DO_SALAO]} />);
    const secao = screen.getByRole("region", { name: "O salão da Altas Horas" });
    expect(within(secao).queryByRole("list")).toBeNull();
    expect(within(secao).queryByRole("button")).toBeNull();
    const foto = within(secao).getByRole("img", { name: FOTO_DO_SALAO.alt });
    expect(foto.getAttribute("srcset")).toContain("/_next/image");
    expect(foto.getAttribute("sizes")).toBeTruthy();
    expect(foto).toHaveAttribute("width", String(FOTO_DO_SALAO.largura));
    expect(foto).toHaveAttribute("height", String(FOTO_DO_SALAO.altura));
    expect(foto).toHaveAttribute("loading", "lazy");
    expect(within(secao).getByText("O salão")).toBeInTheDocument();
  });

  test("A12: sem foto aprovada, a seção não aparece", () => {
    const { container } = renderizar(<Galeria fotos={[]} />);
    expect(container.querySelector("#galeria")).toBeNull();
  });

  test("A12: com 2 ou mais, grade com a foto inteira (contain); só foto sem pessoa preenche o quadro (cover)", () => {
    renderizar(<Galeria fotos={TRES} />);
    const lista = screen.getByRole("list");
    expect(lista).toHaveAttribute("data-quantidade", "3");
    const fotos = within(lista).getAllByRole("img");
    expect(fotos).toHaveLength(3);
    fotos.forEach((foto, i) => {
      expect(foto.getAttribute("srcset"), `foto ${i + 1}`).toContain("/_next/image");
      expect(foto).toHaveAttribute("width", String(TRES[i].largura));
      expect(foto).toHaveAttribute("height", String(TRES[i].altura));
      expect(foto).toHaveAttribute("loading", "lazy");
      expect(foto).toHaveClass("galeria-foto");
    });
    expect(fotos[0]).toHaveClass("galeria-foto-cobre");
    expect(fotos[1]).not.toHaveClass("galeria-foto-cobre");
    // jsdom não aplica CSS: a regra que dá a foto inteira (sem corte) é conferida no arquivo de estilos
    const css = readFileSync(resolve(__dirname, "../estilos/globais.css"), "utf8");
    const regra = css.match(/\.galeria-foto\s*\{[^}]*\}/)?.[0] ?? "";
    expect(regra).toMatch(/object-fit:\s*contain/);
    expect(regra).not.toMatch(/object-fit:\s*cover/);
  });

  test("A13: com 2 ou mais, Enter abre a foto inteira com legenda, as setas trocam, Esc fecha e o foco volta", async () => {
    const { user } = renderizar(<Galeria fotos={TRES} />);
    const botoes = screen.getAllByRole("button", { name: /^Ampliar foto:/ });
    expect(botoes).toHaveLength(3);

    botoes[1].focus();
    await user.keyboard("{Enter}");
    const dialogo = screen.getByRole("dialog");
    expect(dialogo).toHaveAccessibleName(TRES[1].legenda);
    expect(within(dialogo).getByRole("img", { name: TRES[1].alt })).toBeInTheDocument();
    expect(within(dialogo).getByText("Foto 2 de 3")).toBeInTheDocument();

    await user.keyboard("{ArrowRight}");
    expect(dialogo).toHaveAccessibleName(TRES[2].legenda);
    expect(within(dialogo).getByText("Foto 3 de 3")).toBeInTheDocument();
    await user.keyboard("{ArrowLeft}{ArrowLeft}");
    expect(within(dialogo).getByText("Foto 1 de 3")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(botoes[0]).toHaveFocus();

    // por clique e pelos botões: a primeira volta para a última
    await user.click(botoes[0]);
    await user.click(screen.getByRole("button", { name: /Anterior/ }));
    expect(screen.getByText("Foto 3 de 3")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Fechar" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(botoes[2]).toHaveFocus();
  });

  test("A17: sem foto de corte aprovada (cenário A), a home não tem a seção da galeria e o menu não tem o link \"Galeria\"", async () => {
    servidor.use(
      http.get(`${BFF}/servicos`, () => HttpResponse.json(SEIS_SERVICOS)),
      http.get(`${BFF}/alcance`, () => HttpResponse.json({ atualizadoEm: "2026-11-27T00:00:00Z", totalCidades: 0, totalBairros: 0, regioes: [] })),
    );
    const { container } = renderizar(
      <>
        <Menu />
        <Home />
      </>,
    );
    await screen.findByText("Descoloração / Platinado (química)");
    expect(container.querySelector("#galeria")).toBeNull();
    expect(screen.queryByRole("link", { name: /Galeria/ })).toBeNull();
    expect(container.querySelector('a[href$="#galeria"]')).toBeNull();
    expect(screen.queryByRole("heading", { name: /O salão da Altas Horas|Do degradê ao acabamento/ })).toBeNull();
  });
});
