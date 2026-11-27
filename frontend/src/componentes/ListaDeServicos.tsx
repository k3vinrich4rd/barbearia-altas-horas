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

/**
 * "02 · O que fazemos" (Fatia 16, F5, A9 e A10): uma linha por serviço com nome e preço, e a própria linha leva a
 * `/agendar?servicoId=<id>` com o serviço marcado (sem botão "Agendar este serviço" nem link "Ver todos": a lista
 * completa é esta). Com todos os serviços da mesma duração, ela aparece uma vez só, no apoio; com durações diferentes,
 * em cada linha.
 */
export function ServicosDaHome() {
  const consulta = useConsulta(buscarServicos, []);
  return (
    <section id="servicos" aria-labelledby="servicos-titulo" className="secao">
      <p className="sobretitulo">02 · O que fazemos</p>
      <h2 id="servicos-titulo">Um barbeiro, todos os serviços</h2>
      <EstadoDaConsulta consulta={consulta}>
        {(servicos) => {
          if (servicos.length === 0) return <p>Sem serviços no momento.</p>;
          const duracoes = new Set(servicos.map((s) => s.duracaoMin));
          const duracaoUnica = duracoes.size === 1 ? servicos[0].duracaoMin : null;
          return (
            <>
              <p className="subtitulo-secao">
                {duracaoUnica === null
                  ? "Toque em um serviço para ver os horários."
                  : `Cada serviço dura ${duracaoUnica} min. Toque em um para ver os horários.`}
              </p>
              <ul className="lista-servicos">
                {servicos.map((s) => (
                  <li key={s.id}>
                    <Link href={`/agendar?servicoId=${s.id}`} className="linha-servico">
                      <span className="linha-servico-nome">{s.nome}</span>
                      {duracaoUnica === null && <span className="hora">{s.duracaoMin} min</span>}
                      <span className="preco">{formatarPreco(s)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          );
        }}
      </EstadoDaConsulta>
    </section>
  );
}

/** "cerca de 15%" ou "menos de 5%": a faixa que a API manda, nunca um número de pessoas (A50, ADR 0014). */
export function textoDaFaixa(faixa: string): string {
  return faixa === "<5" ? "menos de 5%" : `cerca de ${faixa}%`;
}

/**
 * "De onde vêm nossos clientes" (Fatia 11, A50, e Fatia 16, A8): o bloco só existe com dados que passam no k (ADR 0014).
 * Com dados, os municípios; ao clicar, os bairros com a faixa de porcentagem, só o nome ou o "Outros bairros" sem faixa.
 * NUNCA número de pessoas. Sem dados (lista vazia, erro ou limite) ou carregando, a seção não aparece: uma seção vazia
 * ocupava uma rolagem inteira sem finalidade (Fatia 16, R5).
 */
export function AlcanceDaHome() {
  // undefined = ainda carregando; null = sem dados (vazio ou erro)
  const [alcance, setAlcance] = useState<AlcancePublico | null | undefined>();
  const [aberto, setAberto] = useState<string | undefined>();
  useEffect(() => {
    let vivo = true;
    chamar(api().GET("/alcance")).then(
      (dados) => vivo && setAlcance(dados.regioes.length > 0 ? dados : null),
      () => vivo && setAlcance(null),
    );
    return () => {
      vivo = false;
    };
  }, []);
  if (!alcance) return null;
  return (
    <section id="alcance" aria-labelledby="alcance-titulo" className="secao">
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
                      {b.faixaPercentual ? (
                        <span className="destaque"> · {textoDaFaixa(b.faixaPercentual)}</span>
                      ) : null}
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
