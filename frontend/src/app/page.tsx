import Image from "next/image";
import { Suspense } from "react";
import { AvisoDaHome } from "@/componentes/AvisoDaHome";
import { LinkBotao } from "@/componentes/Botao";
import { AlcanceDaHome, ServicosDaHome } from "@/componentes/ListaDeServicos";
import { HISTORIA, IMAGENS, SUBTITULO_DA_HOME, TITULO_DA_HOME } from "@/conteudo/historia";

export default function Home() {
  return (
    <>
      <Suspense>
        <AvisoDaHome />
      </Suspense>
      <Image src={IMAGENS.logo.src} alt={IMAGENS.logo.alt} width={160} height={160} className="logo" priority />
      <h1>{TITULO_DA_HOME}</h1>
      <p className="destaque">{SUBTITULO_DA_HOME}</p>
      <p>
        <LinkBotao href="/agendar" variante="principal">
          Agendar horário
        </LinkBotao>
      </p>
      <section aria-labelledby="historia-titulo">
        <h2 id="historia-titulo">A história</h2>
        {HISTORIA.map((p) => (
          <p key={p}>{p}</p>
        ))}
        <Image src={IMAGENS.fachada.src} alt={IMAGENS.fachada.alt} width={520} height={390} />
        {IMAGENS.premios.map((p) => (
          <Image key={p.src} src={p.src} alt={p.alt} width={360} height={360} />
        ))}
      </section>
      <ServicosDaHome />
      <AlcanceDaHome />
    </>
  );
}
