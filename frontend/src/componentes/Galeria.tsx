"use client";
// Galeria da home (Fatia 12, E3 e E4): grade de miniaturas com a foto inteira (object-fit: contain, sem corte) e, ao
// tocar, o visualizador com a foto inteira, a legenda e as setas. Tudo pelo next/image (largura e altura reais,
// `sizes`, carregamento preguiçoso), tudo por teclado: Enter abre, ← e → trocam, Esc fecha e o foco volta à miniatura.
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { GALERIA } from "@/conteudo/historia";
import { Aviso } from "./Aviso";
import { Dialogo } from "./Dialogo";

// Distância da tela em que a miniatura começa a carregar: perto o bastante para a foto estar pronta ao rolar.
const MARGEM_DE_CARGA = "200px";
const TAMANHOS_DA_MINIATURA = "(min-width: 60rem) 18rem, 33vw";

/** A foto do visualizador, com a mensagem de erro por forma e palavra se o arquivo não carregar. */
function FotoAmpliada({ indice }: { indice: number }) {
  const foto = GALERIA[indice];
  const [falhou, setFalhou] = useState(false);
  return (
    <figure className="galeria-ampliada">
      {falhou ? (
        <Aviso tipo="erro">Não foi possível carregar esta foto. Feche e tente de novo.</Aviso>
      ) : (
        <Image
          className="galeria-foto"
          src={foto.src}
          alt={foto.alt}
          width={foto.largura}
          height={foto.altura}
          sizes="(min-width: 60rem) 40rem, 100vw"
          onError={() => setFalhou(true)}
        />
      )}
      <figcaption aria-live="polite" className="nota">
        Foto {indice + 1} de {GALERIA.length}
      </figcaption>
    </figure>
  );
}

function Visualizador({
  indice,
  aoMudar,
  aoFechar,
}: {
  indice: number;
  aoMudar: (i: number) => void;
  aoFechar: () => void;
}) {
  const total = GALERIA.length;
  const anterior = () => aoMudar((indice + total - 1) % total);
  const proxima = () => aoMudar((indice + 1) % total);
  const setas = useRef({ anterior, proxima });
  useEffect(() => {
    setas.current = { anterior, proxima };
  });
  useEffect(() => {
    function aoApertar(e: KeyboardEvent) {
      if (e.key === "ArrowLeft") setas.current.anterior();
      else if (e.key === "ArrowRight") setas.current.proxima();
    }
    document.addEventListener("keydown", aoApertar);
    return () => document.removeEventListener("keydown", aoApertar);
  }, []);

  return (
    <Dialogo titulo={GALERIA[indice].legenda} aoFechar={aoFechar}>
      <FotoAmpliada key={indice} indice={indice} />
      <div className="acoes">
        <button type="button" className="botao botao-secundario" onClick={anterior}>
          <span aria-hidden="true">← </span>Anterior
        </button>
        <button type="button" className="botao botao-secundario" onClick={proxima}>
          Próxima<span aria-hidden="true"> →</span>
        </button>
        <button type="button" className="botao botao-principal" onClick={aoFechar}>
          Fechar
        </button>
      </div>
    </Dialogo>
  );
}

/** A miniatura só monta a imagem quando chega perto da tela (A26: a home não baixa as 18 fotos de uma vez). Sem
 * IntersectionObserver (testes, navegador muito antigo), monta na hora. O botão e o espaço (aspect-ratio) ficam sempre. */
function Miniatura({
  foto,
  aoAbrir,
  registrar,
}: {
  foto: (typeof GALERIA)[number];
  aoAbrir: () => void;
  registrar: (el: HTMLButtonElement | null) => void;
}) {
  const botao = useRef<HTMLButtonElement | null>(null);
  const [perto, setPerto] = useState(false);
  useEffect(() => {
    const el = botao.current;
    if (!el || typeof IntersectionObserver === "undefined") {
      setPerto(true);
      return;
    }
    const observador = new IntersectionObserver(
      (entradas) => {
        if (entradas.some((e) => e.isIntersecting)) {
          setPerto(true);
          observador.disconnect();
        }
      },
      { rootMargin: MARGEM_DE_CARGA },
    );
    observador.observe(el);
    return () => observador.disconnect();
  }, []);
  return (
    <button
      ref={(el) => {
        botao.current = el;
        registrar(el);
      }}
      type="button"
      className="galeria-miniatura"
      aria-haspopup="dialog"
      aria-label={`Ampliar foto: ${foto.legenda}`}
      onClick={aoAbrir}
    >
      {perto && (
        <Image
          className="galeria-foto"
          src={foto.src}
          alt={foto.alt}
          width={foto.largura}
          height={foto.altura}
          sizes={TAMANHOS_DA_MINIATURA}
          loading="lazy"
        />
      )}
    </button>
  );
}

export function Galeria() {
  const [aberta, setAberta] = useState<number | null>(null);
  const ultima = useRef(0);
  const miniaturas = useRef<(HTMLButtonElement | null)[]>([]);

  function abrir(i: number) {
    ultima.current = i;
    setAberta(i);
  }
  const mudar = abrir;
  // Fechou: o foco volta à miniatura da foto em que o visualizador estava (o Dialogo devolve à que abriu).
  const fechada = aberta === null;
  const jaAbriu = useRef(false);
  useEffect(() => {
    if (!fechada) jaAbriu.current = true;
    else if (jaAbriu.current) miniaturas.current[ultima.current]?.focus(); // só ao fechar, nunca na carga da página
  }, [fechada]);

  return (
    <section id="galeria" aria-labelledby="t-galeria" className="secao">
      <p className="sobretitulo">Da cadeira</p>
      <h2 id="t-galeria">Do social ao degradê navalhado</h2>
      <p className="subtitulo-secao">
        Toque numa foto para ver inteira. Trabalho de verdade, feito na cadeira da Altas Horas.
      </p>
      <ul className="galeria-grade">
        {GALERIA.map((f, i) => (
          <li key={f.src}>
            <Miniatura
              foto={f}
              aoAbrir={() => abrir(i)}
              registrar={(el) => {
                miniaturas.current[i] = el;
              }}
            />
          </li>
        ))}
      </ul>
      {aberta !== null && <Visualizador indice={aberta} aoMudar={mudar} aoFechar={() => setAberta(null)} />}
    </section>
  );
}
