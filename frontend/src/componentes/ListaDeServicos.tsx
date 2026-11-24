"use client";
// Serviços e alcance da home (A16, A17). Os dados vêm da API pelo BFF; nada escrito à mão.
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import type { components } from "@/api/schema";
import { formatarPreco } from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { EstadoDaConsulta } from "./EstadoDaConsulta";
import { Icone } from "./Icone";

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

/** "De onde vêm nossos clientes" (Fatia 12, E10): o bloco SEMPRE existe. Com dados que passam no k (ADR 0014), só nomes,
 * sem número. Sem dados (lista vazia, erro ou limite), o estado vazio digno: sem botão, sem gráfico, sem "0". */
export function AlcanceDaHome() {
  // undefined = ainda carregando; null = sem dados (vazio ou erro)
  const [alcance, setAlcance] = useState<AlcancePublico | null | undefined>();
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
  return (
    <section id="alcance" aria-labelledby="alcance-titulo" className="secao">
      <p className="sobretitulo">Clientes da região</p>
      <h2 id="alcance-titulo">De onde vêm nossos clientes</h2>
      {alcance === undefined ? null : alcance === null ? (
        <div className="estado-vazio">
          <p>
            <Icone nome="relogio" /> <strong>Em breve:</strong> de onde vêm nossos clientes.
          </p>
          <p className="nota">Mostramos a região quando há clientes suficientes para ninguém ser identificado.</p>
        </div>
      ) : (
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
      )}
    </section>
  );
}
