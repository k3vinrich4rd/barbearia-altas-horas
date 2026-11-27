"use client";
// Seções interativas da home (Main.dc.html): Trajetória (ouvir, linha do tempo e prêmios), como funciona e dúvidas.
// A galeria mora em Galeria.tsx. Tudo por teclado.
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { DUVIDAS, HISTORIA, IMAGENS, MARCOS, PASSOS_DA_FAIXA } from "@/conteudo/historia";
import { linkDoWhatsapp } from "./ContatoWhatsapp";
import { Icone } from "./Icone";

/** "Falar no WhatsApp" do herói: o número é público e vem só de GET /barbeiros (6D). Sem número, o botão some. */
export function WhatsappDoHeroi() {
  const [numero, setNumero] = useState<string | undefined>();
  useEffect(() => {
    let vivo = true;
    chamar(api().GET("/barbeiros")).then(
      (lista) => vivo && setNumero(lista.find((b) => Boolean(b.whatsapp))?.whatsapp ?? undefined),
      () => undefined,
    );
    return () => {
      vivo = false;
    };
  }, []);
  const link = numero ? linkDoWhatsapp(numero) : undefined;
  if (!link) return null;
  return (
    <a
      href={link}
      target="_blank"
      rel="noopener noreferrer"
      className="botao botao-secundario"
      aria-label="Falar no WhatsApp (abre o WhatsApp)"
    >
      <Icone nome="conversa" /> Falar no WhatsApp
    </a>
  );
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

const AUDIO_OPUS = "/assets/audio/historia.opus";
const AUDIO_MP3 = "/assets/audio/historia.mp3";
const AUDIO_VTT = "/assets/audio/historia.vtt";
/** Duração real do arquivo versionado (último cue 00:01:12.819; opus ~73 s). */
const DURACAO_DA_NARRACAO = "1 min 13 s";

export function Trajetoria() {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [falando, setFalando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [usarFala, setUsarFala] = useState(false);
  const [marco, setMarco] = useState(1);
  useRedirecionarHistoria();

  useEffect(
    () => () => {
      window.speechSynthesis?.cancel();
      audioRef.current?.pause();
    },
    [],
  );

  function falarComAparelho(manterAviso = false) {
    const voz = window.speechSynthesis;
    if (!voz) {
      setAviso(
        manterAviso
          ? "Não deu para carregar o áudio. Seu navegador não tem leitura em voz alta."
          : "Seu navegador não tem leitura em voz alta.",
      );
      return;
    }
    if (falando) {
      voz.cancel();
      setFalando(false);
      if (!manterAviso) setAviso("Leitura parada.");
      return;
    }
    const fala = new SpeechSynthesisUtterance(HISTORIA.join(" "));
    fala.lang = "pt-BR";
    fala.onend = () => {
      setFalando(false);
      if (!manterAviso) setAviso("Leitura terminada.");
    };
    voz.speak(fala);
    setFalando(true);
    if (!manterAviso) setAviso("Lendo a trajetória em voz alta.");
  }

  function alternarVoz() {
    if (usarFala) {
      falarComAparelho();
      return;
    }
    const audio = audioRef.current;
    if (!audio) {
      setUsarFala(true);
      falarComAparelho();
      return;
    }
    if (falando) {
      audio.pause();
      setFalando(false);
      setAviso("Leitura parada.");
      return;
    }
    const tentativa = audio.play();
    if (tentativa && typeof tentativa.then === "function") {
      tentativa
        .then(() => {
          setFalando(true);
          setAviso("Lendo a trajetória em voz alta.");
        })
        .catch(() => {
          setUsarFala(true);
          setAviso("Não deu para carregar o áudio. Ler com a voz do aparelho?");
          falarComAparelho(true);
        });
      return;
    }
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
          <div className="player-trajetoria">
            <p>
              <button type="button" className="botao botao-secundario" onClick={alternarVoz} aria-pressed={falando}>
                <Icone nome="voz" /> {falando ? "Pausar" : `Ouvir a trajetória · ${DURACAO_DA_NARRACAO}`}
              </button>
            </p>
            <audio
              ref={audioRef}
              controls
              preload="none"
              onPlay={() => setFalando(true)}
              onPause={() => setFalando(false)}
              onEnded={() => {
                setFalando(false);
                setAviso("Leitura terminada.");
              }}
            >
              <source src={AUDIO_OPUS} type="audio/opus" />
              <source src={AUDIO_MP3} type="audio/mpeg" />
              <track kind="captions" srcLang="pt-BR" label="Português" src={AUDIO_VTT} default />
            </audio>
            <p role="status" className="nota">
              {aviso}
            </p>
          </div>
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

/** Faixa "Como funciona" dentro do herói (D-11): três passos de uma linha. O prazo do pagamento NÃO tem número aqui:
 * ele só aparece onde existe `expiraEm` (tela de pagamento), porque é configurável de 3 a 60 min (D121, A19). */
export function FaixaComoFunciona() {
  return (
    <ol className="faixa-passos" aria-label="Como funciona">
      {PASSOS_DA_FAIXA.map((p) => (
        <li key={p.titulo}>
          <strong>{p.titulo}</strong>
          <span className="destaque">{p.texto}</span>
        </li>
      ))}
    </ol>
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
