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

/** "cerca de 15%" ou "menos de 5%": a faixa que a API manda, nunca um número de pessoas (A50, ADR 0014). */
export function textoDaFaixa(faixa: string): string {
  return faixa === "<5" ? "menos de 5%" : `cerca de ${faixa}%`;
}

/**
 * "De onde vêm nossos clientes" (A50): os municípios; ao clicar, os bairros com a faixa de porcentagem, só o nome (município com
 * menos de 20 clientes) ou o "Outros bairros" sem faixa. NUNCA número de pessoas. Qualquer falha ou lista vazia: o bloco não aparece.
 */
export function AlcanceDaHome() {
  const [alcance, setAlcance] = useState<AlcancePublico | undefined>();
  const [aberto, setAberto] = useState<string | undefined>();
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
        {alcance.regioes.map((r) => {
          const lista = `bairros-${r.codigoIbge}`;
          const expandido = aberto === r.codigoIbge;
          return (
            <li key={r.codigoIbge}>
              <button
                type="button"
                className="botao botao-secundario"
                aria-expanded={expandido}
                aria-controls={lista}
                onClick={() => setAberto(expandido ? undefined : r.codigoIbge)}
              >
                {r.cidade} - {r.uf}
              </button>
              {expandido && (
                <ul id={lista} className="lista-bairros" aria-label={`Bairros de ${r.cidade}`}>
                  {r.bairrosComFaixa.length === 0 && <li>Ainda sem bairros para mostrar.</li>}
                  {r.bairrosComFaixa.map((b) => (
                    <li key={b.bairro}>
                      {b.bairro}
                      {b.faixaPercentual ? <span className="destaque"> · {textoDaFaixa(b.faixaPercentual)}</span> : null}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
