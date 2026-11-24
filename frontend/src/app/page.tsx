import Image from "next/image";
import { Suspense } from "react";
import { AvisoDaHome } from "@/componentes/AvisoDaHome";
import { BarbeiroDaHome } from "@/componentes/BarbeiroDaHome";
import { LinkBotao } from "@/componentes/Botao";
import { Galeria } from "@/componentes/Galeria";
import { AlcanceDaHome, ServicosDaHome } from "@/componentes/ListaDeServicos";
import { Duvidas, FaixaComoFunciona, Trajetoria, WhatsappDoHeroi } from "@/componentes/SecoesDaHome";
import { FATOS_DA_HOME, IMAGENS, SUBTITULO_DA_HOME, TITULO_DA_HOME } from "@/conteudo/historia";

// Ordem da home (Fatia 12, A1): a ordem em que o cliente decide. Herói (com a faixa "Como funciona"), serviços,
// barbeiro, galeria, Trajetória, de onde vêm os clientes, dúvidas; "Onde estamos" é o rodapé do layout.
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
            <WhatsappDoHeroi />
          </p>
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
        <FaixaComoFunciona />
      </section>
      <ServicosDaHome />
      <BarbeiroDaHome />
      <Galeria />
      <Trajetoria />
      <AlcanceDaHome />
      <Duvidas />
    </>
  );
}
