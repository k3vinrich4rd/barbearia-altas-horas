"use client";
// Agenda do painel (Fatia 14C, seção 8 da spec; antes A45 a A47 da Fatia 7): barra com Hoje, data e barbeiro; calendário do
// mês com a contagem por dia; grade do dia (LIVRE, OCUPADO, ALMOCO, FOLGA) e, abaixo, os agendamentos com as ações de
// fechar e cancelar (as mesmas da 7B). O estado fica na URL (/painel?data=2026-12-01&barbeiro=<id>) para o Voltar e para
// compartilhar entre abas. Cada item sai com data-estado E com o status em texto (README, "Padrões da agenda").
// AGUARDA DESENHO (seção 11 da spec): só componentes e tokens que já existem.
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { diaNoFuso, formatarHora } from "@/api/formato";
import { formatarDiaPorExtenso, mascararWhatsapp } from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { AreaDeTexto, Selecao } from "@/componentes/CamposDoPainel";
import { Dialogo } from "@/componentes/Dialogo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import { useSessao } from "@/componentes/Sessao";
import { TEXTOS } from "@/conteudo/textos";
import { CalendarioDoMes, mesDoDia } from "./CalendarioDoMes";
import { ESTADO_DO_STATUS } from "./estados";
import { GradeDoDiaPainel } from "./GradeDoDiaPainel";
import { STATUS_DA_COBRANCA, type AgendamentoAdmin, type GradeDoDia } from "./tipos";

export { ESTADO_DO_STATUS };

const MOTIVO_MAXIMO = 300;

/** Texto próprio das falhas de fechar e cancelar (A46, A47); o resto vem do catálogo pelo `code`. */
function textoDaFalha(erro: ErroApi): string {
  if (erro.code === "TRANSICAO_INVALIDA") return "Só dá para fechar o atendimento depois do horário de início.";
  if (erro.code === "CONFLITO_DE_VERSAO")
    return "Alguém mexeu neste agendamento antes de você. Atualizamos a agenda: confira e tente de novo.";
  if (erro.code === "ACESSO_NEGADO") return "Só o administrador cancela agendamentos.";
  return mensagemDoErro(erro);
}

const DIA_VALIDO = /^\d{4}-\d{2}-\d{2}$/;

/** A grade com as reservas que mudaram aqui (fechar ou cancelar): cancelada deixa o horário livre, sem ler de novo. */
function comSubstituidos(grade: GradeDoDia, substituidos: Record<string, AgendamentoAdmin>): GradeDoDia {
  return {
    ...grade,
    colunas: grade.colunas.map((c) => ({
      ...c,
      agendamentos: c.agendamentos.map((a) => substituidos[a.id] ?? a),
      horarios: c.horarios.map((h) =>
        h.agendamentoId && substituidos[h.agendamentoId]?.status === "CANCELADO"
          ? { inicio: h.inicio, situacao: "LIVRE" as const, agendamentoId: null }
          : h,
      ),
    })),
  };
}

/**
 * O dia vem do endereço (`?data=`) ou é hoje no fuso da barbearia; o barbeiro do filtro, de `?barbeiro=`. Entre
 * `/painel?data=X` e `/painel` o Next mantém a mesma tela montada: o efeito abaixo traz o dia do endereço de volta.
 */
export function PainelAgenda() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const doEndereco = busca.get("data");
  const diaDoEndereco = doEndereco && DIA_VALIDO.test(doEndereco) ? doEndereco : null;
  const barbeiroDoEndereco = busca.get("barbeiro") ?? "";
  const sessao = useSessao();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const hoje = diaNoFuso(new Date());
  const [data, setData] = useState(diaDoEndereco ?? hoje);
  const [foco, setFoco] = useState(diaDoEndereco ?? hoje);
  const [barbeiroId, setBarbeiroId] = useState(admin ? barbeiroDoEndereco : "");
  const [mensagem, setMensagem] = useState("");
  const [substituidos, setSubstituidos] = useState<Record<string, AgendamentoAdmin>>({});
  const [cancelando, setCancelando] = useState<AgendamentoAdmin | null>(null);
  const [confirmandoFalta, setConfirmandoFalta] = useState<AgendamentoAdmin | null>(null);
  const [motivo, setMotivo] = useState("");
  const envio = useEnvio();
  const tituloId = useId();

  useEffect(() => {
    const dia = diaDoEndereco ?? diaNoFuso(new Date());
    setData(dia);
    setFoco(dia);
  }, [diaDoEndereco]);

  const filtro = admin && barbeiroId ? { barbeiroId } : {};
  const mes = mesDoDia(foco);
  const resumo = useConsulta(
    () => chamar(api().GET("/admin/agenda/mes", { params: { query: { mes, ...filtro } } })),
    [mes, barbeiroId, admin],
  );
  const grade = useConsulta(
    () => chamar(api().GET("/admin/agenda/dia", { params: { query: { data, ...filtro } } })),
    [data, barbeiroId, admin],
  );
  const barbeiros = useConsulta(() => chamar(api().GET("/admin/barbeiros")), [], admin);

  useEffect(() => setSubstituidos({}), [grade.dados]);

  /** Guarda o estado na URL (Voltar e compartilhar entre abas, E18). */
  function irPara(dia: string, barbeiro = barbeiroId) {
    setData(dia);
    setFoco(dia);
    setMensagem("");
    const consulta = new URLSearchParams({ data: dia, ...(admin && barbeiro ? { barbeiro } : {}) });
    roteador.replace(`/painel?${consulta}`, { scroll: false });
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
        resumo.recarregar(); // a contagem do mês muda (cancelar tira do dia); a grade muda aqui mesmo
        setMensagem(
          status === "CONCLUIDO"
            ? "Atendimento concluído."
            : status === "NAO_COMPARECEU"
              ? "Falta registrada. O valor pago fica retido."
              : "Agendamento cancelado. Se havia pagamento, a devolução aparece em Pendências.",
        );
        return novo;
      } catch (e) {
        if (e instanceof ErroApi && e.code === "CONFLITO_DE_VERSAO") grade.recarregar();
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
        <Botao onClick={() => irPara(hoje)} disabled={data === hoje}>
          Hoje
        </Botao>
        <Campo
          id="agenda-data"
          rotulo="Data"
          type="date"
          value={data}
          onChange={(e) => DIA_VALIDO.test(e.target.value) && irPara(e.target.value)}
        />
        {admin && (
          <Selecao
            id="filtro-barbeiro"
            rotulo="Barbeiro"
            value={barbeiroId}
            onChange={(e) => {
              setBarbeiroId(e.target.value);
              irPara(data, e.target.value);
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
          <span aria-hidden="true">+</span> Fechar horário do WhatsApp
        </Link>
      </div>

      <details className="agenda-mes" open>
        <summary>Ver o mês</summary>
        <CalendarioDoMes
          resumo={resumo.dados}
          foco={foco}
          selecionado={data}
          hoje={hoje}
          aoFocar={setFoco}
          aoEscolher={(dia) => irPara(dia)}
        />
      </details>

      <h2 id={`${tituloId}-dia`}>{formatarDiaPorExtenso(data)}</h2>
      <div aria-live="polite">{mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}</div>
      {envio.erro && !cancelando && !confirmandoFalta && (
        <Aviso tipo="erro" focar>
          {textoDaFalha(envio.erro)}
        </Aviso>
      )}

      <EstadoDaConsulta consulta={grade}>
        {(dia) => {
          const atual = comSubstituidos(dia, substituidos);
          const itens = atual.colunas
            .flatMap((c) => c.agendamentos)
            .sort((a, b) => a.inicio.localeCompare(b.inicio) || a.id.localeCompare(b.id));
          return (
            <>
              <section aria-labelledby={`${tituloId}-dia`} className="agenda-grade">
                <GradeDoDiaPainel grade={atual} admin={admin} />
              </section>
              <section aria-label="Agendamentos do dia">
                <h3>Agendamentos do dia</h3>
                {itens.length === 0 ? (
                  <p>{TEXTOS.painelSemClientes}</p>
                ) : (
                  <ul className="lista agenda-lista">{itens.map((item) => itemDaAgenda(item))}</ul>
                )}
              </section>
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
