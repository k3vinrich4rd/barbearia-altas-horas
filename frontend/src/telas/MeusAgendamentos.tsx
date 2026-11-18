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
import { STATUS_DA_RESERVA } from "./status";

const TAMANHO = 10;

export function MeusAgendamentos() {
  const roteador = useRouter();
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
      <h1>Meus agendamentos</h1>
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
                <ul className="lista">
                  {p.itens.map((a) => (
                    <li key={a.id} className="item" data-estado={a.status === "CONFIRMADO" ? "confirmado" : a.status === "AGUARDANDO_PAGAMENTO" ? "aguardando" : undefined}>
                      <h2>
                        {a.servico?.nome}
                        {a.barbeiro ? ` com ${a.barbeiro.nomeExibicao}` : ""}
                      </h2>
                      <p>
                        {formatarDataEHora(a.inicio)} · {formatarPreco(a)}
                        <br />
                        Situação: <strong>{STATUS_DA_RESERVA[a.status] ?? a.status}</strong>
                      </p>
                      {a.status === "AGUARDANDO_PAGAMENTO" && a.expiraEm && new Date(a.expiraEm).getTime() > Date.now() && (
                        <Link href={`/agendar/pagamento/${a.id}`}>Pagar</Link>
                      )}
                      {a.status === "CANCELADO" && a.motivoCancelamento && <p>Motivo do cancelamento: {a.motivoCancelamento}</p>}
                    </li>
                  ))}
                </ul>
                <Paginacao pagina={p.pagina} tamanho={p.tamanho} total={p.total} aoMudar={setPagina} />
              </>
            )
          }
        </EstadoDaConsulta>
      )}
    </>
  );
}
