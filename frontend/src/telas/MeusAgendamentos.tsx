"use client";
// Meus agendamentos (A38): lista paginada, status em texto, link "Pagar" na pendente. Nada de cancelar ou remarcar (R18).
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { formatarDataEHora, formatarPreco } from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { Paginacao } from "@/componentes/Paginacao";
import { AvisoDeErro } from "@/componentes/Aviso";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import { useSessao } from "@/componentes/Sessao";

const TAMANHO = 10;

export function MeusAgendamentos() {
  const roteador = useRouter();
  const sessao = useSessao();
  const [pagina, setPagina] = useState(0);
  const consulta = useConsulta(
    () => chamar(api().GET("/me/agendamentos", { params: { query: { pagina, tamanho: TAMANHO } } })),
    [pagina],
  );

  useEffect(() => {
    if (consulta.erro?.status === 401) roteador.push("/entrar?voltar=%2Fmeus-agendamentos");
  }, [consulta.erro, roteador]);

  return (
    <>
      {sessao.usuario?.nome && <p className="sobretitulo">Olá, {sessao.usuario.nome.split(" ")[0]}</p>}
      <h1>Meus agendamentos</h1>
      <p>
        <Link href="/agendar" className="botao botao-principal">
          Agendar horário
        </Link>
      </p>
      {consulta.erro && consulta.erro.status === 401 ? (
        <AvisoDeErro erro={consulta.erro} />
      ) : (
        <EstadoDaConsulta consulta={consulta}>
          {(p) =>
            p.itens.length === 0 ? (
              <p>
                Você ainda não tem agendamentos. <Link href="/agendar">Agendar horário</Link>
              </p>
            ) : (
              <>
                {[
                  ["Próximos", p.itens.filter((a) => ["AGUARDANDO_PAGAMENTO", "CONFIRMADO"].includes(a.status))],
                  ["Anteriores", p.itens.filter((a) => !["AGUARDANDO_PAGAMENTO", "CONFIRMADO"].includes(a.status))],
                ].map(([titulo, itens]) =>
                  (itens as typeof p.itens).length === 0 ? null : (
                    <section key={titulo as string} aria-label={titulo as string}>
                      <h2>{titulo as string}</h2>
                      <ul className="lista">
                        {(itens as typeof p.itens).map((a) => (
                          <li
                            key={a.id}
                            className="item item-reserva"
                            data-estado={
                              a.status === "CONFIRMADO"
                                ? "confirmado"
                                : a.status === "AGUARDANDO_PAGAMENTO"
                                  ? "aguardando"
                                  : undefined
                            }
                          >
                            <h3>
                              {a.servico?.nome}
                              {a.barbeiro ? ` com ${a.barbeiro.nomeExibicao}` : ""}
                            </h3>
                            <p>
                              {formatarDataEHora(a.inicio)} · {formatarPreco(a)}
                            </p>
                            <p>
                              <EtiquetaDeStatus status={a.status} />
                            </p>
                            {a.status === "AGUARDANDO_PAGAMENTO" &&
                              a.expiraEm &&
                              new Date(a.expiraEm).getTime() > Date.now() && (
                                <Link href={`/agendar/pagamento/${a.id}`} className="botao botao-principal">
                                  Pagar
                                </Link>
                              )}
                            {a.status === "CANCELADO" && a.motivoCancelamento && <p>Motivo: {a.motivoCancelamento}</p>}
                          </li>
                        ))}
                      </ul>
                    </section>
                  ),
                )}
                <Paginacao pagina={p.pagina} tamanho={p.tamanho} total={p.total} aoMudar={setPagina} />
              </>
            )
          }
        </EstadoDaConsulta>
      )}
    </>
  );
}
