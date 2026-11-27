import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";
import { AvisoDaHome } from "@/componentes/AvisoDaHome";
import { BarbeiroDaHome } from "@/componentes/BarbeiroDaHome";
import { LinkBotao } from "@/componentes/Botao";
import { Galeria } from "@/componentes/Galeria";
import { AlcanceDaHome, ServicosDaHome } from "@/componentes/ListaDeServicos";
import { ComoMarcar } from "@/componentes/ComoMarcar";
import { Trajetoria } from "@/componentes/SecoesDaHome";
import { IMAGENS, SUBTITULO_DA_HOME, TITULO_DA_HOME } from "@/conteudo/historia";

// Ordem da home (Fatia 16, F3): herói enxuto (promessa, um botão e o link dos serviços), serviços, "Como marcar"
// (Três jeitos + Antes de pagar), barbeiro, galeria, Trajetória e alcance; "Onde estamos" é o rodapé do layout.
// O herói fica sem foto (decisão do Kevin de 27/11): a porta da barbearia continua no site, na galeria (até o F5/F6).
// Quem somos (F4) e Onde estamos na home (F6) mudam a ordem nos próximos PRs.
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
      <ServicosDaHome />
      <ComoMarcar />
      <BarbeiroDaHome />
      <Galeria />
      <Trajetoria />
      <AlcanceDaHome />
    </>
  );
}
