"use client";
// Férias e folgas em página própria do painel (Fatia 15, C13; ADR 0015). Só ADMIN. O dono marca férias ou folga, VÊ ANTES de
// salvar quem está marcado no período (prévia GET .../folgas/conflitos, E24) e decide: manter as reservas
// (`manterReservas: true`), cancelar uma por uma pela barbearia, ou trocar as datas. Nada é cancelado sozinho. O 422
// FOLGA_COM_RESERVAS com `conflitos[]` só aparece se alguém marcou entre a prévia e o salvar (rede de segurança).
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import type { ErroApi } from "@/api/erro";
import { diaNoFuso, formatarDataCurta, formatarDataEHora, formatarDiaDaSemana, somarDias } from "@/api/formato";
import { instanteNoFuso } from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { MENSAGENS, mensagemDoErro } from "@/api/mensagens";
import type { components } from "@/api/schema";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { AreaDeTexto, Selecao } from "@/componentes/CamposDoPainel";
import { Dialogo } from "@/componentes/Dialogo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { EtiquetaDeStatus } from "@/componentes/EtiquetaDeStatus";
import type { BarbeiroAdmin, Folga } from "./tipos";

type Conflito = components["schemas"]["ConflitoDeFolga"];
type FolgaSalva = components["schemas"]["FolgaSalva"];
type Tipo = "FOLGA" | "FERIAS";

const MOTIVO_MAXIMO = 200;
const MOTIVO_DO_CANCELAMENTO_MAXIMO = 300;
/** A prévia espera o dono parar de mexer nas datas (e o leitor de tela não fala a cada tecla). */
const ESPERA_DA_PREVIA_MS = 500;
const ORIGEM: Record<Conflito["origem"], string> = {
  CLIENTE: "cliente com conta",
  GUEST: "visitante",
  MANUAL: "manual",
};

const nomeDoTipo = (t: Folga["tipo"]) => (t === "FERIAS" ? "Férias" : "Folga");
const ehMeiaNoite = (iso: string) => iso.slice(11, 16) === "00:00";
const diaDe = (iso: string) => diaNoFuso(new Date(iso));
/** "sexta, 27/11": o último dia de um período de dias inteiros (o fim é a meia-noite do dia seguinte, [inicio, fim)). */
const ultimoDia = (fimIso: string) => {
  const meio = instanteNoFuso(somarDias(diaDe(fimIso), -1), "12:00");
  return `${formatarDiaDaSemana(meio)}, ${formatarDataCurta(meio).slice(0, 5)}`;
};
const diaPorExtenso = (iso: string) => `${formatarDiaDaSemana(iso)}, ${formatarDataCurta(iso).slice(0, 5)}`;

/** "de quarta, 25/11 até sexta, 27/11 (o dia inteiro)" ou "de quarta, 25/11 às 09:00 até quarta, 25/11 às 12:00". */
export function descreverPeriodo(f: { inicio: string; fim: string }) {
  if (ehMeiaNoite(f.inicio) && ehMeiaNoite(f.fim))
    return `de ${diaPorExtenso(f.inicio)} até ${ultimoDia(f.fim)} (o dia inteiro)`;
  return `de ${formatarDataEHora(f.inicio)} até ${formatarDataEHora(f.fim)}`;
}

const clientesMarcados = (n: number) =>
  n === 0
    ? "Nenhum cliente marcado nesse período."
    : `${n} ${n === 1 ? "cliente marcado" : "clientes marcados"} nesse período`;

function conflitosDoErro(e: ErroApi | undefined): Conflito[] | undefined {
  if (e?.code !== "FOLGA_COM_RESERVAS") return undefined;
  const lista = e.extensoes.conflitos;
  return Array.isArray(lista) ? (lista as Conflito[]) : [];
}

export function PainelFolgas() {
  const busca = useSearchParams();
  const barbeiros = useConsulta(() => chamar(api().GET("/admin/barbeiros")), []);
  const [escolhido, setEscolhido] = useState(busca.get("barbeiro") ?? "");
  return (
    <>
      <p className="sobretitulo">Painel</p>
      <h1>Férias e folgas</h1>
      <EstadoDaConsulta consulta={barbeiros}>
        {(lista) => {
          const barbeiro = lista.find((b) => b.id === escolhido) ?? lista[0];
          if (!barbeiro) return <p>Nenhum barbeiro cadastrado.</p>;
          return (
            <>
              {lista.length > 1 && (
                <Selecao
                  id="folgas-barbeiro"
                  rotulo="Barbeiro"
                  value={barbeiro.id}
                  onChange={(e) => setEscolhido(e.target.value)}
                >
                  {lista.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.nomeExibicao}
                    </option>
                  ))}
                </Selecao>
              )}
              <FolgasDoBarbeiro key={barbeiro.id} barbeiro={barbeiro} />
            </>
          );
        }}
      </EstadoDaConsulta>
    </>
  );
}

function FolgasDoBarbeiro({ barbeiro }: { barbeiro: BarbeiroAdmin }) {
  const lista = useConsulta(
    () => chamar(api().GET("/admin/barbeiros/{id}/folgas", { params: { path: { id: barbeiro.id } } })),
    [barbeiro.id],
  );
  const [formulario, setFormulario] = useState<Folga | "nova" | null>(null);
  const [salva, setSalva] = useState<{ folga: FolgaSalva; editada: boolean } | null>(null);
  const [removendo, setRemovendo] = useState<Folga | null>(null);
  const [removida, setRemovida] = useState(false);
  const envio = useEnvio();

  function abrir(f: Folga | "nova") {
    setSalva(null);
    setRemovida(false);
    setFormulario(f);
  }

  async function remover() {
    if (!removendo) return;
    const alvo = removendo;
    const feito = await envio.executar(async () => {
      await chamar(
        api().DELETE("/admin/barbeiros/{id}/folgas/{folgaId}", {
          params: { path: { id: barbeiro.id, folgaId: alvo.id } },
        }),
      );
      return true;
    });
    if (feito) {
      setRemovendo(null);
      setSalva(null);
      setRemovida(true);
      lista.recarregar();
    }
  }

  const mantidas = salva?.folga.reservasMantidas ?? [];
  return (
    <div className="folgas-painel">
      <section aria-labelledby="t-folgas-lista" className="folgas-lista">
        <h2 id="t-folgas-lista">De hoje em diante</h2>
        {salva && (
          <Aviso tipo="sucesso">
            {salva.editada ? "Folga alterada." : "Folga lançada."}
            {mantidas.length > 0 &&
              ` ${mantidas.length} ${mantidas.length === 1 ? "cliente continua marcado" : "clientes continuam marcados"} nesse período.`}
          </Aviso>
        )}
        {mantidas.length > 0 && (
          <ListaDeConflitos
            conflitos={mantidas}
            rotulo="Reservas mantidas"
            acao={(c) => (
              <Link href={`/painel?data=${diaDe(c.inicio)}&barbeiro=${barbeiro.id}`}>
                Ver na agenda<span className="so-leitor"> a reserva de {c.clienteNome}</span>
              </Link>
            )}
          />
        )}
        {removida && <Aviso tipo="sucesso">Folga removida.</Aviso>}
        <Botao variante="principal" onClick={() => abrir("nova")}>
          Marcar férias ou folga
        </Botao>
        <EstadoDaConsulta consulta={lista}>
          {(folgas) =>
            folgas.length === 0 ? (
              <p>Nenhuma folga de hoje em diante.</p>
            ) : (
              <ul className="lista folgas-cartoes">
                {folgas.map((f) => (
                  <li key={f.id} className="item">
                    <p>
                      <span className="etiqueta">{nomeDoTipo(f.tipo)}</span> {descreverPeriodo(f)}
                      {f.motivo ? ` · ${f.motivo}` : ""}
                    </p>
                    <div className="acoes">
                      <Botao aria-label={`Editar ${descreverPeriodo(f)}`} onClick={() => abrir(f)}>
                        Editar
                      </Botao>
                      <Botao
                        aria-label={`Remover ${descreverPeriodo(f)}`}
                        onClick={() => {
                          envio.limparErro();
                          setRemovendo(f);
                        }}
                      >
                        Remover
                      </Botao>
                    </div>
                  </li>
                ))}
              </ul>
            )
          }
        </EstadoDaConsulta>
      </section>

      {formulario && (
        <FormularioDeFolga
          key={formulario === "nova" ? "nova" : formulario.id}
          barbeiro={barbeiro}
          folga={formulario === "nova" ? undefined : formulario}
          aoSalvar={(folga, editada) => {
            setFormulario(null);
            setSalva({ folga, editada });
            lista.recarregar();
          }}
          aoFechar={() => setFormulario(null)}
        />
      )}

      {removendo && (
        <Dialogo titulo="Remover folga" aoFechar={() => setRemovendo(null)}>
          <p>Os horários voltam a aceitar reservas. Remover {descreverPeriodo(removendo)}?</p>
          {envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />}
          <div className="acoes">
            <Botao variante="principal" disabled={envio.enviando} onClick={remover}>
              Remover folga
            </Botao>
            <Botao disabled={envio.enviando} onClick={() => setRemovendo(null)}>
              Voltar
            </Botao>
          </div>
        </Dialogo>
      )}
    </div>
  );
}

function ListaDeConflitos({
  conflitos,
  rotulo,
  acao,
}: {
  conflitos: Conflito[];
  rotulo: string;
  acao?: (c: Conflito) => React.ReactNode;
}) {
  return (
    <ul className="lista folgas-conflitos" aria-label={rotulo}>
      {conflitos.map((c) => (
        <li key={c.reservaId} className="item">
          <p>
            {formatarDataEHora(c.inicio)} · {c.servico.nome} · {c.clienteNome} · {ORIGEM[c.origem]}{" "}
            <EtiquetaDeStatus status={c.status} />
          </p>
          {acao?.(c)}
        </li>
      ))}
    </ul>
  );
}

/** O período escolhido no formulário, já no formato da API, ou o motivo de ainda não ter um. */
function periodoDoFormulario(f: { diaInteiro: boolean; de: string; ate: string; horaDe: string; horaAte: string }) {
  if (!f.de || !f.ate || (!f.diaInteiro && (!f.horaDe || !f.horaAte))) return { erro: "" };
  const inicio = f.diaInteiro ? instanteNoFuso(f.de, "00:00") : instanteNoFuso(f.de, f.horaDe);
  const fim = f.diaInteiro ? instanteNoFuso(somarDias(f.ate, 1), "00:00") : instanteNoFuso(f.ate, f.horaAte);
  if (new Date(fim) <= new Date(inicio)) return { erro: "O fim da folga deve ser depois do início." };
  return { inicio, fim };
}

function FormularioDeFolga({
  barbeiro,
  folga,
  aoSalvar,
  aoFechar,
}: {
  barbeiro: BarbeiroAdmin;
  folga?: Folga;
  aoSalvar: (f: FolgaSalva, editada: boolean) => void;
  aoFechar: () => void;
}) {
  const inteiro = !folga || (ehMeiaNoite(folga.inicio) && ehMeiaNoite(folga.fim));
  const [tipo, setTipo] = useState<Tipo>(folga?.tipo ?? "FOLGA");
  const [diaInteiro, setDiaInteiro] = useState(inteiro);
  const [de, setDe] = useState(folga ? diaDe(folga.inicio) : "");
  const [ate, setAte] = useState(folga ? (inteiro ? somarDias(diaDe(folga.fim), -1) : diaDe(folga.fim)) : "");
  const [horaDe, setHoraDe] = useState(folga && !inteiro ? folga.inicio.slice(11, 16) : "");
  const [horaAte, setHoraAte] = useState(folga && !inteiro ? folga.fim.slice(11, 16) : "");
  const [motivo, setMotivo] = useState(folga?.motivo ?? "");
  const [tentou, setTentou] = useState(false);
  const [previa, setPrevia] = useState<{ chave: string; conflitos: Conflito[] } | null>(null);
  const [versaoDaPrevia, setVersaoDaPrevia] = useState(0);
  const [cancelarUmAUm, setCancelarUmAUm] = useState(false);
  const [cancelando, setCancelando] = useState<Conflito | null>(null);
  const envio = useEnvio();

  const periodo = periodoDoFormulario({ diaInteiro, de, ate, horaDe, horaAte });
  const chave = "inicio" in periodo ? `${periodo.inicio}|${periodo.fim}` : "";
  const motivoLongo = motivo.trim().length > MOTIVO_MAXIMO;

  // FF-10: a prévia sai assim que as datas ficam válidas, antes de salvar
  useEffect(() => {
    if (!("inicio" in periodo)) return;
    let vivo = true;
    const t = setTimeout(() => {
      chamar(
        api().GET("/admin/barbeiros/{id}/folgas/conflitos", {
          params: { path: { id: barbeiro.id }, query: { inicio: periodo.inicio!, fim: periodo.fim! } },
        }),
      ).then(
        (conflitos) => vivo && setPrevia({ chave, conflitos }),
        () => vivo && setPrevia(null),
      );
    }, ESPERA_DA_PREVIA_MS);
    return () => {
      vivo = false;
      clearTimeout(t);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave, barbeiro.id, versaoDaPrevia]);

  // o 422 (alguém marcou entre a prévia e o salvar) traz a lista nova, que vale no lugar da prévia
  const doErro = conflitosDoErro(envio.erro);
  const conflitos = doErro ?? (previa?.chave === chave ? previa.conflitos : undefined);
  // com o 422 vale decidir mesmo se a lista não veio (API de antes da 1.12.0, sem `conflitos[]`)
  const comClientes = (conflitos?.length ?? 0) > 0 || doErro !== undefined;

  async function salvar(manterReservas: boolean) {
    setTentou(true);
    if (!("inicio" in periodo) || motivoLongo) return;
    const corpo = {
      inicio: periodo.inicio!,
      fim: periodo.fim!,
      tipo,
      ...(motivo.trim() ? { motivo: motivo.trim() } : {}),
      ...(manterReservas ? { manterReservas: true } : {}),
    };
    const resposta = await envio.executar(() =>
      chamar(
        folga
          ? api().PUT("/admin/barbeiros/{id}/folgas/{folgaId}", {
              params: { path: { id: barbeiro.id, folgaId: folga.id } },
              body: corpo,
            })
          : api().POST("/admin/barbeiros/{id}/folgas", { params: { path: { id: barbeiro.id } }, body: corpo }),
      ),
    );
    if (resposta) aoSalvar(resposta, Boolean(folga));
  }

  function enviar(e: FormEvent) {
    e.preventDefault();
    void salvar(false);
  }

  const erroDoPeriodo = "erro" in periodo && (periodo.erro || (tentou ? "Informe o início e o fim." : ""));
  return (
    <section aria-labelledby="t-folga-form" className="folgas-formulario">
      <h2 id="t-folga-form">{folga ? "Editar férias ou folga" : "Marcar férias ou folga"}</h2>
      <form onSubmit={enviar} noValidate aria-labelledby="t-folga-form">
        <div role="group" aria-label="Tipo" className="acoes">
          {(["FOLGA", "FERIAS"] as const).map((t) => (
            <Botao key={t} aria-pressed={tipo === t} onClick={() => setTipo(t)}>
              {nomeDoTipo(t)}
            </Botao>
          ))}
        </div>
        <label className="opcao">
          <input type="checkbox" checked={diaInteiro} onChange={(e) => setDiaInteiro(e.target.checked)} /> Dia inteiro
        </label>
        <Campo id="ff-de" rotulo="De" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        {!diaInteiro && (
          <Campo
            id="ff-hora-de"
            rotulo="Hora do início"
            type="time"
            value={horaDe}
            onChange={(e) => setHoraDe(e.target.value)}
          />
        )}
        <Campo
          id="ff-ate"
          rotulo="Até"
          type="date"
          value={ate}
          onChange={(e) => setAte(e.target.value)}
          ajuda={
            diaInteiro && ate
              ? `até ${ultimoDia(instanteNoFuso(somarDias(ate, 1), "00:00"))} (o dia inteiro)`
              : undefined
          }
          erro={diaInteiro ? erroDoPeriodo || undefined : undefined}
        />
        {!diaInteiro && (
          <Campo
            id="ff-hora-ate"
            rotulo="Hora do fim"
            type="time"
            value={horaAte}
            onChange={(e) => setHoraAte(e.target.value)}
            erro={erroDoPeriodo || undefined}
          />
        )}
        <AreaDeTexto
          id="ff-motivo"
          rotulo="Motivo (opcional)"
          maxLength={MOTIVO_MAXIMO}
          contador
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          ajuda="Só você vê. Não escreva dado de cliente aqui."
          erro={motivoLongo ? `O motivo pode ter até ${MOTIVO_MAXIMO} caracteres.` : undefined}
        />

        <div className="folgas-previa">
          <p role="status">
            {conflitos && !(doErro && conflitos.length === 0) ? clientesMarcados(conflitos.length) : ""}
          </p>
          {doErro && <Aviso tipo="erro">{MENSAGENS.FOLGA_COM_RESERVAS}</Aviso>}
          {(conflitos?.length ?? 0) > 0 && (
            <ListaDeConflitos
              conflitos={conflitos!}
              rotulo="Clientes marcados no período"
              acao={
                cancelarUmAUm
                  ? (c) => (
                      <Botao
                        aria-label={`Cancelar pela barbearia a reserva de ${c.clienteNome}`}
                        onClick={() => setCancelando(c)}
                      >
                        Cancelar pela barbearia
                      </Botao>
                    )
                  : undefined
              }
            />
          )}
        </div>

        {envio.erro && !doErro && <AvisoDeErro erro={envio.erro} focar={false} />}
        <div className="acoes">
          {comClientes ? (
            <>
              <Botao variante="principal" disabled={envio.enviando} onClick={() => void salvar(true)}>
                Salvar e manter os horários
              </Botao>
              <Botao aria-pressed={cancelarUmAUm} onClick={() => setCancelarUmAUm((v) => !v)}>
                Cancelar um a um
              </Botao>
              <Botao onClick={() => document.getElementById("ff-de")?.focus()}>Trocar as datas</Botao>
            </>
          ) : (
            <Botao type="submit" variante="principal" disabled={envio.enviando}>
              Salvar
            </Botao>
          )}
          <Botao onClick={aoFechar}>Voltar</Botao>
        </div>
      </form>

      {cancelando && (
        <CancelarReserva
          conflito={cancelando}
          barbeiroId={barbeiro.id}
          aoFechar={() => setCancelando(null)}
          aoCancelar={() => {
            setCancelando(null);
            envio.limparErro();
            setVersaoDaPrevia((v) => v + 1);
          }}
        />
      )}
    </section>
  );
}

/**
 * "Cancelar pela barbearia" de uma reserva da prévia: a mesma chamada da agenda do painel (PATCH .../status com motivo).
 * O `version` da reserva vem da grade do dia dela, lida na hora (a prévia não traz versão).
 */
function CancelarReserva({
  conflito,
  barbeiroId,
  aoFechar,
  aoCancelar,
}: {
  conflito: Conflito;
  barbeiroId: string;
  aoFechar: () => void;
  aoCancelar: () => void;
}) {
  const [motivo, setMotivo] = useState("");
  const envio = useEnvio();

  async function confirmar() {
    if (!motivo.trim()) return;
    const feito = await envio.executar(async () => {
      const grade = await chamar(
        api().GET("/admin/agenda/dia", { params: { query: { data: diaDe(conflito.inicio), barbeiroId } } }),
      );
      const reserva = grade.colunas.flatMap((c) => c.agendamentos).find((a) => a.id === conflito.reservaId);
      await chamar(
        api().PATCH("/admin/agendamentos/{id}/status", {
          params: { path: { id: conflito.reservaId } },
          body: { status: "CANCELADO", version: reserva?.version ?? 0, motivo: motivo.trim() },
        }),
      );
      return true;
    });
    if (feito) aoCancelar();
  }

  return (
    <Dialogo titulo="Cancelar pela barbearia" aoFechar={aoFechar}>
      <p>
        {conflito.clienteNome}, {formatarDataEHora(conflito.inicio)}. O horário volta a ficar livre e o cliente não é
        avisado pelo sistema. Se já estava pago, o valor vai para devolução pendente em Pendências.
      </p>
      <AreaDeTexto
        id="ff-motivo-cancelamento"
        rotulo="Motivo do cancelamento"
        maxLength={MOTIVO_DO_CANCELAMENTO_MAXIMO}
        contador
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
      />
      {envio.erro && <Aviso tipo="erro">{mensagemDoErro(envio.erro)}</Aviso>}
      <div className="acoes">
        <Botao variante="principal" disabled={envio.enviando || !motivo.trim()} onClick={confirmar}>
          Confirmar cancelamento
        </Botao>
        <Botao disabled={envio.enviando} onClick={aoFechar}>
          Voltar
        </Botao>
      </div>
    </Dialogo>
  );
}
