"use client";
// Passo 1 da reserva em poucos toques (Fatia 12C, spec 4.2): serviço, (barbeiro só se houver mais de um), tira de 7 dias
// com o próximo dia livre já escolhido, horários em chips e resumo fixo com "Continuar". Horários no fuso da barbearia,
// agrupados por barbeiro. A rota e a lógica seguem as mesmas: /agendar → /agendar/dados → pagamento → confirmação.
import { useRouter, useSearchParams } from "next/navigation";
import { Fragment, useEffect, useRef, useState } from "react";
import { api, chamar } from "@/api/cliente";
import {
  diaNoFuso,
  formatarDataCurta,
  formatarDiaDaSemana,
  formatarHora,
  formatarPreco,
  FUSO_PADRAO,
  somarDias,
} from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { ContatoWhatsapp } from "@/componentes/ContatoWhatsapp";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { IndicadorDePassos } from "@/componentes/IndicadorDePassos";
import { buscarServicos } from "@/componentes/ListaDeServicos";
import { TEXTOS } from "@/conteudo/textos";

const DIAS_DA_TIRA = 7;
/** Quantos dias "Ver o próximo dia com horário" olha por vez. É o limite da BUSCA, não regra da agenda (D197): sem horário nesse trecho, a pessoa procura mais. */
const DIAS_DA_BUSCA = 30;

const buscarDisponibilidade = (servicoId: string, data: string, barbeiroId: string) =>
  chamar(
    api().GET("/disponibilidade", { params: { query: { servicoId, data, ...(barbeiroId ? { barbeiroId } : {}) } } }),
  );

type Disponibilidade = Awaited<ReturnType<typeof buscarDisponibilidade>>;

const totalDeHorarios = (d: Disponibilidade) => d.barbeiros.reduce((n, b) => n + b.horarios.length, 0);

/**
 * C8 (Fatia 15, E16 a E19): o dia está "Fechado" quando TODOS os barbeiros da resposta têm `expediente.aberto: false` (com o
 * barbeiro escolhido, a resposta só traz ele). Sem `expediente` (API antiga, campo aditivo) nunca fecha: a tela fica como antes.
 * Folga de parte do dia não fecha o dia (E18): ele continua aberto, só com menos horários, e pode ficar "sem horário".
 */
const diaFechado = (d: Disponibilidade) =>
  d.barbeiros.length > 0 && d.barbeiros.every((b) => b.expediente !== undefined && !b.expediente.aberto);

/** Como o dia aparece na tira: quantos horários livres e se está fechado. */
interface ResumoDoDia {
  livres: number;
  fechado: boolean;
}

/** "12h", "13h30": a hora da faixa "Fechado · 12h às 13h", do campo `almoco` (nada fixo no código). */
function horaCurta(iso: string, fuso: string) {
  const [h, m] = formatarHora(iso, fuso).split(":");
  return `${Number(h)}h${m === "00" ? "" : m}`;
}

/** Livres de cada dia da tira (null = não deu para saber). Uma consulta por dia, ao escolher o serviço. */
function useSemana(servicoId: string, barbeiroId: string, dias: string[]) {
  const [lido, setLido] = useState<{ chave: string; porDia: Record<string, ResumoDoDia | null> }>();
  const chave = `${servicoId}|${barbeiroId}|${dias[0]}`;
  useEffect(() => {
    if (!servicoId) return;
    let vivo = true;
    Promise.all(
      dias.map((d) =>
        buscarDisponibilidade(servicoId, d, barbeiroId).then(
          (r): ResumoDoDia => ({ livres: totalDeHorarios(r), fechado: diaFechado(r) }),
          () => null,
        ),
      ),
    ).then((contagens) => {
      if (vivo) setLido({ chave, porDia: Object.fromEntries(dias.map((d, i) => [d, contagens[i]])) });
    });
    return () => {
      vivo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave]);
  return lido?.chave === chave ? lido.porDia : undefined;
}

/** Entre o primeiro e o último horário livre, o que falta na grade (almoço, ocupado) vira chip indisponível. */
function comIndisponiveis(horarios: string[]): { inicio: string; livre: boolean }[] {
  const tempos = horarios.map((h) => new Date(h).getTime());
  const passos = tempos.slice(1).map((t, i) => t - tempos[i]);
  const passo = passos.length > 0 ? Math.min(...passos) : 0;
  const livres = new Set(horarios);
  if (passo <= 0) return horarios.map((inicio) => ({ inicio, livre: true }));
  const saida: { inicio: string; livre: boolean }[] = [];
  for (let t = tempos[0], n = 0; t <= tempos[tempos.length - 1] && n < 96; t += passo, n++) {
    const livre = horarios.find((h) => new Date(h).getTime() === t);
    saida.push(
      livre && livres.has(livre) ? { inicio: livre, livre: true } : { inicio: new Date(t).toISOString(), livre: false },
    );
  }
  return saida;
}

/** M1 (Fatia 14D): Manhã antes das 12:00, Tarde de 12:00 a 17:59, Noite a partir das 18:00. */
const PERIODOS = ["Manhã", "Tarde", "Noite"] as const;
const periodoDe = (hhmm: string) => (hhmm < "12:00" ? "Manhã" : hhmm < "18:00" ? "Tarde" : "Noite");

const rotuloDoDia = (dia: string) => {
  const meio = `${dia}T12:00:00Z`;
  return `${formatarDiaDaSemana(meio, "UTC").slice(0, 3)} ${Number(dia.slice(8, 10))}`;
};

export function Agendar() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const [servicoId, setServicoId] = useState(busca.get("servicoId") ?? "");
  const [barbeiroId, setBarbeiroId] = useState(busca.get("barbeiroId") ?? "");
  const [data, setData] = useState(busca.get("data") ?? "");
  const [escolhido, setEscolhido] = useState<{ chave: string; barbeiroId: string; inicio: string }>();
  const [procurando, setProcurando] = useState(false);
  /** Até que dia a busca já olhou, quando não achou horário ("Procurar mais 30 dias" continua dali). */
  const [buscadoAte, setBuscadoAte] = useState<string | undefined>();
  const diaEscolhidoPelaPessoa = useRef(Boolean(busca.get("data")));
  const ocupado = busca.get("ocupado") ?? undefined;
  /** M3: "Alterar" no resumo de /agendar/dados volta com o horário já marcado. */
  const inicioDaUrl = useRef(busca.get("inicio"));
  const [proximo, setProximo] = useState<{ chave: string; dia: string; barbeiroId: string; inicio: string }>();

  const hoje = diaNoFuso(new Date());
  // D197: a agenda não tem antecedência mínima nem limite de dias à frente; o seletor só impede o que já passou.
  const dataValida = data !== "" && data >= hoje;
  const tira = Array.from({ length: DIAS_DA_TIRA }, (_, i) => somarDias(hoje, i));

  const servicos = useConsulta(buscarServicos, []);
  const barbeiros = useConsulta(() => chamar(api().GET("/barbeiros")), []);
  // com um só barbeiro ativo, a escolha some e a agenda é a dele
  const unico = barbeiros.dados?.length === 1;
  const barbeiroEfetivo = unico ? "" : barbeiroId;

  const disponibilidade = useConsulta(
    () => buscarDisponibilidade(servicoId, data, barbeiroEfetivo),
    [servicoId, data, barbeiroEfetivo],
    Boolean(servicoId) && dataValida,
  );
  const semana = useSemana(servicoId, barbeiroEfetivo, tira);

  // o próximo dia que tem horário já vem escolhido (a pessoa só troca se quiser)
  useEffect(() => {
    if (diaEscolhidoPelaPessoa.current || data || !semana) return;
    const primeiro = tira.find((d) => (semana[d]?.livres ?? 0) > 0);
    if (primeiro) setData(primeiro);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semana]);

  // M2 (Fatia 14D): o primeiro horário livre do primeiro dia da tira com horário, para marcar em um toque
  const chaveDoProximo = `${servicoId}|${barbeiroEfetivo}`;
  useEffect(() => {
    if (!servicoId || !semana) return;
    const dia = tira.find((d) => (semana[d]?.livres ?? 0) > 0);
    if (!dia) return;
    let vivo = true;
    buscarDisponibilidade(servicoId, dia, barbeiroEfetivo).then(
      (r) => {
        const primeiro = r.barbeiros
          .flatMap((b) => b.horarios.map((inicio) => ({ barbeiroId: b.barbeiroId, inicio })))
          .sort((a, b) => new Date(a.inicio).getTime() - new Date(b.inicio).getTime())[0];
        if (vivo && primeiro) setProximo({ chave: chaveDoProximo, dia, ...primeiro });
      },
      () => undefined,
    );
    return () => {
      vivo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semana]);

  function escolherDia(dia: string) {
    diaEscolhidoPelaPessoa.current = true;
    setBuscadoAte(undefined);
    setData(dia);
  }

  async function procurarProximoDiaComHorario(depoisDe: string) {
    setProcurando(true);
    setBuscadoAte(undefined);
    const ultimo = somarDias(depoisDe, DIAS_DA_BUSCA);
    for (let d = somarDias(depoisDe, 1); d <= ultimo; d = somarDias(d, 1)) {
      try {
        if (totalDeHorarios(await buscarDisponibilidade(servicoId, d, barbeiroEfetivo)) > 0) {
          setProcurando(false);
          escolherDia(d);
          return;
        }
      } catch {
        /* um dia que falha não impede de procurar nos outros */
      }
    }
    setProcurando(false);
    setBuscadoAte(ultimo);
  }

  const servico = servicos.dados?.find((s) => s.id === servicoId);
  const chave = `${servicoId}|${data}|${barbeiroEfetivo}`;
  const sel = escolhido?.chave === chave ? escolhido : undefined;
  const barbeiroEscolhido = barbeiros.dados?.find((b) => b.id === barbeiroId);
  const fusoDoDia = disponibilidade.dados?.fuso ?? FUSO_PADRAO;

  useEffect(() => {
    const inicio = inicioDaUrl.current;
    if (!inicio || !disponibilidade.dados) return;
    const dono = disponibilidade.dados.barbeiros.find((b) => b.horarios.includes(inicio));
    if (dono) setEscolhido({ chave, barbeiroId: dono.barbeiroId, inicio });
    inicioDaUrl.current = null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disponibilidade.dados]);

  const proximoLivre = proximo?.chave === chaveDoProximo ? proximo : undefined;
  function marcarProximo() {
    if (!proximoLivre) return;
    escolherDia(proximoLivre.dia);
    setEscolhido({
      chave: `${servicoId}|${proximoLivre.dia}|${barbeiroEfetivo}`,
      barbeiroId: proximoLivre.barbeiroId,
      inicio: proximoLivre.inicio,
    });
  }

  function continuar() {
    if (!sel) return;
    const p = new URLSearchParams({ servicoId, barbeiroId: sel.barbeiroId, inicio: sel.inicio });
    roteador.push(`/agendar/dados?${p.toString()}`);
  }

  return (
    <>
      <IndicadorDePassos passoAtual={1} />
      <p className="sobretitulo">Agenda · Eduardo Candido</p>
      <h1>Agende seu horário</h1>
      {proximoLivre && (
        <p className="proximo-livre">
          <Botao onClick={marcarProximo}>
            Próximo livre: {formatarDiaDaSemana(proximoLivre.inicio, fusoDoDia).slice(0, 3)}{" "}
            {formatarDataCurta(proximoLivre.inicio, fusoDoDia).slice(0, 5)} às{" "}
            {formatarHora(proximoLivre.inicio, fusoDoDia)}
          </Botao>
        </p>
      )}
      {ocupado && <Aviso tipo="aviso">{mensagemDoErro({ code: "HORARIO_INDISPONIVEL", status: 409 })}</Aviso>}

      <section aria-labelledby="passo-servico">
        <h2 id="passo-servico">1 · Serviço</h2>
        <div className="reserva-servicos">
          <EstadoDaConsulta consulta={servicos}>
            {(lista) =>
              lista.length === 0 ? (
                <p>Sem serviços no momento.</p>
              ) : (
                <div role="radiogroup" aria-labelledby="passo-servico" className="lista opcoes">
                  {lista.map((s) => (
                    <label key={s.id} className="opcao">
                      <input
                        type="radio"
                        name="servico"
                        value={s.id}
                        checked={servicoId === s.id}
                        onChange={() => setServicoId(s.id)}
                      />
                      <span>
                        <strong>{s.nome}</strong>
                        <br />
                        <span className="hora">{s.duracaoMin} min</span> ·{" "}
                        <span className="preco">{formatarPreco(s)}</span>
                      </span>
                    </label>
                  ))}
                </div>
              )
            }
          </EstadoDaConsulta>
        </div>
      </section>

      {barbeiros.dados && barbeiros.dados.length > 1 && (
        <section aria-labelledby="passo-barbeiro">
          <h2 id="passo-barbeiro">2 · Barbeiro</h2>
          <div role="radiogroup" aria-labelledby="passo-barbeiro" className="lista opcoes barbeiros-cartoes">
            {[{ id: "", nomeExibicao: "Qualquer barbeiro" }, ...barbeiros.dados].map((b) => (
              <label key={b.id || "qualquer"} className="opcao">
                <input
                  type="radio"
                  name="barbeiro"
                  value={b.id}
                  checked={barbeiroId === b.id}
                  onChange={() => setBarbeiroId(b.id)}
                />
                <span>
                  <strong>{b.nomeExibicao}</strong>
                </span>
              </label>
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="passo-dia">
        <h2 id="passo-dia">{unico || !barbeiros.dados ? "2" : "3"} · Dia</h2>
        <div role="group" aria-label="Próximos dias" className="dias-tira">
          {tira.map((dia) => {
            const resumo = semana?.[dia];
            if (resumo?.fechado)
              // AF-10: "Fechado" não é "sem horário": não responde ao toque e não diz o motivo
              return (
                <button
                  key={dia}
                  type="button"
                  className="dia-chip dia-chip-fechado"
                  aria-disabled="true"
                  data-livre="fechado"
                  onClick={(e) => e.preventDefault()}
                >
                  {/* o nome acessível é o texto visível ("dom 6, Fechado"), sem aria-label diferente (WCAG 2.5.3) */}
                  <span>
                    {rotuloDoDia(dia)}
                    <span className="so-leitor">,</span>
                  </span>
                  <span className="dia-chip-nota">Fechado</span>
                </button>
              );
            const livres = resumo?.livres;
            return (
              <button
                key={dia}
                type="button"
                className="dia-chip"
                aria-pressed={data === dia}
                data-livre={livres === undefined ? "desconhecido" : livres > 0 ? "sim" : "nao"}
                onClick={() => escolherDia(dia)}
              >
                <span>{rotuloDoDia(dia)}</span>
                <span className="dia-chip-nota">
                  {livres === undefined ? "" : livres > 0 ? "livre" : "sem horário"}
                </span>
              </button>
            );
          })}
        </div>
        <div className="campo">
          <label htmlFor="data">Outro dia</label>
          <input
            id="data"
            type="date"
            min={hoje}
            value={data}
            onChange={(e) => escolherDia(e.target.value)}
            onInput={(e) => escolherDia((e.target as HTMLInputElement).value)}
            aria-describedby={data !== "" && !dataValida ? "data-erro" : undefined}
            aria-invalid={data !== "" && !dataValida ? true : undefined}
          />
          {data !== "" && !dataValida && (
            <p id="data-erro" className="campo-erro">
              Escolha um dia de hoje em diante.
            </p>
          )}
          {disponibilidade.erro && !disponibilidade.erro.acordando && (
            <AvisoDeErro erro={disponibilidade.erro} id="data-erro-api" />
          )}
        </div>
      </section>

      {servicoId && dataValida && !disponibilidade.erro && (
        <section aria-labelledby="passo-horario">
          <h2 id="passo-horario">{unico || !barbeiros.dados ? "3" : "4"} · Horário</h2>
          <EstadoDaConsulta consulta={disponibilidade}>
            {(d) => {
              const dia = `${d.data}T12:00:00Z`;
              const grupos = d.barbeiros
                .map((b) => ({ ...b, horarios: b.horarios.filter((h) => h !== ocupado) }))
                .filter((b) => b.horarios.length > 0);
              // E18: dia aberto sem nenhum horário (cheio, ou uma folga de parte do dia tirou todos) fica "cheio", nunca
              // "Fechado"; só o dia com o expediente fechado diz "Fechado", e sem o motivo
              if (grupos.length === 0)
                return (
                  <div>
                    <p role="status">{diaFechado(d) ? TEXTOS.diaFechado : TEXTOS.diaCheio}</p>
                    <Botao
                      onClick={() => void procurarProximoDiaComHorario(dataValida ? data : hoje)}
                      disabled={procurando}
                    >
                      {procurando ? "Procurando…" : "Ver o próximo dia com horário"}
                    </Botao>
                    {buscadoAte && (
                      <>
                        <Aviso tipo="aviso">{TEXTOS.buscaSemHorario}</Aviso>
                        <Botao onClick={() => void procurarProximoDiaComHorario(buscadoAte)} disabled={procurando}>
                          Procurar mais {DIAS_DA_BUSCA} dias
                        </Botao>{" "}
                        <Botao onClick={() => document.getElementById("data")?.focus()}>Escolher em Outro dia</Botao>
                      </>
                    )}
                  </div>
                );
              const livres = grupos.reduce((n, g) => n + g.horarios.length, 0);
              return (
                <div>
                  <p className="etiqueta">
                    {livres} {livres === 1 ? "horário livre" : "horários livres"}
                  </p>
                  {grupos.map((b) => (
                    <div
                      key={b.barbeiroId}
                      role="group"
                      aria-label={`Horários de ${b.nomeExibicao}, ${formatarDiaDaSemana(dia, d.fuso)} ${formatarDataCurta(dia, d.fuso)}`}
                    >
                      <h3>{b.nomeExibicao}</h3>
                      {b.expediente?.aberto && b.expediente.inicio && b.expediente.fim && (
                        <p className="nota">
                          Atende das {horaCurta(b.expediente.inicio, fusoDoDia)} às{" "}
                          {horaCurta(b.expediente.fim, fusoDoDia)}
                        </p>
                      )}
                      {PERIODOS.map((periodo) => {
                        const almoco = b.expediente?.almoco;
                        const noAlmoco = (iso: string) =>
                          !!almoco &&
                          new Date(iso).getTime() >= new Date(almoco.inicio).getTime() &&
                          new Date(iso).getTime() < new Date(almoco.fim).getTime();
                        const todos = comIndisponiveis(b.horarios).filter((h) => !noAlmoco(h.inicio));
                        const chips = todos.filter((h) => periodoDe(formatarHora(h.inicio, fusoDoDia)) === periodo);
                        if (!chips.some((h) => h.livre)) return null;
                        // AF-11: a faixa "Fechado · 12h às 13h" entra antes do primeiro período que começa depois do almoço
                        const primeiroDepois = PERIODOS.find((p) =>
                          todos.some(
                            (h) =>
                              h.livre &&
                              periodoDe(formatarHora(h.inicio, fusoDoDia)) === p &&
                              !!almoco &&
                              new Date(h.inicio).getTime() >= new Date(almoco.fim).getTime(),
                          ),
                        );
                        const faixa = almoco && primeiroDepois === periodo && (
                          <p className="faixa-fechado">
                            Fechado · {horaCurta(almoco.inicio, fusoDoDia)} às {horaCurta(almoco.fim, fusoDoDia)}
                          </p>
                        );
                        return (
                          <Fragment key={periodo}>
                            {faixa}
                            <div role="group" aria-label={periodo} className="periodo-horarios">
                              <h4>{periodo}</h4>
                              <div className="horarios horarios-chips">
                                {chips.map((h) =>
                                  h.livre ? (
                                    <button
                                      key={h.inicio}
                                      type="button"
                                      className="botao botao-secundario horario chip-horario"
                                      aria-pressed={sel?.inicio === h.inicio && sel.barbeiroId === b.barbeiroId}
                                      onClick={() =>
                                        setEscolhido({ chave, barbeiroId: b.barbeiroId, inicio: h.inicio })
                                      }
                                    >
                                      {formatarHora(h.inicio, fusoDoDia)}
                                    </button>
                                  ) : (
                                    <button
                                      key={h.inicio}
                                      type="button"
                                      className="botao botao-secundario horario chip-horario chip-indisponivel"
                                      aria-disabled="true"
                                      onClick={(e) => e.preventDefault()}
                                    >
                                      {formatarHora(h.inicio, fusoDoDia)}
                                      <span className="so-leitor"> indisponível</span>
                                    </button>
                                  ),
                                )}
                              </div>
                            </div>
                          </Fragment>
                        );
                      })}
                    </div>
                  ))}
                </div>
              );
            }}
          </EstadoDaConsulta>
        </section>
      )}

      <p className="nota">{TEXTOS.notaAgendar}</p>
      <p className="nota">
        Pagamento antecipado por Pix ou cartão. Você terá um prazo curto para pagar, que aparece na próxima etapa; sem
        pagamento nesse prazo, o horário volta para a agenda.
      </p>
      <ContatoWhatsapp barbeiro={barbeiroEscolhido ?? barbeiros.dados?.find((b) => Boolean(b.whatsapp))} />

      <div className="resumo-fixo-espaco" aria-hidden="true" />
      <aside aria-labelledby="t-resumo" className="resumo-fixo">
        <h2 id="t-resumo" className="so-leitor">
          Resumo
        </h2>
        <dl>
          <dt>Serviço</dt>
          <dd>{servico?.nome ?? "Escolha o serviço"}</dd>
          <dt>Quando</dt>
          <dd>
            {sel
              ? `${formatarDiaDaSemana(sel.inicio, fusoDoDia)}, ${formatarDataCurta(sel.inicio, fusoDoDia)} às ${formatarHora(sel.inicio, fusoDoDia)}`
              : "Escolha o horário"}
          </dd>
          <dt>Total</dt>
          <dd>{servico ? formatarPreco(servico) : "-"}</dd>
        </dl>
        <Botao variante="principal" disabled={!sel} onClick={continuar}>
          Continuar
        </Botao>
      </aside>
    </>
  );
}
