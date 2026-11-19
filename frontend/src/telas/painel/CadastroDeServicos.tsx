"use client";
// Serviços do painel (A49): lista com os inativos marcados, criar e editar. Preço digitado vira centavos inteiros sem float.
// Nenhum serviço é apagado: sai de cena com ativo = false.
import { useEffect, useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { formatarCentavos } from "@/api/formato";
import { centavosDoTexto, textoDeCentavos } from "@/api/formato-painel";
import { useConsulta, useEnvio } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import type { ServicoAdmin } from "./tipos";

type Campos = "nome" | "duracaoMin" | "preco" | "descricao";

export function CadastroDeServicos() {
  const lista = useConsulta(() => chamar(api().GET("/admin/servicos")), []);
  const [editando, setEditando] = useState<ServicoAdmin | "novo" | null>(null);
  const [mensagem, setMensagem] = useState("");
  const [nome, setNome] = useState("");
  const [duracao, setDuracao] = useState("");
  const [preco, setPreco] = useState("");
  const [descricao, setDescricao] = useState("");
  const [ativo, setAtivo] = useState(true);
  const [erros, setErros] = useState<Partial<Record<Campos, string>>>({});
  const envio = useEnvio();

  function abrir(servico: ServicoAdmin | "novo") {
    envio.limparErro();
    setErros({});
    setMensagem("");
    setEditando(servico);
    if (servico === "novo") {
      setNome("");
      setDuracao("");
      setPreco("");
      setDescricao("");
      setAtivo(true);
    } else {
      setNome(servico.nome);
      setDuracao(String(servico.duracaoMin));
      setPreco(textoDeCentavos(servico.precoCentavos));
      setDescricao(servico.descricao ?? "");
      setAtivo(servico.ativo);
    }
  }

  // depois do 409, a lista recarregada traz a version atual: o formulário aberto passa a usá-la (o que foi digitado fica)
  useEffect(() => {
    if (envio.erro?.code !== "CONFLITO_DE_VERSAO" || editando === null || editando === "novo") return;
    const atual = lista.dados?.find((x) => x.id === editando.id);
    if (atual && atual.version !== editando.version) setEditando(atual);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lista.dados]);

  async function salvar(e: FormEvent) {
    e.preventDefault();
    const novos: Partial<Record<Campos, string>> = {};
    const centavos = centavosDoTexto(preco);
    const minutos = /^\d+$/.test(duracao.trim()) ? Number(duracao) : NaN;
    if (nome.trim().length < 2 || nome.trim().length > 80) novos.nome = "O nome deve ter de 2 a 80 caracteres.";
    if (!(minutos >= 5 && minutos <= 480)) novos.duracaoMin = "A duração deve ser um número de 5 a 480 minutos.";
    if (centavos === null) novos.preco = "Informe um preço maior que zero, como 55,90.";
    setErros(novos);
    if (Object.keys(novos).length > 0 || centavos === null || editando === null) return;
    const corpo = {
      nome: nome.trim(),
      duracaoMin: minutos,
      precoCentavos: centavos,
      ...(descricao.trim() ? { descricao: descricao.trim() } : {}),
    };
    const salvo = await envio.executar(async () => {
      try {
        return editando === "novo"
          ? await chamar(api().POST("/admin/servicos", { body: corpo }))
          : await chamar(
              api().PUT("/admin/servicos/{id}", {
                params: { path: { id: editando.id } },
                body: { ...corpo, ativo, version: editando.version },
              }),
            );
      } catch (falha) {
        // alguém salvou antes: busca a versão atual e fecha o formulário (o próximo "Editar" já leva a version certa)
        if (falha instanceof ErroApi && falha.code === "CONFLITO_DE_VERSAO") lista.recarregar();
        throw falha;
      }
    });
    if (salvo) {
      setMensagem("Serviço salvo.");
      setEditando(null);
      lista.recarregar();
    }
  }

  const erroApi = envio.erro;
  const doCampo = (campo: Campos): string | undefined =>
    erros[campo] ??
    (erroApi?.code === "NOME_JA_EXISTE" && campo === "nome" ? mensagemDoErro(erroApi) : undefined) ??
    (erroApi?.code === "VALIDACAO_INVALIDA" ? erroApi.doCampo(campo === "preco" ? "precoCentavos" : campo) : undefined);
  const erroDeLinha =
    erroApi && erroApi.code !== "NOME_JA_EXISTE" && erroApi.code !== "VALIDACAO_INVALIDA" ? erroApi : undefined;

  return (
    <section aria-labelledby="t-servicos">
      <div className="secao-cabecalho">
        <h2 id="t-servicos">Serviços</h2>
        <Botao onClick={() => abrir("novo")}>Novo serviço</Botao>
      </div>
      {mensagem && <Aviso tipo="sucesso">{mensagem}</Aviso>}
      {erroDeLinha?.code === "CONFLITO_DE_VERSAO" && (
        <Aviso tipo="aviso">
          Alguém alterou este serviço antes de você. Atualizamos a versão: confira os campos e salve de novo.
        </Aviso>
      )}
      <EstadoDaConsulta consulta={lista}>
        {(servicos) => (
          <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Tabela de serviços">
            <table>
              <caption>Serviços, inclusive os inativos</caption>
              <thead>
                <tr>
                  <th scope="col">Nome</th>
                  <th scope="col">Duração</th>
                  <th scope="col">Preço</th>
                  <th scope="col">Situação</th>
                  <th scope="col">Ações</th>
                </tr>
              </thead>
              <tbody>
                {servicos.map((s) => (
                  <tr key={s.id} data-ativo={s.ativo}>
                    <th scope="row">{s.nome}</th>
                    <td>{s.duracaoMin} min</td>
                    <td className="preco">{s.precoFormatado ?? formatarCentavos(s.precoCentavos)}</td>
                    <td>
                      <span className="etiqueta" data-status={s.ativo ? "confirmado" : "cancelado"}>
                        {s.ativo ? "Ativo" : "Inativo"}
                      </span>
                    </td>
                    <td>
                      <Botao aria-label={`Editar ${s.nome}`} onClick={() => abrir(s)}>
                        Editar
                      </Botao>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </EstadoDaConsulta>

      {editando !== null && (
        <form
          onSubmit={salvar}
          noValidate
          aria-label={editando === "novo" ? "Novo serviço" : `Editar ${editando.nome}`}
        >
          <h3>{editando === "novo" ? "Novo serviço" : `Editar ${editando.nome}`}</h3>
          {erroDeLinha && erroDeLinha.code !== "CONFLITO_DE_VERSAO" && <AvisoDeErro erro={erroDeLinha} focar={false} />}
          <Campo
            id="s-nome"
            rotulo="Nome"
            value={nome}
            maxLength={80}
            onChange={(e) => setNome(e.target.value)}
            erro={doCampo("nome")}
          />
          <Campo
            id="s-duracao"
            rotulo="Duração (minutos)"
            inputMode="numeric"
            value={duracao}
            onChange={(e) => setDuracao(e.target.value)}
            erro={doCampo("duracaoMin")}
            ajuda="De 5 a 480 minutos."
          />
          <Campo
            id="s-preco"
            rotulo="Preço (R$)"
            inputMode="decimal"
            value={preco}
            onChange={(e) => setPreco(e.target.value)}
            erro={doCampo("preco")}
            ajuda="Em reais, como 55,90. Vale só para as reservas novas."
          />
          <Campo
            id="s-descricao"
            rotulo="Descrição (opcional)"
            maxLength={300}
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            erro={doCampo("descricao")}
          />
          {editando !== "novo" && (
            <div className="caixa">
              <input id="s-ativo" type="checkbox" checked={ativo} onChange={(e) => setAtivo(e.target.checked)} />
              <label htmlFor="s-ativo">Ativo (aparece para o cliente agendar)</label>
            </div>
          )}
          <div className="acoes">
            <Botao type="submit" variante="principal" disabled={envio.enviando}>
              Salvar serviço
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
