"use client";
// Agenda do painel (A45 a A47, V7): lista ordenada por horário com o almoço intercalado, fechar o atendimento e cancelar.
// Cada item sai com data-estado E com o status em texto; a forma de cada estado é do CSS (README, "Padrões da agenda").
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useId, useMemo, useState } from "react";
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

/**
 * O dia vem do endereço (`?data=`) ou é hoje no fuso da barbearia. Entre `/painel?data=X` e `/painel` o Next mantém a
 * mesma tela montada, e o `useState` só olha o valor inicial: sem a `key`, o dia de X ficava preso e o painel abria
 * num dia que não era hoje. A `key` remonta a agenda a cada mudança do `?data=`.
 */
export function PainelAgenda() {
  const doEndereco = useSearchParams().get("data");
  const valido = doEndereco && /^\d{4}-\d{2}-\d{2}$/.test(doEndereco) ? doEndereco : null;
  return <AgendaDoPainel key={valido ?? "hoje"} diaDoEndereco={valido} />;
}

function AgendaDoPainel({ diaDoEndereco }: { diaDoEndereco: string | null }) {
  const sessao = useSessao();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const hoje = diaNoFuso(new Date());
  const [data, setData] = useState(diaDoEndereco ?? hoje);
  const [visao, setVisao] = useState<Visao>("DIA");
  const [barbeiroId, setBarbeiroId] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [substituidos, setSubstituidos] = useState<Record<string, AgendamentoAdmin>>({});
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

  function itemDaAgenda(item: AgendamentoAdmin) {
    const atual = substituidos[item.id] ?? item;
    const pago = atual.origem !== "MANUAL" && atual.cobrancaStatus === "PAGA";
    const podeFechar = atual.status === "CONFIRMADO";
    const podeCancelar = admin && (atual.status === "CONFIRMADO" || atual.status === "AGUARDANDO_PAGAMENTO");
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
        {(podeFechar || podeCancelar) && (
          <div className="acoes">
            {podeFechar && (
              <>
                <Botao
                  variante="principal"
                  disabled={envio.enviando}
                  aria-describedby={nomeId}
                  onClick={() => mudar(atual, "CONCLUIDO")}
                >
                  Concluído
                </Botao>
                <Botao
                  disabled={envio.enviando}
                  aria-describedby={nomeId}
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
                aria-describedby={nomeId}
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
        )}
      </li>
    );
  }

  return (
    <>
      <h1>Agenda</h1>
      <div className="agenda-controles">
        <div className="acoes" role="group" aria-label="Navegar no calendário">
          <Botao onClick={() => andar(-1)}>{visao === "DIA" ? "Dia anterior" : "Semana anterior"}</Botao>
          <Botao onClick={() => setData(hoje)} disabled={visao === "DIA" ? data === hoje : dias.includes(hoje)}>
            Hoje
          </Botao>
          <Botao onClick={() => andar(1)}>{visao === "DIA" ? "Próximo dia" : "Próxima semana"}</Botao>
        </div>
        <div className="acoes" role="group" aria-label="Visão">
          <Botao aria-pressed={visao === "DIA"} onClick={() => setVisao("DIA")}>
            Dia
          </Botao>
          <Botao aria-pressed={visao === "SEMANA"} onClick={() => setVisao("SEMANA")}>
            Semana
          </Botao>
        </div>
        {admin && (
          <Selecao
            id="filtro-barbeiro"
            rotulo="Barbeiro"
            value={barbeiroId}
            onChange={(e) => setBarbeiroId(e.target.value)}
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
          return (
            <>
              {(visao === "DIA" ? dias : comConteudo).map((dia) => (
                <section key={dia} aria-label={visao === "DIA" ? "Agendamentos do dia" : formatarDiaPorExtenso(dia)}>
                  {visao === "SEMANA" && <h3>{formatarDiaPorExtenso(dia)}</h3>}
                  <ul className="lista agenda-lista">
                    {entradasDoDia(dia, porDia(dia), jornada).map((e) =>
                      e.tipo === "almoco" ? (
                        <li key={`almoco-${e.inicio}`} className="agenda-almoco" data-estado="almoco">
                          Almoço · {horaCurta(e.inicio)} às {horaCurta(e.fim)}
                        </li>
                      ) : (
                        itemDaAgenda(e.item)
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
