"use client";
// Alcance por região (A53, V9): só tabela, acessível, sem barras. Grupo com menos de 5 atendimentos mostra "menos de 5"
// e nenhum percentual: a API manda os dois nulos e o front nunca calcula nem subtrai para descobrir o valor (E20, D114).
import { useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import { useConsulta } from "@/api/hooks";
import { formatarDiaCurto } from "@/api/formato-painel";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { Selecao } from "@/componentes/CamposDoPainel";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { useSessao } from "@/componentes/Sessao";

const MAXIMO_DE_DIAS = 366;

function diasEntre(de: string, ate: string): number {
  const [a1, m1, d1] = de.split("-").map(Number);
  const [a2, m2, d2] = ate.split("-").map(Number);
  return Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86_400_000) + 1;
}

const NAO_MOSTRA = "menos de 5";
const percentual = (n: number) => `${n.toFixed(1).replace(".", ",")}%`;

interface Filtro {
  de: string;
  ate: string;
  barbeiroId: string;
}

export function PainelAlcance() {
  const sessao = useSessao();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [barbeiroId, setBarbeiroId] = useState("");
  const [erro, setErro] = useState("");
  // o período padrão é o da API (últimos 30 dias): sem escolha, não manda `de` nem `ate`
  const [filtro, setFiltro] = useState<Filtro>({ de: "", ate: "", barbeiroId: "" });
  const barbeiros = useConsulta(() => chamar(api().GET("/barbeiros")), [], admin);
  const alcance = useConsulta(
    () =>
      chamar(
        api().GET("/admin/alcance", {
          params: {
            query: {
              ...(filtro.de ? { de: filtro.de } : {}),
              ...(filtro.ate ? { ate: filtro.ate } : {}),
              ...(admin && filtro.barbeiroId ? { barbeiroId: filtro.barbeiroId } : {}),
            },
          },
        }),
      ),
    [filtro, admin],
  );

  function aplicar(e: FormEvent) {
    e.preventDefault();
    if (de && ate) {
      if (de > ate) return setErro("A data inicial deve ser antes da final.");
      if (diasEntre(de, ate) > MAXIMO_DE_DIAS) return setErro(`O período pode ter no máximo ${MAXIMO_DE_DIAS} dias.`);
    }
    setErro("");
    setFiltro({ de, ate, barbeiroId });
  }

  return (
    <>
      <h1>Alcance por região</h1>
      <p>
        De onde vêm os atendimentos, pelo CEP informado. Só somas: grupos com menos de 5 atendimentos não mostram
        número.
      </p>
      <form onSubmit={aplicar} noValidate aria-label="Filtro do alcance">
        <Campo id="a-de" rotulo="De" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        <Campo
          id="a-ate"
          rotulo="Até"
          type="date"
          value={ate}
          onChange={(e) => setAte(e.target.value)}
          erro={erro || undefined}
          ajuda="Sem escolher, vale os últimos 30 dias. No máximo 366 dias."
        />
        {admin && (
          <Selecao id="a-barbeiro" rotulo="Barbeiro" value={barbeiroId} onChange={(e) => setBarbeiroId(e.target.value)}>
            <option value="">Todos</option>
            {barbeiros.dados?.map((b) => (
              <option key={b.id} value={b.id}>
                {b.nomeExibicao}
              </option>
            ))}
          </Selecao>
        )}
        <div className="acoes">
          <Botao type="submit" variante="principal">
            Aplicar
          </Botao>
        </div>
      </form>

      <EstadoDaConsulta consulta={alcance}>
        {(a) =>
          a.total === 0 ? (
            <p>Ainda não há atendimentos suficientes para mostrar o alcance.</p>
          ) : (
            <>
              <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Tabela do alcance por região">
                <table>
                  <caption>
                    {a.total} atendimentos · de {formatarDiaCurto(a.periodo.de)} a {formatarDiaCurto(a.periodo.ate)}
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">Cidade e bairro</th>
                      <th scope="col">Atendimentos</th>
                      <th scope="col">Parcela</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.cidades.flatMap((c) => [
                      <tr key={`c-${c.cidade}`}>
                        <th scope="row">{c.uf ? `${c.cidade}, ${c.uf}` : c.cidade}</th>
                        <td>{c.abaixoDoLimite ? NAO_MOSTRA : c.atendimentos}</td>
                        <td>{c.abaixoDoLimite || c.percentual === null ? "—" : percentual(c.percentual)}</td>
                      </tr>,
                      ...c.bairros.map((b) => (
                        <tr key={`b-${c.cidade}-${b.bairro}`} className="alcance-bairro">
                          <th scope="row">
                            <span className="so-leitor">{c.cidade}: </span>
                            {b.bairro}
                          </th>
                          <td>{b.abaixoDoLimite ? NAO_MOSTRA : b.atendimentos}</td>
                          <td>{b.abaixoDoLimite || b.percentual === null ? "—" : percentual(b.percentual)}</td>
                        </tr>
                      )),
                    ])}
                  </tbody>
                </table>
              </div>
              {a.semRegiao > 0 && <p>Sem região informada: {a.semRegiao}</p>}
            </>
          )
        }
      </EstadoDaConsulta>
    </>
  );
}
