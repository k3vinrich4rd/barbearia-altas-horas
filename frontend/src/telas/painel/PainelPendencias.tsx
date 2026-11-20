"use client";
// Pendências de pagamento (A48, A7, V8): devoluções a fazer (ou as cobranças por status, filtro do canvas) e avisos do
// gateway em revisão. O filtro vai para a API (`status`): filtrar só na tela daria resultado errado numa lista paginada.
// Só ADMIN.
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { formatarCentavos, formatarDataEHora } from "@/api/formato";
import { mascararWhatsapp } from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Dialogo } from "@/componentes/Dialogo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { Paginacao } from "@/componentes/Paginacao";
import { STATUS_DA_COBRANCA, type CobrancaAdmin } from "./tipos";

type StatusFiltrado = "PENDENTE" | "PAGA" | "CANCELADA";

/** Os filtros do canvas. "A devolver" não manda `status`: a API entrega as pendências, as mais antigas primeiro. */
const FILTROS: { id: string; rotulo: string; titulo: string; status?: StatusFiltrado }[] = [
  { id: "devolver", rotulo: "A devolver", titulo: "Cobranças a devolver" },
  { id: "aguardando", rotulo: "Aguardando", titulo: "Cobranças aguardando pagamento", status: "PENDENTE" },
  { id: "pagas", rotulo: "Pagas", titulo: "Cobranças pagas", status: "PAGA" },
  { id: "expiradas", rotulo: "Expiradas ou canceladas", titulo: "Cobranças expiradas ou canceladas", status: "CANCELADA" },
];

const TAMANHO = 10;

function motivoDaPendencia(c: CobrancaAdmin): string {
  if (c.status === "PAGA_APOS_EXPIRACAO") return "Pago depois do prazo e o horário já tinha outro dono";
  return c.reserva?.motivoCancelamento
    ? `Cancelado pela barbearia: ${c.reserva.motivoCancelamento}`
    : "Cancelado pela barbearia";
}

export function PainelPendencias() {
  const [pagina, setPagina] = useState(0);
  const [filtroId, setFiltroId] = useState("devolver");
  const filtro = FILTROS.find((f) => f.id === filtroId) ?? FILTROS[0];
  const [paginaEventos, setPaginaEventos] = useState(0);
  const [devolvendo, setDevolvendo] = useState<CobrancaAdmin | null>(null);
  const [mensagem, setMensagem] = useState("");
  const envio = useEnvio();
  const cobrancas = useConsulta(
    () =>
      chamar(
        api().GET("/admin/pagamentos", {
          params: { query: { pagina, tamanho: TAMANHO, ...(filtro.status ? { status: filtro.status } : {}) } },
        }),
      ),
    [pagina, filtro.status],
  );
  const eventos = useConsulta(
    () =>
      chamar(
        api().GET("/admin/pagamentos/eventos", { params: { query: { pagina: paginaEventos, tamanho: TAMANHO } } }),
      ),
    [paginaEventos],
  );

  async function registrar() {
    if (!devolvendo) return;
    const alvo = devolvendo;
    const feito = await envio.executar(() =>
      chamar(api().POST("/admin/pagamentos/{id}/devolucao", { params: { path: { id: alvo.id } } })),
    );
    // a repetição (422) fica no diálogo como "já registrada", com o botão de atualizar a fila
    if (feito) {
      setMensagem("Devolução registrada.");
      setDevolvendo(null);
      cobrancas.recarregar();
    }
  }

  async function revisar(id: string) {
    const feito = await envio.executar(() =>
      chamar(api().POST("/admin/pagamentos/eventos/{id}/revisao", { params: { path: { id } } })),
    );
    if (feito) {
      setMensagem("Aviso marcado como revisado.");
      eventos.recarregar();
    }
  }

  const aDevolver = filtro.status === undefined;
  const jaRegistrada = envio.erro?.status === 422 && devolvendo !== null;
  return (
    <>
      <h1>Pendências de pagamento</h1>
      <p>Devoluções que você precisa fazer e avisos do gateway que pedem revisão.</p>
      <div aria-live="polite">{mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}</div>
      {envio.erro && !devolvendo && <AvisoDeErro erro={envio.erro} focar={false} />}

      <section aria-labelledby="t-devolver">
        <h2 id="t-devolver">{aDevolver ? "Devoluções a fazer" : "Cobranças"}</h2>
        <div className="acoes" role="group" aria-label="Filtrar cobranças">
          {FILTROS.map((f) => (
            <Botao
              key={f.id}
              aria-pressed={f.id === filtro.id}
              onClick={() => {
                setFiltroId(f.id);
                setPagina(0);
                setMensagem("");
              }}
            >
              {f.rotulo}
            </Botao>
          ))}
        </div>
        <EstadoDaConsulta consulta={cobrancas}>
          {(p) =>
            p.itens.length === 0 ? (
              <p>{aDevolver ? "Nada pendente." : "Nenhuma cobrança neste filtro."}</p>
            ) : (
              <>
                <div className="tabela-rolavel" tabIndex={0} role="region" aria-label={filtro.titulo}>
                  <table>
                    <caption>{filtro.titulo}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Reserva</th>
                        <th scope="col">Contato</th>
                        <th scope="col">Serviço</th>
                        <th scope="col">Valor</th>
                        <th scope="col">Status</th>
                        {aDevolver && <th scope="col">Motivo</th>}
                        {aDevolver && <th scope="col">Ação</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {p.itens.map((c) => (
                        <tr key={c.id}>
                          <td>{c.reserva?.inicio ? formatarDataEHora(c.reserva.inicio) : "—"}</td>
                          <td>
                            {c.contato?.nome ?? "—"}
                            {c.contato?.whatsapp && (
                              <>
                                <br />
                                <span>{mascararWhatsapp(c.contato.whatsapp)}</span>
                              </>
                            )}
                          </td>
                          <td>{c.reserva?.servico ?? "—"}</td>
                          <td className="preco">{formatarCentavos(c.valorCentavos)}</td>
                          <td>{STATUS_DA_COBRANCA[c.status] ?? c.status}</td>
                          {aDevolver && <td>{motivoDaPendencia(c)}</td>}
                          {aDevolver && (
                            <td>
                              <Botao
                                aria-label={`Registrar devolução de ${formatarCentavos(c.valorCentavos)} para ${c.contato?.nome ?? "o cliente"}`}
                                onClick={() => {
                                  envio.limparErro();
                                  setMensagem("");
                                  setDevolvendo(c);
                                }}
                              >
                                Registrar devolução
                              </Botao>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Paginacao pagina={p.pagina} tamanho={p.tamanho} total={p.total} aoMudar={setPagina} />
              </>
            )
          }
        </EstadoDaConsulta>
      </section>

      <section aria-labelledby="t-eventos">
        <h2 id="t-eventos">Avisos do gateway em revisão</h2>
        <EstadoDaConsulta consulta={eventos}>
          {(p) =>
            p.itens.length === 0 ? (
              <p>Nada pendente.</p>
            ) : (
              <>
                <p>
                  O sistema tentou processar estes avisos várias vezes e não conseguiu. Confira no painel do gateway
                  antes de mexer na reserva.
                </p>
                <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Avisos do gateway em revisão">
                  <table>
                    <caption>Avisos em revisão manual</caption>
                    <thead>
                      <tr>
                        <th scope="col">Tipo</th>
                        <th scope="col">Recebido</th>
                        <th scope="col">Tentativas</th>
                        <th scope="col">Último erro</th>
                        <th scope="col">Ação</th>
                      </tr>
                    </thead>
                    <tbody>
                      {p.itens.map((ev) => (
                        <tr key={ev.id}>
                          <td>{ev.tipo}</td>
                          <td>{formatarDataEHora(ev.recebidoEm)}</td>
                          <td>{ev.tentativas}</td>
                          <td>{ev.ultimoErro ?? "—"}</td>
                          <td>
                            <Botao
                              disabled={envio.enviando}
                              onClick={() => revisar(ev.id)}
                              aria-label={`Marcar como revisado: ${ev.tipo}`}
                            >
                              Marcar como revisado
                            </Botao>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Paginacao pagina={p.pagina} tamanho={p.tamanho} total={p.total} aoMudar={setPaginaEventos} />
              </>
            )
          }
        </EstadoDaConsulta>
      </section>

      {devolvendo && (
        <Dialogo titulo="Registrar devolução" aoFechar={() => setDevolvendo(null)}>
          <p>
            Confirme só depois de devolver {formatarCentavos(devolvendo.valorCentavos)} para{" "}
            {devolvendo.contato?.nome ?? "o cliente"}. O sistema apenas registra: o Pix de volta é feito fora do app.
          </p>
          {jaRegistrada ? (
            <Aviso tipo="aviso">Esta devolução já foi registrada.</Aviso>
          ) : (
            envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />
          )}
          <div className="acoes">
            {jaRegistrada ? (
              <Botao
                variante="principal"
                onClick={() => {
                  setDevolvendo(null);
                  envio.limparErro();
                  cobrancas.recarregar();
                }}
              >
                Atualizar a lista
              </Botao>
            ) : (
              <Botao variante="principal" disabled={envio.enviando} onClick={registrar}>
                Registrar devolução
              </Botao>
            )}
            <Botao disabled={envio.enviando} onClick={() => setDevolvendo(null)}>
              Voltar
            </Botao>
          </div>
        </Dialogo>
      )}
    </>
  );
}
