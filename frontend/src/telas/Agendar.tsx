"use client";
// Passo 1 da reserva: serviço, barbeiro, dia e horário (A18 a A20). Horários no fuso da barbearia, agrupados por barbeiro.
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
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
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { ContatoWhatsapp } from "@/componentes/ContatoWhatsapp";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { buscarServicos } from "@/componentes/ListaDeServicos";
import { mensagemDoErro } from "@/api/mensagens";

const HORIZONTE = Number(process.env.NEXT_PUBLIC_HORIZONTE_DIAS ?? "30") || 30;

export function Agendar() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const [servicoId, setServicoId] = useState(busca.get("servicoId") ?? "");
  const [barbeiroId, setBarbeiroId] = useState(busca.get("barbeiroId") ?? "");
  const [data, setData] = useState(busca.get("data") ?? "");
  const ocupado = busca.get("ocupado") ?? undefined;

  const hoje = diaNoFuso(new Date());
  const limite = somarDias(hoje, HORIZONTE);
  const dataValida = data !== "" && data >= hoje && data <= limite;

  const servicos = useConsulta(buscarServicos, []);
  const barbeiros = useConsulta(() => chamar(api().GET("/barbeiros")), []);
  const disponibilidade = useConsulta(
    () =>
      chamar(
        api().GET("/disponibilidade", {
          params: { query: { servicoId, data, ...(barbeiroId ? { barbeiroId } : {}) } },
        }),
      ),
    [servicoId, data, barbeiroId],
    Boolean(servicoId) && dataValida,
  );

  function escolher(barbeiro: string, inicio: string) {
    const p = new URLSearchParams({ servicoId, barbeiroId: barbeiro, inicio });
    roteador.push(`/agendar/dados?${p.toString()}`);
  }

  const barbeiroEscolhido = barbeiros.dados?.find((b) => b.id === barbeiroId);

  return (
    <>
      <p className="sobretitulo">Agenda · Eduardo Candido</p>
      <h1>Agende seu horário</h1>
      {ocupado && <Aviso tipo="aviso">{mensagemDoErro({ code: "HORARIO_INDISPONIVEL", status: 409 })}</Aviso>}

      <section aria-labelledby="passo-servico">
        <h2 id="passo-servico">1 · Serviço</h2>
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
      </section>

      <section aria-labelledby="passo-dia">
        <h2 id="passo-dia">2 · Dia</h2>
        <form onSubmit={(e) => e.preventDefault()}>
          <div className="campo">
            <label htmlFor="barbeiro">Barbeiro</label>
            <select id="barbeiro" value={barbeiroId} onChange={(e) => setBarbeiroId(e.target.value)}>
              <option value="">Qualquer barbeiro</option>
              {barbeiros.dados?.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.nomeExibicao}
                </option>
              ))}
            </select>
          </div>
          <div className="campo">
            <label htmlFor="data">Dia</label>
            <input
              id="data"
              type="date"
              min={hoje}
              max={limite}
              value={data}
              onChange={(e) => setData(e.target.value)}
              aria-describedby={data !== "" && !dataValida ? "data-erro" : undefined}
              aria-invalid={data !== "" && !dataValida ? true : undefined}
            />
            {data !== "" && !dataValida && (
              <p id="data-erro" className="campo-erro">
                Escolha um dia de hoje até {formatarDataCurta(`${limite}T12:00:00Z`)}.
              </p>
            )}
            {disponibilidade.erro && !disponibilidade.erro.acordando && (
              <AvisoDeErro erro={disponibilidade.erro} id="data-erro-api" />
            )}
          </div>
        </form>
      </section>

      {servicoId && dataValida && !disponibilidade.erro && (
        <section aria-labelledby="passo-horario">
          <h2 id="passo-horario">3 · Horário</h2>
          <EstadoDaConsulta consulta={disponibilidade}>
            {(d) => {
              const dia = `${d.data}T12:00:00Z`;
              const grupos = d.barbeiros
                .map((b) => ({ ...b, horarios: b.horarios.filter((h) => h !== ocupado) }))
                .filter((b) => b.horarios.length > 0);
              if (grupos.length === 0) return <p>Sem horário livre neste dia. Tente outro dia.</p>;
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
                      <div className="horarios">
                        {b.horarios.map((h) => (
                          <Botao
                            key={h}
                            className="botao botao-secundario horario"
                            onClick={() => escolher(b.barbeiroId, h)}
                          >
                            {formatarHora(h, d.fuso ?? FUSO_PADRAO)}
                          </Botao>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              );
            }}
          </EstadoDaConsulta>
        </section>
      )}
      <aside aria-labelledby="t-resumo" className="resumo">
        <h2 id="t-resumo">Resumo</h2>
        <dl>
          <dt>Serviço</dt>
          <dd>{servicos.dados?.find((x) => x.id === servicoId)?.nome ?? "Escolha o serviço"}</dd>
          <dt>Dia</dt>
          <dd>{dataValida ? formatarDataCurta(`${data}T12:00:00Z`) : "Escolha o dia"}</dd>
          <dt>Total</dt>
          <dd>
            {(() => {
              const x = servicos.dados?.find((y) => y.id === servicoId);
              return x ? formatarPreco(x) : "-";
            })()}
          </dd>
        </dl>
        <p className="nota">
          Só aparecem os horários livres, com pelo menos 2 horas de antecedência e até 30 dias à frente. Pix ou cartão:
          na próxima etapa, um contador mostra o prazo para pagar e o horário fica reservado só para você. Sem pagamento
          no prazo, o horário volta para a agenda.
        </p>
        <ContatoWhatsapp barbeiro={barbeiroEscolhido ?? barbeiros.dados?.find((b) => Boolean(b.whatsapp))} />
      </aside>
    </>
  );
}
