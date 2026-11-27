"use client";
// Rodapé do site (PDF, pág. 2): onde estamos, horário, Instagram e WhatsApp, e o crédito da agenda online.
// O WhatsApp é o número PÚBLICO do barbeiro, vindo de GET /barbeiros (Fatia 6D); sem número, a linha some.
// O mapa é só um link (sem mapa embutido, sem script de terceiros).
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { linkDoWhatsapp } from "./ContatoWhatsapp";
import { ehPaginaDeTarefa } from "./tipoDePagina";

export const ENDERECO_NO_MAPA =
  "https://www.google.com/maps/search/?api=1&query=" +
  encodeURIComponent("R. São Judas Tadeu, 313, Jardim Bonança, Osasco - SP");

/** "11906941689" vira "(11) 90694-1689"; com DDI 55, ele é tirado da exibição. */
export function formatarWhatsapp(numero: string): string {
  let d = numero.replace(/\D/g, "");
  if (d.length > 11 && d.startsWith("55")) d = d.slice(2);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return d;
}

function useWhatsappPublico(ativo: boolean) {
  const [numero, setNumero] = useState<string | undefined>();
  useEffect(() => {
    if (!ativo) return;
    let vivo = true;
    chamar(api().GET("/barbeiros")).then(
      (lista) => vivo && setNumero(lista.find((b) => Boolean(b.whatsapp))?.whatsapp ?? undefined),
      () => undefined,
    );
    return () => {
      vivo = false;
    };
  }, [ativo]);
  return numero;
}

export function Rodape() {
  // Fatia 16, F2 (A15): nas telas de tarefa o rodapé tem uma linha só; o WhatsApp fica no próprio agendamento.
  const enxuto = ehPaginaDeTarefa(usePathname());
  const numero = useWhatsappPublico(!enxuto);
  if (enxuto) {
    return (
      <footer className="rodape rodape-enxuto">
        <ul className="rodape-lista rodape-links">
          <li>
            <Link href="/#onde-estamos" className="link-solto">
              Onde estamos
            </Link>
          </li>
          <li>
            <Link href="/privacidade" className="link-solto">
              Política de privacidade
            </Link>
          </li>
        </ul>
      </footer>
    );
  }
  const link = numero ? linkDoWhatsapp(numero) : undefined;
  return (
    <footer className="rodape">
      <div className="rodape-colunas">
        <section id="onde-estamos" aria-labelledby="rodape-onde">
          <h2 id="rodape-onde" className="rodape-titulo" tabIndex={-1}>
            Onde estamos
          </h2>
          <address>
            R. São Judas Tadeu, 313
            <br />
            Jd. Bonança · Osasco/SP
          </address>
          <p>
            <a href={ENDERECO_NO_MAPA} target="_blank" rel="noopener noreferrer">
              Abrir no mapa (nova aba)
            </a>
          </p>
        </section>
        <section aria-labelledby="rodape-horario">
          <h2 id="rodape-horario" className="rodape-titulo">
            Horário de funcionamento
          </h2>
          <p>Terça a sábado, das 7h às 20h</p>
          <p>Almoço das 12h às 13h · domingo e segunda: fechado</p>
        </section>
        <section aria-labelledby="rodape-contato">
          <h2 id="rodape-contato" className="rodape-titulo">
            Contato
          </h2>
          <ul className="rodape-lista">
            <li>
              <a href="https://www.instagram.com/altashorascabeleireiros/" rel="noopener noreferrer">
                @altashorascabeleireiros
              </a>
            </li>
            <li>
              <a href="https://www.instagram.com/eduardo_candido_barber/" rel="noopener noreferrer">
                @eduardo_candido_barber
              </a>
            </li>
            {link && numero && (
              <li>
                <a href={link} target="_blank" rel="noopener noreferrer">
                  {formatarWhatsapp(numero)} <span className="so-leitor">(abre o WhatsApp)</span>
                </a>
              </li>
            )}
          </ul>
        </section>
      </div>
      <p className="rodape-credito">
        <span aria-hidden="true">★ 19 ✂ 97 ★ </span>
        <span className="etiqueta">Beta</span> Agenda online feita por Kevin Richard, filho do Eduardo.
      </p>
      <ul className="rodape-lista rodape-links">
        <li>
          <Link href="/privacidade" className="link-solto">
            Política de privacidade
          </Link>
        </li>
        <li>
          <a href="#conteudo" className="link-solto">
            Voltar ao topo
          </a>
        </li>
      </ul>
    </footer>
  );
}
