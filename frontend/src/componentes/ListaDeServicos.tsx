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

export function ServicosDaHome({ comLinkVerTodos = true }: { comLinkVerTodos?: boolean } = {}) {
  const consulta = useConsulta(buscarServicos, []);
  return (
    <section id="servicos" aria-labelledby="servicos-titulo" className="secao">
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
            <>
              <ul className="grade-servicos">
                {servicos.map((s) => (
                  <li key={s.id} className="cartao cartao-servico">
                    <h3>{s.nome}</h3>
                    <p className="cartao-detalhe">
                      <span className="hora">{s.duracaoMin} min</span>
                      <span className="preco">{formatarPreco(s)}</span>
                    </p>
                    <Link
                      href={`/agendar?servicoId=${s.id}`}
                      className="botao botao-secundario"
                      aria-label={`Agendar este serviço: ${s.nome}, ${s.duracaoMin} minutos, ${formatarPreco(s)}`}
                    >
                      Agendar este serviço
                    </Link>
                  </li>
                ))}
              </ul>
              {comLinkVerTodos && (
                <p>
                  <Link href="/servicos">Ver todos os serviços e preços</Link>
                </p>
              )}
            </>
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
