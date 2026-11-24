"use client";
// Bloco "Barbeiro" da home (Fatia 12, A9 e A10): foto, nome profissional, WhatsApp e "Agendar com <nome>".
// Os dados vêm só de GET /barbeiros (o WhatsApp é público, só dígitos, D122); nenhum número escrito no código.
// A foto é a do Eduardo (docs/assets): outro barbeiro, quando houver (Fatia 10), aparece sem foto.
import Image from "next/image";
import Link from "next/link";
import { api, chamar } from "@/api/cliente";
import { useConsulta } from "@/api/hooks";
import { IMAGENS } from "@/conteudo/historia";
import { linkDoWhatsapp } from "./ContatoWhatsapp";
import { EstadoDaConsulta } from "./EstadoDaConsulta";
import { Icone } from "./Icone";

const semAcento = (t: string) => t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
const ehOEduardo = (nome: string) => semAcento(nome).includes("eduardo candido");

export function BarbeiroDaHome() {
  const consulta = useConsulta(() => chamar(api().GET("/barbeiros")), []);
  return (
    <section id="barbeiro" aria-labelledby="barbeiro-titulo" className="secao">
      <p className="sobretitulo">Barbeiro</p>
      <h2 id="barbeiro-titulo">Quem corta o seu cabelo</h2>
      <EstadoDaConsulta consulta={consulta}>
        {(barbeiros) =>
          barbeiros.length === 0 ? (
            <p>Sem barbeiro no momento.</p>
          ) : (
            <ul className="grade-barbeiros">
              {barbeiros.map((b) => {
                const whatsapp = b.whatsapp ? linkDoWhatsapp(b.whatsapp) : undefined;
                const primeiroNome = b.nomeExibicao.split(" ")[0];
                return (
                  <li key={b.id} className="cartao cartao-barbeiro">
                    {ehOEduardo(b.nomeExibicao) && (
                      <Image
                        className="foto-barbeiro"
                        src={IMAGENS.barbeiro.src}
                        alt={IMAGENS.barbeiro.alt}
                        width={IMAGENS.barbeiro.largura}
                        height={IMAGENS.barbeiro.altura}
                        sizes="(min-width: 60rem) 12rem, 8rem"
                      />
                    )}
                    <div className="cartao-barbeiro-texto">
                      <h3>{b.nomeExibicao}</h3>
                      {ehOEduardo(b.nomeExibicao) && (
                        <p className="destaque">Barbeiro e professor, na cadeira desde 1995.</p>
                      )}
                      <p className="acoes">
                        <Link href={`/agendar?barbeiroId=${b.id}`} className="botao botao-principal">
                          Agendar com {primeiroNome}
                        </Link>
                        {whatsapp && (
                          <a
                            href={whatsapp}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="botao botao-secundario"
                            aria-label={`Falar com ${primeiroNome} no WhatsApp (abre o WhatsApp)`}
                          >
                            <Icone nome="conversa" /> WhatsApp
                          </a>
                        )}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )
        }
      </EstadoDaConsulta>
    </section>
  );
}
