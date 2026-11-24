"use client";
// Alcance por estado, região e município (Fatia 11, A51 a A54, V9): só tabela, acessível, sem barras. O seletor de nível escolhe
// o que aparece nas linhas; cada linha traz o nível de baixo. Quem tem menos de k pessoas mostra "menos de k" e nenhum percentual:
// a API manda os dois nulos, o lugar já SOBE de nível, e o front nunca calcula nem subtrai para descobrir o valor (E13, D114).
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { useConsulta } from "@/api/hooks";
import { formatarDiaCurto } from "@/api/formato-painel";
import { Selecao } from "@/componentes/CamposDoPainel";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { useSessao } from "@/componentes/Sessao";
import type { components } from "@/api/schema";

type Painel = components["schemas"]["AlcancePainel"];
type Grupo = components["schemas"]["AlcanceGrupo"];
type Filho = components["schemas"]["AlcanceFilho"];
type Nivel = components["schemas"]["NivelDoAlcance"];
type Periodo = "ULTIMOS_30_DIAS" | "ULTIMOS_90_DIAS" | "ULTIMOS_12_MESES";

const NIVEIS: { valor: Nivel; texto: string; coluna: string; rotulo: string }[] = [
  { valor: "ESTADO", texto: "Estado", coluna: "Estado", rotulo: "estado" },
  { valor: "REGIAO", texto: "Região", coluna: "Região", rotulo: "região" },
  { valor: "MUNICIPIO", texto: "Município", coluna: "Município e bairro", rotulo: "município" },
];
const PERIODOS: { valor: Periodo; texto: string }[] = [
  { valor: "ULTIMOS_30_DIAS", texto: "Últimos 30 dias" },
  { valor: "ULTIMOS_90_DIAS", texto: "Últimos 90 dias" },
  { valor: "ULTIMOS_12_MESES", texto: "Últimos 12 meses" },
];
const UFS = "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" ");

const percentual = (n: number) => `${n.toFixed(1).replace(".", ",")}%`;
const AGRUPADO: Record<string, string> = {
  REGIAO: "somados na região",
  UF: "somados no estado",
  PAIS: "somados fora dos estados com clientes suficientes",
};

export function PainelAlcance() {
  const sessao = useSessao();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const [nivel, setNivel] = useState<Nivel>("MUNICIPIO");
  const [uf, setUf] = useState("");
  const [periodo, setPeriodo] = useState<Periodo>("ULTIMOS_30_DIAS");
  const [barbeiroId, setBarbeiroId] = useState("");
  const barbeiros = useConsulta(() => chamar(api().GET("/barbeiros")), [], admin);
  const alcance = useConsulta(
    () =>
      chamar(
        api().GET("/admin/alcance", {
          params: {
            query: {
              nivel,
              periodo,
              ...(uf ? { uf } : {}),
              ...(admin && barbeiroId ? { barbeiroId } : {}),
            },
          },
        }),
      ),
    [nivel, uf, periodo, barbeiroId, admin],
  );
  const nivelAtual = NIVEIS.find((n) => n.valor === nivel)!;

  return (
    <>
      <h1>Alcance por região</h1>
      <p>
        De onde vêm os <strong>clientes</strong> (pessoas diferentes, não atendimentos), pelo bairro e a cidade que elas
        autorizaram. Quem tem menos de 5 pessoas não aparece com número: ele é somado ao nível de cima.
      </p>
      {!admin && (
        <p className="nota" role="note">
          Você vê só os seus clientes.
        </p>
      )}
      <form aria-label="Filtro do alcance" onSubmit={(e) => e.preventDefault()}>
        <Selecao id="a-nivel" rotulo="Nível" value={nivel} onChange={(e) => setNivel(e.target.value as Nivel)}>
          {NIVEIS.map((n) => (
            <option key={n.valor} value={n.valor}>
              {n.texto}
            </option>
          ))}
        </Selecao>
        <Selecao id="a-uf" rotulo="Estado (UF)" value={uf} onChange={(e) => setUf(e.target.value)}>
          <option value="">Todos</option>
          {UFS.map((u) => (
            <option key={u} value={u}>
              {u}
            </option>
          ))}
        </Selecao>
        <Selecao id="a-periodo" rotulo="Período" value={periodo} onChange={(e) => setPeriodo(e.target.value as Periodo)}>
          {PERIODOS.map((p) => (
            <option key={p.valor} value={p.valor}>
              {p.texto}
            </option>
          ))}
        </Selecao>
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
      </form>

      <EstadoDaConsulta consulta={alcance}>
        {(a) => <Tabela a={a} coluna={nivelAtual.coluna} rotulo={nivelAtual.rotulo} />}
      </EstadoDaConsulta>
    </>
  );
}

function Tabela({ a, coluna, rotulo }: { a: Painel; coluna: string; rotulo: string }) {
  const menosDeK = `menos de ${a.k}`;
  const vazio = a.grupos.length === 0 && (a.semRegiao.clientes ?? 0) === 0;
  if (vazio) return <p>Ainda não há clientes suficientes para mostrar o alcance.</p>;

  const numero = (clientes: number | null | undefined, abaixo: boolean) => (abaixo || clientes == null ? menosDeK : clientes);
  const parcela = (p: number | null | undefined, abaixo: boolean) => (abaixo || p == null ? "—" : percentual(p));
  const sem = a.semRegiao;

  return (
    <>
      <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Tabela do alcance por região">
        <table>
          <caption>
            {a.totalVisivel && a.clientes != null ? `${a.clientes} clientes` : "Clientes"} por {rotulo} · de{" "}
            {formatarDiaCurto(a.periodo.de)} a {formatarDiaCurto(a.periodo.ate)}
          </caption>
          <thead>
            <tr>
              <th scope="col">{coluna}</th>
              <th scope="col">Clientes</th>
              <th scope="col">Parcela</th>
            </tr>
          </thead>
          <tbody>
            {a.grupos.flatMap((g: Grupo, i: number) => [
              <tr key={`g-${i}-${g.tipo}-${g.codigo ?? g.nome}`}>
                <th scope="row">
                  {g.uf && g.tipo !== "UF" ? `${g.nome}, ${g.uf}` : g.nome}
                  {g.agrupadoEm && <span className="nota"> ({AGRUPADO[g.agrupadoEm] ?? "somados"})</span>}
                </th>
                <td>{numero(g.clientes, g.abaixoDoLimite)}</td>
                <td>{parcela(g.percentual, g.abaixoDoLimite)}</td>
              </tr>,
              ...g.filhos.map((f: Filho, j: number) => (
                <tr key={`f-${i}-${j}-${f.tipo}-${f.codigo ?? f.nome}`} className="alcance-bairro">
                  <th scope="row">
                    <span className="so-leitor">{g.nome}: </span>
                    {f.nome}
                  </th>
                  <td>{numero(f.clientes, f.abaixoDoLimite)}</td>
                  <td>{parcela(f.percentual, f.abaixoDoLimite)}</td>
                </tr>
              )),
            ])}
          </tbody>
        </table>
      </div>
      {sem.abaixoDoLimite ? (
        <p>Sem região informada: {menosDeK}</p>
      ) : (
        (sem.clientes ?? 0) > 0 && <p>Sem região informada: {sem.clientes}</p>
      )}
      {!a.totalVisivel && (
        <p role="note">A soma ficou escondida para proteger os clientes: o total e as porcentagens não aparecem.</p>
      )}
    </>
  );
}
