import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";
import { AvisoDaHome } from "@/componentes/AvisoDaHome";
import { LinkBotao } from "@/componentes/Botao";
import { Galeria } from "@/componentes/Galeria";
import { AlcanceDaHome, ServicosDaHome } from "@/componentes/ListaDeServicos";
import { ComoMarcar } from "@/componentes/ComoMarcar";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { IMAGENS, SUBTITULO_DA_HOME, TITULO_DA_HOME } from "@/conteudo/historia";

// Ordem da home (Fatia 16, A4): herói enxuto, "01 · Quem somos" (a Trajetória, com o barbeiro dentro), "02 · O que
// fazemos", "03 · Como marcar", a galeria (até o F5) e o alcance (só com dado); "Onde estamos" é o rodapé do layout (até o F6).
// O herói fica sem foto (decisão do Kevin de 27/11): a porta da barbearia continua no site, na galeria (até o F5/F6).
export default function Home() {
  return (
    <>
      <Suspense>
        <AvisoDaHome />
      </Suspense>
      <section id="inicio" aria-labelledby="t-inicio" className="heroi">
        <div>
          <p className="sobretitulo" aria-hidden="true">
            ★ 19 ✂ 97 ★
          </p>
          <h1 id="t-inicio" className="tipo-titulo-hero">
            {TITULO_DA_HOME}
          </h1>
          <p className="destaque">{SUBTITULO_DA_HOME}</p>
          <p className="acoes">
            <LinkBotao href="/agendar" variante="principal">
              Agendar horário
            </LinkBotao>
            <Link href="#servicos" className="link-solto">
              Ver serviços e preços
            </Link>
          </p>
        </div>
        <div className="selo-heroi">
          <Image src={IMAGENS.logo.src} alt="" width={320} height={320} />
        </div>
      </section>
      <Trajetoria />
      <ServicosDaHome />
      <ComoMarcar />
      <Galeria />
      <AlcanceDaHome />
    </>
  );
}
