"use client";
// Seções interativas da home (Main.dc.html): Trajetória (ouvir, linha do tempo e prêmios), como funciona e dúvidas.
// A galeria mora em Galeria.tsx. Tudo por teclado.
import Image from "next/image";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { DUVIDAS, HISTORIA, IMAGENS, MARCOS } from "@/conteudo/historia";
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

/** Links antigos para `/#historia` (já divulgados) levam à Trajetória (E1): troca o hash e rola até o bloco. */
function useRedirecionarHistoria() {
  useEffect(() => {
    function ir() {
      if (window.location.hash !== "#historia") return;
      window.history.replaceState(null, "", "#trajetoria");
      document.getElementById("trajetoria")?.scrollIntoView();
    }
    ir();
    window.addEventListener("hashchange", ir);
    return () => window.removeEventListener("hashchange", ir);
  }, []);
}

export function Trajetoria() {
  const [falando, setFalando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [marco, setMarco] = useState(1);
  useRedirecionarHistoria();

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
    setAviso("Lendo a trajetória em voz alta.");
  }

  return (
    <section id="trajetoria" aria-labelledby="t-trajetoria" className="secao">
      <div className="duas-colunas">
        <figure className="retrato">
          <Image src={IMAGENS.retrato.src} alt={IMAGENS.retrato.alt} width={360} height={360} />
        </figure>
        <div>
          <p className="sobretitulo">Trajetória</p>
          <h2 id="t-trajetoria">Quase três décadas na mesma cadeira</h2>
          {HISTORIA.map((p) => (
            <p key={p}>{p}</p>
          ))}
          <p>
            <button type="button" className="botao botao-secundario" onClick={alternarVoz} aria-pressed={falando}>
              <Icone nome="voz" /> {falando ? "Parar leitura" : "Ouvir a trajetória"}
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
      <h3>Reconhecido duas vezes em 2017</h3>
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
