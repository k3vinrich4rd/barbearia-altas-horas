"use client";
// Agenda do painel (A45 a A47, V7, A1 a A6 da 7C): o dia é uma GRADE DE HORAS de 30 em 30 minutos (canvas PainelAgenda),
// com o almoço listrado e cada reserva no bloco certo; clicar num bloco abre os detalhes e as ações ao lado. A semana
// continua em lista (o canvas não desenha a semana). Cada reserva sai com data-estado E com o status em texto; a forma
// de cada estado é do CSS (README, "Padrões da agenda"). A ordem de leitura é por horário, a mesma da grade.
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Fragment, useEffect, useId, useMemo, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { diaNoFuso, formatarHora, somarDias } from "@/api/formato";
import {
  diaIsoDaSemana,
  formatarDiaCurto,
  formatarDiaPorExtenso,
  mascararWhatsapp,
  segundaDaSemana,
} from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { AreaDeTexto, Selecao } from "@/componentes/CamposDoPainel";
import { Dialogo } from "@/componentes/Dialogo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import { useSessao } from "@/componentes/Sessao";
import { STATUS_DA_RESERVA } from "@/telas/status";
import { montarGrade } from "./grade";
import {
  almocosDoDia,
  emMinutos,
  horaCurta,
  STATUS_DA_COBRANCA,
  type AgendamentoAdmin,
  type IntervaloJornada,
} from "./tipos";

export const ESTADO_DO_STATUS: Record<string, string> = {
  CONFIRMADO: "confirmado",
  AGUARDANDO_PAGAMENTO: "aguardando",
  CONCLUIDO: "concluido",
  NAO_COMPARECEU: "nao-compareceu",
  CANCELADO: "cancelado",
  EXPIRADO: "expirado",
};

type Visao = "DIA" | "SEMANA";
const MOTIVO_MAXIMO = 300;

/** Texto próprio das falhas de fechar e cancelar (A46, A47); o resto vem do catálogo pelo `code`. */
function textoDaFalha(erro: ErroApi): string {
  if (erro.code === "TRANSICAO_INVALIDA") return "Só dá para fechar o atendimento depois do horário de início.";
  if (erro.code === "CONFLITO_DE_VERSAO")
    return "Alguém mexeu neste agendamento antes de você. Atualizamos a agenda: confira e tente de novo.";
  if (erro.code === "ACESSO_NEGADO") return "Só o administrador cancela agendamentos.";
  return mensagemDoErro(erro);
}

type Entrada =
  | { tipo: "agendamento"; minutos: number; item: AgendamentoAdmin }
  | { tipo: "almoco"; minutos: number; inicio: string; fim: string };

function entradasDoDia(dia: string, itens: AgendamentoAdmin[], jornada: IntervaloJornada[] | null): Entrada[] {
  const lista: Entrada[] = itens.map((item) => ({
    tipo: "agendamento",
    minutos: emMinutos(formatarHora(item.inicio)),
    item,
  }));
  if (jornada)
    for (const a of almocosDoDia(jornada, diaIsoDaSemana(dia)))
      lista.push({ tipo: "almoco", minutos: emMinutos(a.inicio), inicio: a.inicio, fim: a.fim });
  // estável: na mesma hora o agendamento (ordem da API) vem antes do almoço
  return lista.sort((a, b) => a.minutos - b.minutos);
}

/** O selo do bloco, como no canvas: PAGO, MANUAL · WHATSAPP ou, nos outros estados, a etiqueta com forma e palavra. */
function SeloDoBloco({ item }: { item: AgendamentoAdmin }) {
  if (item.status !== "CONFIRMADO") return <EtiquetaDeStatus status={item.status} />;
  if (item.origem === "MANUAL") return <span className="selo-origem">MANUAL · WHATSAPP</span>;
  if (item.cobrancaStatus === "PAGA") return <span className="selo-origem">PAGO</span>;
  return null;
}

function textoDoBloco(item: AgendamentoAdmin): string | null {
  if (item.status === "AGUARDANDO_PAGAMENTO" && item.expiraEm)
    return `Aguardando pagamento até ${formatarHora(item.expiraEm)}`;
  if (item.status === "CONFIRMADO")
    return item.origem === "MANUAL" && item.observacao
      ? `Confirmado · Obs.: ${item.observacao}`
      : STATUS_DA_RESERVA.CONFIRMADO;
  return null;
}

export function PainelAgenda() {
  const sessao = useSessao();
  const busca = useSearchParams();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const hoje = diaNoFuso(new Date());
  const doEndereco = busca.get("data");
  const [data, setData] = useState(doEndereco && /^\d{4}-\d{2}-\d{2}$/.test(doEndereco) ? doEndereco : hoje);
  const [visao, setVisao] = useState<Visao>("DIA");
  const [barbeiroId, setBarbeiroId] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [substituidos, setSubstituidos] = useState<Record<string, AgendamentoAdmin>>({});
  const [selecionadoId, setSelecionadoId] = useState<string | null>(null);
  const [cancelando, setCancelando] = useState<AgendamentoAdmin | null>(null);
  const [confirmandoFalta, setConfirmandoFalta] = useState<AgendamentoAdmin | null>(null);
  const [motivo, setMotivo] = useState("");
  const envio = useEnvio();
  const tituloId = useId();

  const agenda = useConsulta(
    () =>
      chamar(
        api().GET("/admin/agenda", {
          params: { query: { data, visao, ...(admin && barbeiroId ? { barbeiroId } : {}) } },
        }),
      ),
    [data, visao, barbeiroId, admin],
  );
  // jornada para listrar o almoço: ADMIN pela lista de barbeiros (com o filtro escolhido), BARBEIRO pela rota própria (D142)
  const barbeiros = useConsulta(() => chamar(api().GET("/admin/barbeiros")), [], admin);
  const minha = useConsulta(() => chamar(api().GET("/admin/minha-jornada")), [], !admin && sessao.autenticado);
  const jornada: IntervaloJornada[] | null = admin
    ? barbeiroId
      ? (barbeiros.dados?.find((b) => b.id === barbeiroId)?.jornada ?? null)
      : null
    : (minha.dados?.intervalos ?? null);

  useEffect(() => setSubstituidos({}), [agenda.dados]);

  const dias = useMemo(() => {
    if (visao === "DIA") return [data];
    const segunda = segundaDaSemana(data);
    return Array.from({ length: 7 }, (_, i) => somarDias(segunda, i));
  }, [data, visao]);

  function andar(passo: number) {
    setData((d) => somarDias(d, passo * (visao === "SEMANA" ? 7 : 1)));
    setSelecionadoId(null);
    setMensagem("");
  }

  async function mudar(item: AgendamentoAdmin, status: "CONCLUIDO" | "NAO_COMPARECEU" | "CANCELADO", texto?: string) {
    return envio.executar(async () => {
      try {
        const novo = await chamar(
          api().PATCH("/admin/agendamentos/{id}/status", {
            params: { path: { id: item.id } },
            body: { status, version: item.version ?? 0, ...(texto ? { motivo: texto } : {}) },
          }),
        );
        setSubstituidos((s) => ({ ...s, [novo.id]: novo }));
        setMensagem(
          status === "CONCLUIDO"
            ? "Atendimento concluído."
            : status === "NAO_COMPARECEU"
              ? "Falta registrada. O valor pago fica retido."
              : "Agendamento cancelado. Se havia pagamento, a devolução aparece em Pendências.",
        );
        return novo;
      } catch (e) {
        if (e instanceof ErroApi && e.code === "CONFLITO_DE_VERSAO") agenda.recarregar();
        throw e;
      }
    });
  }

  async function confirmarFalta() {
    if (!confirmandoFalta) return;
    const feito = await mudar(confirmandoFalta, "NAO_COMPARECEU");
    if (feito) setConfirmandoFalta(null);
  }

  async function confirmarCancelamento() {
    if (!cancelando || !motivo.trim()) return;
    const feito = await mudar(cancelando, "CANCELADO", motivo.trim());
    if (feito) {
      setCancelando(null);
      setMotivo("");
    }
  }

  const titulo =
    visao === "DIA"
      ? formatarDiaPorExtenso(data)
      : `Semana de ${formatarDiaCurto(dias[0]).slice(0, 5)} a ${formatarDiaCurto(dias[6]).slice(0, 5)}`;

  /** Concluído, Não compareceu e Cancelar pela barbearia: os mesmos botões na grade (painel ao lado) e na lista da semana. */
  function acoesDe(atual: AgendamentoAdmin, descricaoId: string) {
    const podeFechar = atual.status === "CONFIRMADO";
    const podeCancelar = admin && (atual.status === "CONFIRMADO" || atual.status === "AGUARDANDO_PAGAMENTO");
    if (!podeFechar && !podeCancelar) return null;
    return (
      <div className="acoes">
        {podeFechar && (
          <>
            <Botao
              variante="principal"
              disabled={envio.enviando}
              aria-describedby={descricaoId}
              onClick={() => mudar(atual, "CONCLUIDO")}
            >
              Concluído
            </Botao>
            <Botao
              disabled={envio.enviando}
              aria-describedby={descricaoId}
              onClick={() => {
                envio.limparErro();
                setConfirmandoFalta(atual);
              }}
            >
              Não compareceu
            </Botao>
          </>
        )}
        {podeCancelar && (
          <Botao
            disabled={envio.enviando}
            aria-describedby={descricaoId}
            onClick={() => {
              envio.limparErro();
              setMotivo("");
              setCancelando(atual);
            }}
          >
            Cancelar pela barbearia
          </Botao>
        )}
      </div>
    );
  }

  /** A semana: a lista de sempre, com os detalhes e as ações dentro de cada reserva. */
  function itemDaLista(item: AgendamentoAdmin) {
    const atual = substituidos[item.id] ?? item;
    const pago = atual.origem !== "MANUAL" && atual.cobrancaStatus === "PAGA";
    const nomeId = `${tituloId}-${atual.id}`;
    return (
      <li key={atual.id} className="item agenda-item" data-estado={ESTADO_DO_STATUS[atual.status]}>
        <p className="agenda-linha">
          <span className="hora">
            {formatarHora(atual.inicio)}–{formatarHora(atual.fim)}
          </span>
          <EtiquetaDeStatus status={atual.status} />
          {atual.origem === "MANUAL" && <span className="selo-origem">MANUAL · WHATSAPP</span>}
          {pago && <span className="selo-origem">PAGO</span>}
        </p>
        <p id={nomeId} className="agenda-titulo">
          <strong>{atual.servico?.nome}</strong> · {atual.cliente.nome}
        </p>
        {atual.cliente.whatsapp && <p>WhatsApp: {mascararWhatsapp(atual.cliente.whatsapp)}</p>}
        {atual.cobrancaStatus && <p>Pagamento: {STATUS_DA_COBRANCA[atual.cobrancaStatus] ?? atual.cobrancaStatus}</p>}
        {atual.status === "AGUARDANDO_PAGAMENTO" && atual.expiraEm && (
          <p>Aguardando pagamento até {formatarHora(atual.expiraEm)}.</p>
        )}
        {atual.observacao && <p>Observação: {atual.observacao}</p>}
        {atual.motivoCancelamento && <p>Motivo: {atual.motivoCancelamento}</p>}
        {acoesDe(atual, nomeId)}
      </li>
    );
  }

  /** O painel ao lado da grade: detalhes da reserva escolhida e as ações (canvas: aside com aria-live). */
  function detalhe(itens: AgendamentoAdmin[]) {
    const escolhido = itens.find((i) => i.id === selecionadoId);
    const atual = escolhido ? (substituidos[escolhido.id] ?? escolhido) : null;
    if (!atual)
      return (
        <aside className="agenda-detalhe" aria-labelledby={`${tituloId}-det`} aria-live="polite">
          <h3 id={`${tituloId}-det`}>Detalhes</h3>
          <p>Escolha um horário da grade para ver os detalhes e fechar o atendimento.</p>
        </aside>
      );
    const manual = atual.origem === "MANUAL";
    const nomeId = `${tituloId}-det`;
    return (
      <aside className="agenda-detalhe" aria-labelledby={nomeId} aria-live="polite" data-estado={ESTADO_DO_STATUS[atual.status]}>
        <h3 id={nomeId}>
          {formatarHora(atual.inicio)}–{formatarHora(atual.fim)}
        </h3>
        <p>
          <EtiquetaDeStatus status={atual.status} />
        </p>
        <dl className="agenda-dados">
          <div>
            <dt>Cliente</dt>
            <dd>
              {atual.cliente.nome}
              {atual.cliente.whatsapp && <> · WhatsApp: {mascararWhatsapp(atual.cliente.whatsapp)}</>}
            </dd>
          </div>
          <div>
            <dt>Serviço</dt>
            <dd>{atual.servico?.nome}</dd>
          </div>
          <div>
            <dt>Origem</dt>
            <dd>{manual ? "Reserva manual (WhatsApp), sem cobrança" : "App"}</dd>
          </div>
          {!manual && atual.cobrancaStatus && (
            <div>
              <dt>Pagamento</dt>
              <dd>{STATUS_DA_COBRANCA[atual.cobrancaStatus] ?? atual.cobrancaStatus}</dd>
            </div>
          )}
          {atual.status === "AGUARDANDO_PAGAMENTO" && atual.expiraEm && (
            <div>
              <dt>Prazo</dt>
              <dd>Aguardando pagamento até {formatarHora(atual.expiraEm)}.</dd>
            </div>
          )}
          {atual.observacao && (
            <div>
              <dt>Observação</dt>
              <dd>{atual.observacao}</dd>
            </div>
          )}
          {atual.motivoCancelamento && (
            <div>
              <dt>Motivo</dt>
              <dd>{atual.motivoCancelamento}</dd>
            </div>
          )}
        </dl>
        {acoesDe(atual, nomeId)}
      </aside>
    );
  }

  /** O dia em grade de horas: linhas de 30 min, almoço listrado, blocos de reserva e o painel de detalhes ao lado. */
  function gradeDoDia(dia: string, itens: AgendamentoAdmin[]) {
    const atuais = itens.map((i) => substituidos[i.id] ?? i);
    const almocos = jornada ? almocosDoDia(jornada, diaIsoDaSemana(dia)) : [];
    const grade = montarGrade(atuais, almocos);
    const confirmados = atuais.filter((i) => i.status === "CONFIRMADO").length;
    const aguardando = atuais.filter((i) => i.status === "AGUARDANDO_PAGAMENTO").length;
    // a ordem de leitura é a de horário: reservas e almoço juntos, como na lista da 7B
    const ordem = [
      ...grade.blocos.map((b) => ({ minutos: emMinutos(b.inicio), bloco: b, almoco: null })),
      ...grade.almocos.map((a) => ({ minutos: emMinutos(a.inicio), bloco: null, almoco: a })),
    ].sort((x, y) => x.minutos - y.minutos || (x.bloco ? -1 : 1));
    return (
      <>
        <p>
          {confirmados} confirmados · {aguardando} aguardando pagamento · linhas de 30 em 30 minutos
        </p>
        <div className="agenda-grade-conteudo">
          <div className="grade" role="list" aria-label="Agenda do dia" data-faixas={grade.faixas}>
            {grade.linhas.map((l) => (
              <Fragment key={l.linha}>
                <span className="grade-hora" aria-hidden="true" data-linha={l.linha}>
                  {l.rotulo}
                </span>
                <span className="grade-trilho" aria-hidden="true" data-linha={l.linha} data-cheia={l.cheia} />
              </Fragment>
            ))}
            {ordem.map((e) => {
              if (e.almoco)
                return (
                  <div
                    key={`almoco-${e.almoco.inicio}`}
                    role="listitem"
                    className="grade-almoco"
                    data-estado="almoco"
                    data-linha={e.almoco.linha}
                    data-span={e.almoco.linhas}
                  >
                    <span>
                      Almoço · {horaCurta(e.almoco.inicio)} às {horaCurta(e.almoco.fim)}
                    </span>
                  </div>
                );
              const { item, linha, linhas, faixa } = e.bloco!;
              const texto = textoDoBloco(item);
              return (
                <div
                  key={item.id}
                  role="listitem"
                  className="grade-item"
                  data-estado={ESTADO_DO_STATUS[item.status]}
                  data-linha={linha}
                  data-span={linhas}
                  data-faixa={faixa}
                >
                  <button
                    type="button"
                    className="grade-bloco"
                    aria-pressed={item.id === selecionadoId}
                    onClick={() => setSelecionadoId(item.id)}
                  >
                    <span className="grade-faixa">
                      <span className="hora">
                        {formatarHora(item.inicio)}–{formatarHora(item.fim)}
                      </span>
                      <SeloDoBloco item={item} />
                    </span>
                    <span className="grade-titulo">
                      <strong>{item.servico?.nome}</strong> · {item.cliente.nome}
                    </span>
                    {texto && <span className="grade-texto">{texto}</span>}
                  </button>
                </div>
              );
            })}
          </div>
          {detalhe(itens)}
        </div>
      </>
    );
  }

  return (
    <>
      <h1>Agenda</h1>
      <div className="agenda-controles">
        <div className="acoes" role="group" aria-label="Navegar no calendário">
          <Botao onClick={() => andar(-1)}>{visao === "DIA" ? "Dia anterior" : "Semana anterior"}</Botao>
          <Botao
            onClick={() => {
              setData(hoje);
              setSelecionadoId(null);
            }}
            disabled={visao === "DIA" ? data === hoje : dias.includes(hoje)}
          >
            Hoje
          </Botao>
          <Botao onClick={() => andar(1)}>{visao === "DIA" ? "Próximo dia" : "Próxima semana"}</Botao>
        </div>
        <div className="acoes" role="group" aria-label="Visão">
          <Botao
            aria-pressed={visao === "DIA"}
            onClick={() => {
              setVisao("DIA");
              setSelecionadoId(null);
            }}
          >
            Dia
          </Botao>
          <Botao
            aria-pressed={visao === "SEMANA"}
            onClick={() => {
              setVisao("SEMANA");
              setSelecionadoId(null);
            }}
          >
            Semana
          </Botao>
        </div>
        {admin && (
          <Selecao
            id="filtro-barbeiro"
            rotulo="Barbeiro"
            value={barbeiroId}
            onChange={(e) => {
              setBarbeiroId(e.target.value);
              setSelecionadoId(null);
            }}
          >
            <option value="">Todos</option>
            {barbeiros.dados?.map((b) => (
              <option key={b.id} value={b.id}>
                {b.nomeExibicao}
              </option>
            ))}
          </Selecao>
        )}
        <Link href="/painel/reserva-manual" className="botao botao-principal">
          Reserva manual
        </Link>
      </div>

      <h2>{titulo}</h2>
      <div aria-live="polite">{mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}</div>
      {envio.erro && !cancelando && !confirmandoFalta && (
        <Aviso tipo="erro" focar>
          {textoDaFalha(envio.erro)}
        </Aviso>
      )}

      <EstadoDaConsulta consulta={agenda}>
        {(itens) => {
          const porDia = (dia: string) => itens.filter((i) => diaNoFuso(new Date(i.inicio)) === dia);
          const comConteudo = dias.filter((d) => porDia(d).length > 0);
          if (comConteudo.length === 0)
            return <p>{visao === "DIA" ? "Sem agendamentos neste dia." : "Sem agendamentos nesta semana."}</p>;
          if (visao === "DIA") return gradeDoDia(data, porDia(data));
          return (
            <>
              {comConteudo.map((dia) => (
                <section key={dia} aria-label={formatarDiaPorExtenso(dia)}>
                  <h3>{formatarDiaPorExtenso(dia)}</h3>
                  <ul className="lista agenda-lista">
                    {entradasDoDia(dia, porDia(dia), jornada).map((e) =>
                      e.tipo === "almoco" ? (
                        <li key={`almoco-${e.inicio}`} className="agenda-almoco" data-estado="almoco">
                          Almoço · {horaCurta(e.inicio)} às {horaCurta(e.fim)}
                        </li>
                      ) : (
                        itemDaLista(e.item)
                      ),
                    )}
                  </ul>
                </section>
              ))}
            </>
          );
        }}
      </EstadoDaConsulta>

      {confirmandoFalta && (
        <Dialogo titulo="Registrar que o cliente não compareceu" aoFechar={() => setConfirmandoFalta(null)}>
          <p>O valor pago fica retido: não há devolução quando o cliente falta.</p>
          {envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />}
          <div className="acoes">
            <Botao variante="principal" disabled={envio.enviando} onClick={confirmarFalta}>
              Confirmar falta
            </Botao>
            <Botao disabled={envio.enviando} onClick={() => setConfirmandoFalta(null)}>
              Voltar
            </Botao>
          </div>
        </Dialogo>
      )}

      {cancelando && (
        <Dialogo titulo="Cancelar pela barbearia" aoFechar={() => setCancelando(null)}>
          <p>
            {cancelando.cobrancaStatus === "PAGA"
              ? "Este agendamento já foi pago: o valor vai para devolução pendente e você registra a devolução em Pendências."
              : "O horário volta a ficar livre. O cliente não é avisado pelo sistema."}
          </p>
          <AreaDeTexto
            id="motivo-cancelamento"
            rotulo="Motivo do cancelamento"
            maxLength={MOTIVO_MAXIMO}
            contador
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            erro={envio.erro?.code === "MOTIVO_OBRIGATORIO" ? mensagemDoErro(envio.erro) : undefined}
          />
          {envio.erro && envio.erro.code !== "MOTIVO_OBRIGATORIO" && (
            <Aviso tipo="erro">{textoDaFalha(envio.erro)}</Aviso>
          )}
          <div className="acoes">
            <Botao variante="principal" disabled={envio.enviando || !motivo.trim()} onClick={confirmarCancelamento}>
              Confirmar cancelamento
            </Botao>
            <Botao disabled={envio.enviando} onClick={() => setCancelando(null)}>
              Voltar
            </Botao>
          </div>
        </Dialogo>
      )}
    </>
  );
}
