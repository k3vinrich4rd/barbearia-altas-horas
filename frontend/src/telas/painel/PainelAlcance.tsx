"use client";
// Alcance por região (A53, A9, A10): tabela acessível com uma barra por linha (canvas PainelAlcance). Sem limite mínimo
// (D149): toda cidade e todo bairro aparece com o número exato e o percentual que a API manda; o front nunca calcula.
// A barra é só um desenho do mesmo percentual da tabela (aria-hidden, só tokens): nenhum número a mais.
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

const percentual = (n: number) => `${n.toFixed(1).replace(".", ",")}%`;

/** O desenho do percentual: o mesmo número da coluna Parcela, sem texto próprio (a tabela é a fonte). */
function Barra({ valor }: { valor: number }) {
  return <progress className="barra" max={100} value={valor} tabIndex={-1} />;
}

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
        De onde vêm os atendimentos, pelo CEP informado. Só somas por região: nenhum nome nem contato de cliente aparece.
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
                      <th scope="col">
                        <span className="so-leitor">Barra da parcela (só desenho; o número está na coluna Parcela)</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.cidades.flatMap((c) => [
                      <tr key={`c-${c.cidade}-${c.uf}`} className="alcance-cidade">
                        <th scope="row">{`${c.cidade}, ${c.uf}`}</th>
                        <td>{c.atendimentos}</td>
                        <td>{percentual(c.percentual)}</td>
                        <td aria-hidden="true">
                          <Barra valor={c.percentual} />
                        </td>
                      </tr>,
                      ...c.bairros.map((b) => (
                        <tr key={`b-${c.cidade}-${b.bairro}`} className="alcance-bairro">
                          <th scope="row">
                            <span className="so-leitor">{c.cidade}: </span>
                            {b.bairro}
                          </th>
                          <td>{b.atendimentos}</td>
                          <td>{percentual(b.percentual)}</td>
                          <td aria-hidden="true">
                            <Barra valor={b.percentual} />
                          </td>
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
