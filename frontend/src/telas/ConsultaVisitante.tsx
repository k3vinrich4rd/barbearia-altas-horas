"use client";
// Consulta da reserva do visitante (A32). O fragmento `#id=…&codigo=…` não vai ao servidor: é lido aqui e
// removido do endereço. O 404 da API não distingue "não existe" de "código errado", e a tela também não.
import { useEffect, useRef, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { formatarDataEHora, formatarPreco } from "@/api/formato";
import type { components } from "@/api/schema";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import { LinkBotao } from "@/componentes/Botao";

type Reserva = components["schemas"]["Agendamento"];

export function ConsultaVisitante() {
  const [id, setId] = useState("");
  const [codigo, setCodigo] = useState("");
  const [reserva, setReserva] = useState<Reserva | undefined>();
  const [erro, setErro] = useState<ErroApi | undefined>();
  const [buscando, setBuscando] = useState(false);
  const emCurso = useRef(false);

  async function consultar(reservaId: string, token: string) {
    if (emCurso.current) return;
    emCurso.current = true;
    setBuscando(true);
    setErro(undefined);
    setReserva(undefined);
    try {
      const r = await chamar(
        api().GET("/agendamentos/guest/{id}", {
          params: { path: { id: reservaId.trim() } },
          headers: { "X-Guest-Token": token.trim() },
        }),
      );
      setReserva(r);
    } catch (e) {
      setErro(e instanceof ErroApi ? e : undefined);
    } finally {
      emCurso.current = false;
      setBuscando(false);
      setCodigo("");
    }
  }

  useEffect(() => {
    const p = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    const hid = p.get("id");
    const hcodigo = p.get("codigo");
    if (hid || hcodigo) {
      window.history.replaceState(null, "", window.location.pathname);
      if (hid && hcodigo) void consultar(hid, hcodigo);
      else if (hid) setId(hid);
    }
  }, []);

  return (
    <>
      <p className="sobretitulo">Sua reserva</p>
      <h1>Consultar reserva</h1>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void consultar(id, codigo);
        }}
        aria-label="Consultar reserva"
      >
        <Campo
          id="reserva-id"
          rotulo="Número da reserva"
          autoComplete="off"
          value={id}
          onChange={(e) => setId(e.target.value)}
        />
        <Campo
          id="reserva-codigo"
          rotulo="Código da reserva"
          autoComplete="off"
          value={codigo}
          onChange={(e) => setCodigo(e.target.value)}
        />
        <Botao type="submit" variante="principal" disabled={buscando}>
          Consultar
        </Botao>
      </form>
      {erro &&
        (erro.status === 404 ? (
          <Aviso tipo="erro" focar>
            Não encontramos esta reserva.
          </Aviso>
        ) : (
          <AvisoDeErro erro={erro} />
        ))}
      {reserva && (
        <section aria-labelledby="resultado-reserva">
          <h2 id="resultado-reserva">Sua reserva</h2>
          <p role="status">
            {reserva.servico?.nome}
            {reserva.barbeiro ? ` com ${reserva.barbeiro.nomeExibicao}` : ""}
            <br />
            {formatarDataEHora(reserva.inicio)} · {formatarPreco(reserva)}
            <br />
            Situação: <EtiquetaDeStatus status={reserva.status} />
          </p>
          {reserva.status === "AGUARDANDO_PAGAMENTO" && (
            <LinkBotao href={`/agendar/pagamento/${reserva.id}`} variante="principal">
              Voltar ao pagamento
            </LinkBotao>
          )}
          {(reserva.status === "EXPIRADO" || reserva.status === "CANCELADO") && (
            <LinkBotao href="/agendar" variante="principal">
              Escolher outro horário
            </LinkBotao>
          )}
        </section>
      )}
    </>
  );
}
