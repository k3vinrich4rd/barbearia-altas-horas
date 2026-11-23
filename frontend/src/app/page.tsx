import Image from "next/image";
import { Suspense } from "react";
import { AvisoDaHome } from "@/componentes/AvisoDaHome";
import { LinkBotao } from "@/componentes/Botao";
import { AlcanceDaHome, ServicosDaHome } from "@/componentes/ListaDeServicos";
import {
  ComoFunciona,
  Duvidas,
  Galeria,
  Historia,
  Premios,
  WhatsappDaHome,
} from "@/componentes/SecoesDaHome";
import { FATOS_DA_HOME, IMAGENS, SUBTITULO_DA_HOME, TITULO_DA_HOME } from "@/conteudo/historia";

export default function Home() {
  return (
    <>
      <Suspense>
        <AvisoDaHome />
      </Suspense>
      <section id="inicio" aria-labelledby="t-inicio" className="heroi">
        <div>
          <p className="sobretitulo">
            <span aria-hidden="true">★ 19 ✂ 97 ★ · </span>Jardim Bonança, Osasco, desde 1997
          </p>
          <h1 id="t-inicio" className="tipo-titulo-hero">
            {TITULO_DA_HOME}
          </h1>
          <p className="destaque">{SUBTITULO_DA_HOME}</p>
          <p className="acoes">
            <LinkBotao href="/agendar" variante="principal">
              Agendar horário
            </LinkBotao>
            <LinkBotao href="/servicos">Serviços e preços</LinkBotao>
          </p>
          <WhatsappDaHome />
          <ul className="fatos">
            {FATOS_DA_HOME.map((f) => (
              <li key={f.titulo}>
                <strong>{f.titulo}</strong>
                <span className="destaque">{f.texto}</span>
              </li>
            ))}
          </ul>
        </div>
        <figure className="foto-fachada">
          <Image src={IMAGENS.fachada.src} alt={IMAGENS.fachada.alt} width={560} height={420} priority />
          <figcaption className="nota">A fachada, na R. São Judas Tadeu, 313</figcaption>
        </figure>
      </section>
      <div className="divisor" aria-hidden="true">
        <span className="selo">19 ✂ 97</span>
      </div>
      <Historia />
      <Premios />
      <ServicosDaHome />
      <ComoFunciona />
      <Galeria />
      <Duvidas />
      <AlcanceDaHome />
    </>
  );
}
