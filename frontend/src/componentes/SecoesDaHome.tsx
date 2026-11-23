"use client";
// Seções interativas da home (Main.dc.html): ouvir a história, linha do tempo, galeria e dúvidas. Tudo por teclado.
import Image from "next/image";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { DUVIDAS, GALERIA, HISTORIA, IMAGENS, MARCOS } from "@/conteudo/historia";
import { type BarbeiroComWhatsapp, ContatoWhatsapp } from "./ContatoWhatsapp";
import { Icone } from "./Icone";

/** "Prefere combinar pelo WhatsApp?": o número é público e vem da API (6D). Sem número, o bloco some. */
export function WhatsappDaHome() {
  const [barbeiro, setBarbeiro] = useState<BarbeiroComWhatsapp | undefined>();
  useEffect(() => {
    let vivo = true;
    chamar(api().GET("/barbeiros")).then(
      (lista) => vivo && setBarbeiro(lista.find((b) => Boolean(b.whatsapp))),
      () => undefined,
    );
    return () => {
      vivo = false;
    };
  }, []);
  return <ContatoWhatsapp barbeiro={barbeiro} />;
}

export function Historia() {
  const [falando, setFalando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [marco, setMarco] = useState(1);

  useEffect(() => () => window.speechSynthesis?.cancel(), []);

  function alternarVoz() {
    const voz = window.speechSynthesis;
    if (!voz) {
      setAviso("Seu navegador não tem leitura em voz alta.");
      return;
    }
    if (falando) {
      voz.cancel();
      setFalando(false);
      setAviso("Leitura parada.");
      return;
    }
    const fala = new SpeechSynthesisUtterance(HISTORIA.join(" "));
    fala.lang = "pt-BR";
    fala.onend = () => {
      setFalando(false);
      setAviso("Leitura terminada.");
    };
    voz.speak(fala);
    setFalando(true);
    setAviso("Lendo a história em voz alta.");
  }

  return (
    <section id="historia" aria-labelledby="t-historia" className="secao">
      <div className="duas-colunas">
        <figure className="retrato">
          <Image src={IMAGENS.retrato.src} alt={IMAGENS.retrato.alt} width={360} height={360} />
        </figure>
        <div>
          <p className="sobretitulo">A história</p>
          <h2 id="t-historia">Quase três décadas na mesma cadeira</h2>
          {HISTORIA.map((p) => (
            <p key={p}>{p}</p>
          ))}
          <p>
            <button type="button" className="botao botao-secundario" onClick={alternarVoz} aria-pressed={falando}>
              <Icone nome="voz" /> {falando ? "Parar leitura" : "Ouvir a história"}
            </button>
          </p>
          <p role="status" className="nota">
            {aviso}
          </p>
          <blockquote>
            <p>“Amor na ponta da tesoura!”</p>
            <footer>Eduardo Candido, ao receber o Prêmio Barbeiros do Brasil</footer>
          </blockquote>
        </div>
      </div>
      <h3>Linha do tempo</h3>
      <div role="group" aria-label="Escolha um ano da linha do tempo" className="linha-do-tempo">
        {MARCOS.map((m, i) => (
          <button
            key={m.ano}
            type="button"
            aria-pressed={marco === i}
            aria-controls="marco-detalhe"
            onClick={() => setMarco(i)}
            className="marco"
          >
            <span className="marco-ano">{m.ano}</span>
            <span>{m.titulo}</span>
          </button>
        ))}
      </div>
      <p id="marco-detalhe" aria-live="polite">
        <strong>
          {MARCOS[marco].ano} · {MARCOS[marco].titulo}.
        </strong>{" "}
        {MARCOS[marco].texto}
      </p>
    </section>
  );
}

export function Premios() {
  return (
    <section id="premios" aria-labelledby="t-premios" className="secao">
      <p className="sobretitulo">Trajetória e prêmios</p>
      <h2 id="t-premios">Reconhecido duas vezes em 2017</h2>
      <p>
        Vinte anos depois de abrir a barbearia, o trabalho do Eduardo foi premiado duas vezes no mesmo ano. Os troféus
        vieram para Osasco e hoje ficam na prateleira do salão, à vista de quem senta na cadeira.
      </p>
      <ul className="grade-fotos">
        {IMAGENS.premios.map((p, i) => (
          <li key={p.src} className="cartao">
            <figure className="foto-inteira">
              <Image src={p.src} alt={p.alt} width={480} height={480} />
              <figcaption>
                <strong>{i === 0 ? "Prêmio Barbeiros do Brasil" : "Prêmio Master Barber · Êxito Profissional"}</strong>
                <span className="destaque">
                  {i === 0
                    ? "2017 · o troféu com a barra de barbeiro que ele fez questão de trazer para Osasco"
                    : "2017 · reconhecimento pela trajetória e pelo êxito profissional"}
                </span>
              </figcaption>
            </figure>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function ComoFunciona() {
  const passos = [
    ["01", "Escolha", "O serviço e um horário livre, em até 30 dias, com pelo menos 2 horas de antecedência."],
    [
      "02",
      "Pague",
      "Por Pix ou cartão, no prazo que o contador mostra. Enquanto isso, o horário fica reservado só para você.",
    ],
    [
      "03",
      "Confirmado",
      "O pagamento confirma o horário na agenda do Eduardo. Dois clientes nunca ficam com o mesmo horário.",
    ],
  ];
  return (
    <section id="como-funciona" aria-labelledby="t-como" className="secao">
      <p className="sobretitulo">Como funciona</p>
      <h2 id="t-como">Três passos, e a cadeira é sua</h2>
      <ol className="passos">
        {passos.map(([n, titulo, texto]) => (
          <li key={n} className="cartao">
            <span aria-hidden="true" className="passo-numero">
              {n}
            </span>
            <h3>{titulo}</h3>
            <p>{texto}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

export function Galeria() {
  const [i, setI] = useState(0);
  const atual = GALERIA[i];
  return (
    <section id="galeria" aria-labelledby="t-galeria" className="secao">
      <p className="sobretitulo">Da cadeira</p>
      <h2 id="t-galeria">Do social ao degradê navalhado</h2>
      <figure className="foto-inteira galeria-grande">
        <Image src={atual.src} alt={atual.alt} width={640} height={640} />
        <figcaption aria-live="polite">
          <strong>{atual.legenda}</strong>
          <span className="destaque">
            Foto {i + 1} de {GALERIA.length}
          </span>
        </figcaption>
      </figure>
      <div className="acoes">
        <button
          type="button"
          className="botao botao-secundario"
          aria-label="Foto anterior"
          onClick={() => setI((i + GALERIA.length - 1) % GALERIA.length)}
        >
          Anterior
        </button>
        <button
          type="button"
          className="botao botao-secundario"
          aria-label="Próxima foto"
          onClick={() => setI((i + 1) % GALERIA.length)}
        >
          Próxima
        </button>
      </div>
      <div role="group" aria-label="Miniaturas da galeria" className="miniaturas">
        {GALERIA.map((f, n) => (
          <button
            key={f.src}
            type="button"
            aria-pressed={n === i}
            aria-label={`Ver foto: ${f.legenda}`}
            onClick={() => setI(n)}
            className="miniatura"
          >
            <Image src={f.src} alt="" width={96} height={96} />
          </button>
        ))}
      </div>
    </section>
  );
}

export function Duvidas() {
  return (
    <section id="duvidas" aria-labelledby="t-duvidas" className="secao">
      <p className="sobretitulo">Dúvidas</p>
      <h2 id="t-duvidas">Antes de agendar</h2>
      {DUVIDAS.map((d) => (
        <details key={d.pergunta} className="duvida">
          <summary>
            {d.pergunta}
            <span aria-hidden="true" className="mais">
              +
            </span>
          </summary>
          <p>{d.resposta}</p>
        </details>
      ))}
    </section>
  );
}

export function OndeEstamos() {
  return (
    <section id="onde" aria-labelledby="t-onde" className="secao">
      <h2 id="t-onde">Onde estamos</h2>
      <address>
        R. São Judas Tadeu, 313
        <br />
        Jd. Bonança · Osasco/SP
      </address>
      <h3>Horário</h3>
      <p>Terça a sábado, das 7h às 20h</p>
      <p>Almoço das 12h às 13h · domingo e segunda: fechado</p>
      <h3>Instagram</h3>
      <p>
        <a href="https://www.instagram.com/eduardo_candido_barber/" rel="noopener noreferrer">
          @eduardo_candido_barber
        </a>
      </p>
    </section>
  );
}
