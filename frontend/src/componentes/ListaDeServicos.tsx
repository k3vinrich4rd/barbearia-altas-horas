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

const atendimentos = (n: number) => `${n} ${n === 1 ? "atendimento" : "atendimentos"}`;

/**
 * "De onde vêm nossos clientes" (A13, A14): cada cidade com a quantidade de atendimentos em destaque e os bairros com o
 * número de cada um, do maior para o menor como a API manda (D149: sem limite mínimo). Só região e número, nunca nome nem
 * contato. Qualquer falha ou lista vazia: o bloco não aparece.
 */
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
          <li key={`${r.cidade}-${r.uf}`} className="regiao">
            <p className="regiao-cidade">
              <strong>{r.cidade}</strong>
              <span aria-hidden="true"> · </span>
              <span className="regiao-quantidade">{atendimentos(r.atendimentos)}</span>
            </p>
            {r.bairros.length > 0 && (
              <ul className="regiao-bairros" aria-label={`Bairros de ${r.cidade}`}>
                {r.bairros.map((b) => (
                  <li key={b.bairro}>
                    <span>{b.bairro}</span>
                    <strong className="regiao-numero">{b.atendimentos}</strong>
                    <span className="so-leitor"> {b.atendimentos === 1 ? "atendimento" : "atendimentos"}</span>
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
