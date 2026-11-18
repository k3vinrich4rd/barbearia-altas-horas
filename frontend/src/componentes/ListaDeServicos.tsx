"use client";
// Serviços e alcance da home (A16, A17). Os dados vêm da API pelo BFF; nada escrito à mão.
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import type { components } from "@/api/schema";
import { formatarPreco } from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { EstadoDaConsulta } from "./EstadoDaConsulta";

export type Servico = components["schemas"]["Servico"];
export type AlcancePublico = components["schemas"]["AlcancePublico"];

export const buscarServicos = () => chamar(api().GET("/servicos"));

export function ServicosDaHome() {
  const consulta = useConsulta(buscarServicos, []);
  return (
    <section aria-labelledby="servicos-titulo" className="secao">
      <p className="sobretitulo">Serviços</p>
      <h2 id="servicos-titulo">Um barbeiro, todos os serviços</h2>
      <p className="subtitulo-secao">
        Todos com a duração mostrada em cada cartão. Sem combos. O valor é pago na reserva.
      </p>
      <EstadoDaConsulta consulta={consulta}>
        {(servicos) =>
          servicos.length === 0 ? (
            <p>Sem serviços no momento.</p>
          ) : (
            <ul className="grade-servicos">
              {servicos.map((s) => (
                <li key={s.id} className="cartao">
                  <h3>{s.nome}</h3>
                  <p className="cartao-detalhe">
                    <span className="hora">{s.duracaoMin} min</span>
                    <span className="preco">{formatarPreco(s)}</span>
                  </p>
                  <Link
                    href={`/agendar?servicoId=${s.id}`}
                    className="botao botao-secundario"
                    aria-label={`Agendar ${s.nome}, ${s.duracaoMin} minutos, ${formatarPreco(s)}`}
                  >
                    Agendar
                  </Link>
                </li>
              ))}
            </ul>
          )
        }
      </EstadoDaConsulta>
    </section>
  );
}

/** "De onde vêm nossos clientes": só nomes, sem número. Qualquer falha ou lista vazia: o bloco não aparece. */
export function AlcanceDaHome() {
  const [alcance, setAlcance] = useState<AlcancePublico | undefined>();
  useEffect(() => {
    let vivo = true;
    chamar(api().GET("/alcance")).then(
      (dados) => vivo && setAlcance(dados),
      () => vivo && setAlcance(undefined),
    );
    return () => {
      vivo = false;
    };
  }, []);
  if (!alcance || alcance.regioes.length === 0) return null;
  return (
    <section aria-labelledby="alcance-titulo" className="secao">
      <p className="sobretitulo">Clientes da região</p>
      <h2 id="alcance-titulo">De onde vêm nossos clientes</h2>
      <ul className="lista-regioes">
        {alcance.regioes.map((r) => (
          <li key={`${r.cidade}-${r.uf}`}>
            <strong>
              {r.cidade} - {r.uf}
            </strong>
            {r.bairros.length > 0 && <span className="destaque">{r.bairros.join(" · ")}</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}
