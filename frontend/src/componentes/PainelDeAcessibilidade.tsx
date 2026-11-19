"use client";
// Painel de acessibilidade do desenho (README, "Acessibilidade e interação"): texto maior/menor, tema claro,
// alto contraste e sem animação, com aria-pressed. Nada é gravado no navegador.
import { useState } from "react";

const PASSOS = [100, 110, 125, 150];

function aplicar(atributo: string, valor: string | undefined) {
  const html = document.documentElement;
  if (valor === undefined) delete html.dataset[atributo];
  else html.dataset[atributo] = valor;
}

export function PainelDeAcessibilidade() {
  const [passo, setPasso] = useState(0);
  const [claro, setClaro] = useState(false);
  const [contraste, setContraste] = useState(false);
  const [semAnimacao, setSemAnimacao] = useState(false);

  function mudarTexto(delta: number) {
    const novo = Math.min(PASSOS.length - 1, Math.max(0, passo + delta));
    setPasso(novo);
    // zoom escala tudo junto (os tamanhos vêm dos tokens, em px)
    document.body.style.zoom = String(PASSOS[novo] / 100);
  }

  return (
    <div role="region" aria-label="Recursos de acessibilidade" className="painel-a11y">
      <span className="painel-a11y-titulo">Acessibilidade</span>
      <button type="button" className="botao-mini" onClick={() => mudarTexto(-1)} disabled={passo === 0}>
        <span aria-hidden="true">A−</span>
        <span className="so-leitor">Diminuir o tamanho do texto</span>
      </button>
      <button type="button" className="botao-mini" onClick={() => mudarTexto(1)} disabled={passo === PASSOS.length - 1}>
        <span aria-hidden="true">A+</span>
        <span className="so-leitor">Aumentar o tamanho do texto</span>
      </button>
      <button
        type="button"
        className="botao-mini"
        aria-pressed={claro}
        onClick={() => {
          setClaro(!claro);
          aplicar("theme", claro ? "escuro" : "claro");
        }}
      >
        Tema claro
      </button>
      <button
        type="button"
        className="botao-mini"
        aria-pressed={contraste}
        onClick={() => {
          setContraste(!contraste);
          aplicar("contraste", contraste ? undefined : "alto");
        }}
      >
        Alto contraste
      </button>
      <button
        type="button"
        className="botao-mini"
        aria-pressed={semAnimacao}
        onClick={() => {
          setSemAnimacao(!semAnimacao);
          aplicar("movimento", semAnimacao ? undefined : "reduzido");
        }}
      >
        Sem animação
      </button>
    </div>
  );
}
