"use client";
// Seções interativas da home (Main.dc.html): Trajetória (ouvir, linha do tempo e prêmios) e o link do WhatsApp de "Como marcar".
// A galeria mora em Galeria.tsx. Tudo por teclado.
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { api, chamar } from "@/api/cliente";
import { HISTORIA, IMAGENS, MARCOS } from "@/conteudo/historia";
import { linkDoWhatsapp } from "./ContatoWhatsapp";
import { Icone } from "./Icone";

/** "Ou abrir o WhatsApp direto", no cartão 3 de "Como marcar" (Fatia 16, F3; spec 09, TJ-5). O número é público e vem
 * só de GET /barbeiros (6D). Sem número, o link some e o resto do cartão fica. */
export function WhatsappDireto() {
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
      className="link-solto nota"
      aria-label="Ou abrir o WhatsApp direto (abre o WhatsApp)"
    >
      Ou abrir o WhatsApp direto
    </a>
  );
}

// Links antigos (/#historia, /#barbeiro, /#trajetoria) e o clique em qualquer âncora da home: NavegacaoPorAncora.tsx
// (no layout, para valer também vindo de outra rota). `#trajetoria` continua como âncora dentro da seção, para funcionar
// mesmo antes do JavaScript.
export { HASHES_DE_QUEM_SOMOS } from "./ancoras";

const AUDIO_OPUS = "/assets/audio/historia.opus";
const AUDIO_MP3 = "/assets/audio/historia.mp3";
const AUDIO_VTT = "/assets/audio/historia.vtt";
/** Duração real do arquivo versionado (último cue 00:01:12.819; opus ~73 s). */
const DURACAO_DA_NARRACAO = "1 min 13 s";
/** WebKit às vezes baixa o Opus e não dispara erro nem playing; após este prazo tentamos o MP3. */
export const TEMPO_LIMITE_DO_AUDIO_MS = 8_000;

export function Trajetoria() {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [falando, setFalando] = useState(false);
  const [carregando, setCarregando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [fontesNoAr, setFontesNoAr] = useState(false);
  const [oferecerLeitura, setOferecerLeitura] = useState(false);
  const [leituraPeloAparelho, setLeituraPeloAparelho] = useState(false);
  const [marco, setMarco] = useState(1);
  const tentouMp3 = useRef(false);
  const trocandoFonte = useRef(false);
  const tempoLimite = useRef<ReturnType<typeof setTimeout> | null>(null);
  const aguardandoPlaying = useRef(false);

  function limparTempoLimite() {
    if (tempoLimite.current !== null) {
      clearTimeout(tempoLimite.current);
      tempoLimite.current = null;
    }
    aguardandoPlaying.current = false;
  }

  useEffect(
    () => () => {
      if (tempoLimite.current !== null) {
        clearTimeout(tempoLimite.current);
        tempoLimite.current = null;
      }
      aguardandoPlaying.current = false;
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

  function eAbortError(erro: unknown) {
    return (
      (typeof DOMException !== "undefined" && erro instanceof DOMException && erro.name === "AbortError") ||
      (typeof erro === "object" && erro !== null && "name" in erro && (erro as { name: string }).name === "AbortError")
    );
  }

  function avisarErroDeAudio() {
    limparTempoLimite();
    trocandoFonte.current = false;
    setCarregando(false);
    setFalando(false);
    setOferecerLeitura(true);
    setAviso("Não deu para carregar o áudio. Ler com a voz do aparelho?");
  }

  function tentarMp3(audio: HTMLAudioElement) {
    tentouMp3.current = true;
    trocandoFonte.current = true;
    audio.src = AUDIO_MP3;
    audio.load();
    tocar(audio);
  }

  function aoErroDoAudio(audio: HTMLAudioElement | null) {
    if (!audio) {
      avisarErroDeAudio();
      return;
    }
    const fonte = audio.currentSrc;
    const opusFalhou = !tentouMp3.current && (fonte.includes("historia.opus") || fonte === "");
    if (opusFalhou) {
      tentarMp3(audio);
      return;
    }
    avisarErroDeAudio();
  }

  function armarTempoLimite(audio: HTMLAudioElement) {
    if (tempoLimite.current !== null) {
      clearTimeout(tempoLimite.current);
      tempoLimite.current = null;
    }
    aguardandoPlaying.current = true;
    tempoLimite.current = setTimeout(() => {
      tempoLimite.current = null;
      if (!aguardandoPlaying.current) return;
      if (!tentouMp3.current) {
        tentarMp3(audio);
        return;
      }
      avisarErroDeAudio();
    }, TEMPO_LIMITE_DO_AUDIO_MS);
  }

  function tocar(audio: HTMLAudioElement) {
    setCarregando(true);
    setOferecerLeitura(false);
    setAviso("Carregando o áudio…");
    armarTempoLimite(audio);
    const tentativa = audio.play();
    if (tentativa && typeof tentativa.then === "function") {
      tentativa.catch((erro: unknown) => {
        if (eAbortError(erro)) {
          // pause() ou troca de fonte interrompe o play(); erro de rede/decodificação vai no onError.
          if (trocandoFonte.current) return;
          if (audio.error) {
            aoErroDoAudio(audio);
            return;
          }
          setCarregando(false);
          setFalando(false);
          return;
        }
        aoErroDoAudio(audio);
      });
      return;
    }
  }

  function iniciarLeituraPeloAparelho() {
    setLeituraPeloAparelho(true);
    setOferecerLeitura(false);
    falarComAparelho();
  }

  function alternarVoz() {
    if (leituraPeloAparelho) {
      falarComAparelho();
      return;
    }
    if (falando || carregando) {
      limparTempoLimite();
      audioRef.current?.pause();
      setCarregando(false);
      setFalando(false);
      setAviso("Leitura parada.");
      return;
    }
    // Fontes, legenda e controles nativos só entram no DOM ao apertar Ouvir
    // (VZ-1: o .vtt com default era baixado no carregamento; controles vazios não tocavam).
    if (!fontesNoAr) {
      flushSync(() => setFontesNoAr(true));
    }
    const audio = audioRef.current;
    if (!audio) {
      setOferecerLeitura(true);
      setAviso("Não deu para carregar o áudio. Ler com a voz do aparelho?");
      return;
    }
    tocar(audio);
  }

  // "01 · Quem somos" (Fatia 16, F4): a história do Eduardo contada uma vez só. O bloco "Barbeiro", a citação e o bloco
  // dos prêmios saíram; as fotos dos prêmios aparecem no marco 2017 da linha do tempo.
  return (
    <section id="quem-somos" aria-labelledby="t-quem-somos" className="secao">
      <div id="trajetoria" className="duas-colunas">
        <figure className="retrato">
          <Image src={IMAGENS.retrato.src} alt={IMAGENS.retrato.alt} width={360} height={360} />
        </figure>
        <div>
          <p className="sobretitulo">01 · Quem somos</p>
          <h2 id="t-quem-somos" tabIndex={-1}>
            Quase três décadas na mesma cadeira
          </h2>
          <div className="player-trajetoria">
            <p>
              <button type="button" className="botao botao-secundario" onClick={alternarVoz}>
                <Icone nome="voz" /> {falando || carregando ? "Pausar" : `Ouvir a trajetória · ${DURACAO_DA_NARRACAO}`}
              </button>
            </p>
            {fontesNoAr && (
              <audio
                ref={audioRef}
                controls
                preload="none"
                onPlaying={() => {
                  limparTempoLimite();
                  trocandoFonte.current = false;
                  setCarregando(false);
                  setFalando(true);
                  setAviso("Lendo a trajetória em voz alta.");
                }}
                onPause={() => {
                  const el = audioRef.current;
                  if (el?.ended || el?.error || trocandoFonte.current) return;
                  limparTempoLimite();
                  setCarregando(false);
                  setFalando(false);
                  setAviso("Leitura parada.");
                }}
                onEnded={() => {
                  limparTempoLimite();
                  setCarregando(false);
                  setFalando(false);
                  setAviso("Leitura terminada.");
                }}
                onError={() => aoErroDoAudio(audioRef.current)}
              >
                <source src={AUDIO_OPUS} type="audio/ogg; codecs=opus" />
                <source src={AUDIO_MP3} type="audio/mpeg" />
                <track kind="captions" srcLang="pt-BR" label="Português" src={AUDIO_VTT} default />
              </audio>
            )}
            {oferecerLeitura && (
              <p>
                <button type="button" className="botao botao-secundario" onClick={iniciarLeituraPeloAparelho}>
                  Ler com a voz do aparelho
                </button>
              </p>
            )}
            <p role="status" aria-live="polite" className="nota">
              {aviso}
            </p>
          </div>
          {HISTORIA.map((p) => (
            <p key={p}>{p}</p>
          ))}
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
      {MARCOS[marco].ano === "2017" && (
        <ul className="grade-fotos">
          {IMAGENS.premios.map((p) => (
            <li key={p.src}>
              <figure className="foto-inteira">
                <Image src={p.src} alt={p.alt} width={480} height={480} />
              </figure>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
