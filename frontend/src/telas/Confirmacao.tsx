"use client";
// Confirmação da reserva (A31): resumo, aviso da política (R18) e, para o visitante, o código da reserva (E7).
import Link from "next/link";
import { useEffect, useState } from "react";
import { codigoDoVisitante } from "@/api/bff";
import { api, chamar } from "@/api/cliente";
import { formatarDataEHora, formatarPreco } from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { CopiarTexto } from "@/componentes/CopiarTexto";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { useSessao } from "@/componentes/Sessao";
import { STATUS_DA_RESERVA } from "./status";

export function Confirmacao({ reservaId }: { reservaId: string }) {
  const sessao = useSessao();
  const reserva = useConsulta(
    () =>
      sessao.autenticado
        ? chamar(api().GET("/agendamentos/{id}", { params: { path: { id: reservaId } } }))
        : chamar(api().GET("/agendamentos/guest/{id}", { params: { path: { id: reservaId } } })),
    [sessao.autenticado, reservaId],
    !sessao.carregando,
  );
  const politica = useConsulta(() => chamar(api().GET("/politica-pagamento")), []);
  const [codigo, setCodigo] = useState<string | undefined>();

  useEffect(() => {
    if (sessao.carregando || sessao.autenticado) return;
    let vivo = true;
    codigoDoVisitante(reservaId).then(
      (c) => vivo && setCodigo(c.codigo),
      () => vivo && setCodigo(undefined),
    );
    return () => {
      vivo = false;
    };
  }, [sessao.carregando, sessao.autenticado, reservaId]);

  if (reserva.erro && !reserva.erro.acordando) {
    return (
      <>
        <h1>Confirmação</h1>
        {reserva.erro.status === 404 ? <Aviso tipo="erro">Não encontramos esta reserva.</Aviso> : <AvisoDeErro erro={reserva.erro} />}
        <Link href="/agendar">Agendar horário</Link>
      </>
    );
  }

  return (
    <>
      <h1>Reserva confirmada</h1>
      <EstadoDaConsulta consulta={reserva}>
        {(r) => (
          <>
            <section aria-labelledby="resumo-confirmacao">
              <h2 id="resumo-confirmacao">Resumo</h2>
              <p>
                {r.servico?.nome}
                {r.barbeiro ? ` com ${r.barbeiro.nomeExibicao}` : ""}
                <br />
                {formatarDataEHora(r.inicio)}
                <br />
                Valor pago: {formatarPreco(r)}
                <br />
                Situação: {STATUS_DA_RESERVA[r.status] ?? r.status}
              </p>
            </section>
            {politica.dados && <p className="nota">{politica.dados.texto}</p>}
            {sessao.autenticado ? (
              <p>
                <Link href="/meus-agendamentos">Ver meus agendamentos</Link>
              </p>
            ) : (
              <section aria-labelledby="codigo-reserva">
                <h2 id="codigo-reserva">Código da reserva</h2>
                <Aviso tipo="aviso">Guarde este código: ele é a única forma de ver esta reserva.</Aviso>
                {codigo ? (
                  <>
                    <CopiarTexto id="codigo" rotulo="Código" texto={codigo} />
                    <CopiarTexto id="link-acompanhamento" rotulo="Link para acompanhar" texto={`${window.location.origin}/reserva#id=${r.id}&codigo=${codigo}`} />
                    <p className="nota">O link fica guardado no histórico deste navegador. Quem tiver o link vê a reserva.</p>
                  </>
                ) : (
                  <p>O código só aparece neste navegador, logo depois da reserva.</p>
                )}
              </section>
            )}
          </>
        )}
      </EstadoDaConsulta>
    </>
  );
}
