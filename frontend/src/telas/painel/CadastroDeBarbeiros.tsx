"use client";
// Barbeiros, jornada e folgas do painel (A50). WhatsApp comercial com máscara (6D); a conta vinculada é opcional.
import { useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { formatarDataEHora } from "@/api/formato";
import { instanteNoFuso, mascararWhatsapp, soNumeros } from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { Dialogo } from "@/componentes/Dialogo";
import { EditorDeJornada } from "@/componentes/EditorDeJornada";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { almocosDoDia, horaCurta, type BarbeiroAdmin, type Folga } from "./tipos";

function resumoDoAlmoco(b: BarbeiroAdmin): string {
  const almocos = almocosDoDia(b.jornada ?? [], 2); // terça: o dia de referência do resumo
  const dias = new Set((b.jornada ?? []).map((j) => j.diaSemana)).size;
  if (dias === 0) return "Sem expediente cadastrado";
  return `${dias} dia(s) por semana${almocos[0] ? ` · almoço ${horaCurta(almocos[0].inicio)} às ${horaCurta(almocos[0].fim)}` : ""}`;
}

function Folgas({ barbeiro }: { barbeiro: BarbeiroAdmin }) {
  const lista = useConsulta(
    () => chamar(api().GET("/admin/barbeiros/{id}/folgas", { params: { path: { id: barbeiro.id } } })),
    [barbeiro.id],
  );
  const [dia, setDia] = useState("");
  const [horaIni, setHoraIni] = useState("");
  const [diaFim, setDiaFim] = useState("");
  const [horaFim, setHoraFim] = useState("");
  const [motivo, setMotivo] = useState("");
  const [removendo, setRemovendo] = useState<Folga | null>(null);
  const [mensagem, setMensagem] = useState("");
  const [incompleto, setIncompleto] = useState("");
  const envio = useEnvio();

  async function criar(e: FormEvent) {
    e.preventDefault();
    if (!dia || !horaIni || !diaFim || !horaFim) {
      setIncompleto("Informe o início e o fim da folga.");
      return;
    }
    setIncompleto("");
    const inicio = instanteNoFuso(dia, horaIni);
    const fim = instanteNoFuso(diaFim, horaFim);
    if (new Date(fim) <= new Date(inicio)) {
      setIncompleto("O fim da folga deve ser depois do início.");
      return;
    }
    const criada = await envio.executar(() =>
      chamar(
        api().POST("/admin/barbeiros/{id}/folgas", {
          params: { path: { id: barbeiro.id } },
          body: { inicio, fim, ...(motivo.trim() ? { motivo: motivo.trim() } : {}) },
        }),
      ),
    );
    if (criada) {
      setMensagem("Folga lançada.");
      setMotivo("");
      lista.recarregar();
    }
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
      setMensagem("Folga removida.");
      setRemovendo(null);
      lista.recarregar();
    }
  }

  return (
    <section aria-labelledby={`t-folgas-${barbeiro.id}`}>
      <h4 id={`t-folgas-${barbeiro.id}`}>Folgas de {barbeiro.nomeExibicao}</h4>
      {mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}
      <EstadoDaConsulta consulta={lista}>
        {(folgas) =>
          folgas.length === 0 ? (
            <p>Nenhuma folga de hoje em diante.</p>
          ) : (
            <ul className="lista">
              {folgas.map((f) => (
                <li key={f.id} className="item">
                  <p>
                    {formatarDataEHora(f.inicio)} até {formatarDataEHora(f.fim)}
                    {f.motivo ? ` · ${f.motivo}` : ""}
                  </p>
                  <Botao
                    aria-label={`Remover a folga de ${formatarDataEHora(f.inicio)}`}
                    onClick={() => {
                      envio.limparErro();
                      setRemovendo(f);
                    }}
                  >
                    Remover folga
                  </Botao>
                </li>
              ))}
            </ul>
          )
        }
      </EstadoDaConsulta>
      <form onSubmit={criar} noValidate aria-label="Lançar folga">
        <fieldset>
          <legend>Lançar folga</legend>
          <Campo id="f-dia" rotulo="Dia do início" type="date" value={dia} onChange={(e) => setDia(e.target.value)} />
          <Campo
            id="f-hora"
            rotulo="Hora do início"
            type="time"
            value={horaIni}
            onChange={(e) => setHoraIni(e.target.value)}
          />
          <Campo
            id="f-dia-fim"
            rotulo="Dia do fim"
            type="date"
            value={diaFim}
            onChange={(e) => setDiaFim(e.target.value)}
          />
          <Campo
            id="f-hora-fim"
            rotulo="Hora do fim"
            type="time"
            value={horaFim}
            onChange={(e) => setHoraFim(e.target.value)}
            erro={incompleto || undefined}
          />
          <Campo
            id="f-motivo"
            rotulo="Motivo (opcional)"
            maxLength={200}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
          />
          {envio.erro && !removendo && (
            <Aviso tipo="erro">
              {envio.erro.code === "FOLGA_COM_RESERVAS"
                ? "Há agendamentos neste período. Cancele-os antes ou escolha outro período."
                : mensagemDoErro(envio.erro)}
            </Aviso>
          )}
          <Botao type="submit" variante="principal" disabled={envio.enviando}>
            Lançar folga
          </Botao>
        </fieldset>
      </form>
      {removendo && (
        <Dialogo titulo="Remover folga" aoFechar={() => setRemovendo(null)}>
          <p>O horário volta a aceitar reservas. Remover a folga de {formatarDataEHora(removendo.inicio)}?</p>
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
    </section>
  );
}

export function CadastroDeBarbeiros() {
  const lista = useConsulta(() => chamar(api().GET("/admin/barbeiros")), []);
  const [editando, setEditando] = useState<BarbeiroAdmin | "novo" | null>(null);
  const [jornadaDe, setJornadaDe] = useState<string | null>(null);
  const [mensagem, setMensagem] = useState("");
  const [nome, setNome] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [usuarioId, setUsuarioId] = useState("");
  const [ativo, setAtivo] = useState(true);
  const [erros, setErros] = useState<Partial<Record<"nome" | "whatsapp" | "usuarioId", string>>>({});
  const envio = useEnvio();
  const envioDaJornada = useEnvio();

  function abrir(b: BarbeiroAdmin | "novo") {
    envio.limparErro();
    setErros({});
    setMensagem("");
    setJornadaDe(null);
    setEditando(b);
    setNome(b === "novo" ? "" : b.nomeExibicao);
    setWhatsapp(b === "novo" || !b.whatsapp ? "" : mascararWhatsapp(b.whatsapp));
    setUsuarioId(b === "novo" ? "" : (b.usuarioId ?? ""));
    setAtivo(b === "novo" ? true : b.ativo);
  }

  async function salvar(e: FormEvent) {
    e.preventDefault();
    const novos: typeof erros = {};
    const numeros = soNumeros(whatsapp);
    if (nome.trim().length < 2 || nome.trim().length > 80) novos.nome = "O nome deve ter de 2 a 80 caracteres.";
    if (numeros && (numeros.length < 10 || numeros.length > 13))
      novos.whatsapp = "Digite o DDD e o número (10 ou 11 números).";
    setErros(novos);
    if (Object.keys(novos).length > 0 || editando === null) return;
    const corpo = {
      nomeExibicao: nome.trim(),
      ...(usuarioId.trim() ? { usuarioId: usuarioId.trim() } : {}),
      ...(numeros ? { whatsapp: numeros } : {}),
    };
    const salvo = await envio.executar(async () => {
      try {
        return editando === "novo"
          ? await chamar(api().POST("/admin/barbeiros", { body: corpo }))
          : await chamar(
              api().PUT("/admin/barbeiros/{id}", {
                params: { path: { id: editando.id } },
                body: { ...corpo, ativo, version: editando.version },
              }),
            );
      } catch (falha) {
        if (falha instanceof ErroApi && falha.code === "CONFLITO_DE_VERSAO") lista.recarregar();
        throw falha;
      }
    });
    if (salvo) {
      setMensagem("Barbeiro salvo.");
      setEditando(null);
      lista.recarregar();
    }
  }

  async function salvarJornada(
    barbeiro: BarbeiroAdmin,
    jornada: { diaSemana: number; horaInicio: string; horaFim: string }[],
  ) {
    const feito = await envioDaJornada.executar(() =>
      chamar(api().PUT("/admin/barbeiros/{id}/jornada", { params: { path: { id: barbeiro.id } }, body: jornada })),
    );
    if (feito) {
      setMensagem("Jornada salva.");
      lista.recarregar();
    }
  }

  const erroApi = envio.erro;
  const campo = (c: "nome" | "whatsapp" | "usuarioId"): string | undefined =>
    erros[c] ??
    (c === "usuarioId" && erroApi?.code === "USUARIO_JA_VINCULADO" ? mensagemDoErro(erroApi) : undefined) ??
    (erroApi?.code === "VALIDACAO_INVALIDA" ? erroApi.doCampo(c === "nome" ? "nomeExibicao" : c) : undefined);
  const erroDeLinha =
    erroApi && erroApi.code !== "USUARIO_JA_VINCULADO" && erroApi.code !== "VALIDACAO_INVALIDA" ? erroApi : undefined;
  const erroDaJornada = envioDaJornada.erro;

  return (
    <section aria-labelledby="t-barbeiros">
      <div className="secao-cabecalho">
        <h2 id="t-barbeiros">Barbeiros</h2>
        <Botao onClick={() => abrir("novo")}>Novo barbeiro</Botao>
      </div>
      {mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}
      {erroDeLinha?.code === "CONFLITO_DE_VERSAO" && (
        <Aviso tipo="aviso">
          Alguém alterou este barbeiro antes de você. Atualizamos a lista: abra de novo e repita a mudança.
        </Aviso>
      )}
      <EstadoDaConsulta consulta={lista}>
        {(barbeiros) => (
          <>
            <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Tabela de barbeiros">
              <table>
                <caption>Barbeiros</caption>
                <thead>
                  <tr>
                    <th scope="col">Nome</th>
                    <th scope="col">WhatsApp comercial</th>
                    <th scope="col">Conta de login</th>
                    <th scope="col">Situação</th>
                    <th scope="col">Jornada</th>
                    <th scope="col">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {barbeiros.map((b) => (
                    <tr key={b.id}>
                      <th scope="row">{b.nomeExibicao}</th>
                      <td>{b.whatsapp ? mascararWhatsapp(b.whatsapp) : "Sem número"}</td>
                      <td>{b.usuarioId ? "Vinculada" : "Sem conta"}</td>
                      <td>
                        <span className="etiqueta" data-status={b.ativo ? "confirmado" : "cancelado"}>
                          {b.ativo ? "Ativo" : "Inativo"}
                        </span>
                      </td>
                      <td>{resumoDoAlmoco(b)}</td>
                      <td>
                        <div className="acoes">
                          <Botao aria-label={`Editar ${b.nomeExibicao}`} onClick={() => abrir(b)}>
                            Editar
                          </Botao>
                          <Botao
                            aria-label={`Jornada e folgas de ${b.nomeExibicao}`}
                            onClick={() => {
                              setEditando(null);
                              setMensagem("");
                              envioDaJornada.limparErro();
                              setJornadaDe(jornadaDe === b.id ? null : b.id);
                            }}
                          >
                            Jornada e folgas
                          </Botao>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="campo-ajuda">
              O WhatsApp comercial aparece na home e na escolha do horário. Sem número, o botão de WhatsApp some.
            </p>
            {barbeiros
              .filter((b) => b.id === jornadaDe)
              .map((b) => (
                <div key={b.id}>
                  <h3>Jornada de {b.nomeExibicao}</h3>
                  <EditorDeJornada
                    key={`${b.id}-${b.version}-${(b.jornada ?? []).length}`}
                    inicial={b.jornada ?? []}
                    desabilitado={envioDaJornada.enviando}
                    sobreposta={erroDaJornada?.code === "JORNADA_SOBREPOSTA"}
                    erroGeral={
                      erroDaJornada
                        ? erroDaJornada.code === "JORNADA_SOBREPOSTA"
                          ? "Há horários que se sobrepõem no mesmo dia. Ajuste e salve de novo."
                          : mensagemDoErro(erroDaJornada)
                        : undefined
                    }
                    aoSalvar={(j) => salvarJornada(b, j)}
                  />
                  <Folgas barbeiro={b} />
                </div>
              ))}
          </>
        )}
      </EstadoDaConsulta>

      {editando !== null && (
        <form
          onSubmit={salvar}
          noValidate
          aria-label={editando === "novo" ? "Novo barbeiro" : `Editar ${editando.nomeExibicao}`}
        >
          <h3>{editando === "novo" ? "Novo barbeiro" : `Editar ${editando.nomeExibicao}`}</h3>
          {erroDeLinha && erroDeLinha.code !== "CONFLITO_DE_VERSAO" && <AvisoDeErro erro={erroDeLinha} focar={false} />}
          <Campo
            id="b-nome"
            rotulo="Nome de exibição"
            value={nome}
            maxLength={80}
            onChange={(e) => setNome(e.target.value)}
            erro={campo("nome")}
          />
          <Campo
            id="b-whatsapp"
            rotulo="WhatsApp comercial"
            inputMode="tel"
            autoComplete="off"
            value={whatsapp}
            onChange={(e) => setWhatsapp(mascararWhatsapp(e.target.value))}
            erro={campo("whatsapp")}
            ajuda="Opcional. É público: aparece para qualquer visitante. Use o número da barbearia, não um pessoal."
          />
          <Campo
            id="b-usuario"
            rotulo="Conta de login (opcional)"
            autoComplete="off"
            value={usuarioId}
            onChange={(e) => setUsuarioId(e.target.value)}
            erro={campo("usuarioId")}
            ajuda="O identificador da conta com papel BARBEIRO. Com a conta ligada, ele vê só a própria agenda."
          />
          {editando !== "novo" && (
            <div className="caixa">
              <input id="b-ativo" type="checkbox" checked={ativo} onChange={(e) => setAtivo(e.target.checked)} />
              <label htmlFor="b-ativo">Ativo (aparece para o cliente agendar)</label>
            </div>
          )}
          <div className="acoes">
            <Botao type="submit" variante="principal" disabled={envio.enviando}>
              Salvar barbeiro
            </Botao>
            <Botao disabled={envio.enviando} onClick={() => setEditando(null)}>
              Cancelar
            </Botao>
          </div>
        </form>
      )}
    </section>
  );
}
