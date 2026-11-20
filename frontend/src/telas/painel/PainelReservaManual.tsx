"use client";
// Reserva manual (A62, 6D): trava no painel um horário combinado por WhatsApp. Nasce confirmada e sem cobrança.
// Não existe campo de WhatsApp, e-mail nem CPF do cliente: a API não guarda (LGPD, R30).
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import type { ErroApi } from "@/api/erro";
import { diaNoFuso } from "@/api/formato";
import { instanteNoFuso } from "@/api/formato-painel";
import { useConsulta, useEnvio, type Consulta } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { AreaDeTexto, Selecao } from "@/componentes/CamposDoPainel";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { useSessao } from "@/componentes/Sessao";
import { buscarServicos } from "@/componentes/ListaDeServicos";

type Campos = "servicoId" | "barbeiroId" | "data" | "hora" | "nomeCliente" | "observacao";

/** Em qual campo cada erro da API aparece; o texto é do catálogo, com ajustes para a reserva manual. */
function erroDoCampo(erro: ErroApi): { campo: Campos; texto: string } | null {
  switch (erro.code) {
    case "HORARIO_INDISPONIVEL":
      return { campo: "hora", texto: "Este horário já está ocupado para esse barbeiro. Escolha outro." };
    case "FORA_DO_EXPEDIENTE":
      return { campo: "hora", texto: "O barbeiro não atende neste horário (fora da jornada, no almoço ou de folga)." };
    case "ANTECEDENCIA_INSUFICIENTE":
      return { campo: "hora", texto: "Este horário já passou. Escolha um horário a partir de agora." };
    case "FORA_DO_HORIZONTE":
      return { campo: "data", texto: "Este dia está além do que a agenda aceita. Escolha uma data mais próxima." };
    case "SERVICO_INATIVO":
      return { campo: "servicoId", texto: mensagemDoErro(erro) };
    default:
      return null;
  }
}

export function PainelReservaManual() {
  const roteador = useRouter();
  const sessao = useSessao();
  const admin = !!sessao.papeis?.includes("ADMIN");
  const [servicoId, setServicoId] = useState("");
  const [barbeiroId, setBarbeiroId] = useState("");
  const [data, setData] = useState("");
  const [hora, setHora] = useState("");
  const [nomeCliente, setNomeCliente] = useState("");
  const [observacao, setObservacao] = useState("");
  const [faltando, setFaltando] = useState<Partial<Record<Campos, string>>>({});
  const envio = useEnvio();

  const servicos = useConsulta(buscarServicos, []);
  // ADMIN escolhe entre os barbeiros; o BARBEIRO só enxerga o próprio (id e nome vêm de /admin/minha-jornada)
  const todos = useConsulta(() => chamar(api().GET("/barbeiros")), [], admin);
  const minha = useConsulta(() => chamar(api().GET("/admin/minha-jornada")), [], !admin);
  const barbeiros = admin
    ? (todos.dados ?? []).map((b) => ({ id: b.id, nome: b.nomeExibicao }))
    : minha.dados
      ? [{ id: minha.dados.barbeiroId, nome: minha.dados.nomeExibicao }]
      : [];
  const barbeiroEscolhido = barbeiroId || (!admin && barbeiros.length === 1 ? barbeiros[0].id : "");
  const hoje = diaNoFuso(new Date());

  async function enviar(e: FormEvent) {
    e.preventDefault();
    const erros: Partial<Record<Campos, string>> = {};
    if (!servicoId) erros.servicoId = "Escolha o serviço.";
    if (!barbeiroEscolhido) erros.barbeiroId = "Escolha o barbeiro.";
    if (!data) erros.data = "Informe o dia.";
    else if (data < hoje) erros.data = "Este dia já passou.";
    if (!hora) erros.hora = "Informe o horário.";
    setFaltando(erros);
    if (Object.keys(erros).length > 0) return;
    const criada = await envio.executar(() =>
      chamar(
        api().POST("/admin/agendamentos", {
          body: {
            servicoId,
            barbeiroId: barbeiroEscolhido,
            inicio: instanteNoFuso(data, hora),
            ...(nomeCliente.trim() ? { nomeCliente: nomeCliente.trim() } : {}),
            ...(observacao.trim() ? { observacao: observacao.trim() } : {}),
          },
        }),
      ),
    );
    // a agenda do dia da reserva abre e se recarrega sozinha
    if (criada) roteador.push(`/painel?data=${data}`);
  }

  const erroApi = envio.erro;
  const doCampo = erroApi ? erroDoCampo(erroApi) : null;
  const texto = (campo: Campos) =>
    faltando[campo] ??
    (doCampo?.campo === campo ? doCampo.texto : undefined) ??
    (erroApi?.code === "VALIDACAO_INVALIDA"
      ? erroApi.doCampo(campo === "nomeCliente" || campo === "observacao" ? campo : "")
      : undefined);

  return (
    <>
      <h1>Reserva manual</h1>
      <p>Para quem combinou pelo WhatsApp. A reserva já nasce confirmada e sem cobrança.</p>
      <EstadoDaConsulta consulta={(admin ? todos : minha) as Consulta<unknown>}>
        {() => (
          <form onSubmit={enviar} noValidate aria-label="Reserva manual">
            {erroApi && !doCampo && erroApi.code !== "VALIDACAO_INVALIDA" && (
              <AvisoDeErro
                erro={erroApi}
                final="O servidor não respondeu. Confira a agenda antes de tentar de novo: a reserva pode ter sido criada."
              />
            )}
            <Selecao
              id="m-servico"
              rotulo="Serviço"
              value={servicoId}
              onChange={(e) => setServicoId(e.target.value)}
              erro={texto("servicoId")}
            >
              <option value="">Escolha o serviço</option>
              {servicos.dados?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.nome} · {s.duracaoMin} min
                </option>
              ))}
            </Selecao>
            <Selecao
              id="m-barbeiro"
              rotulo="Barbeiro"
              value={barbeiroEscolhido}
              onChange={(e) => setBarbeiroId(e.target.value)}
              erro={texto("barbeiroId")}
              ajuda={admin ? undefined : "Você reserva só na sua própria agenda."}
            >
              {admin && <option value="">Escolha o barbeiro</option>}
              {barbeiros.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.nome}
                </option>
              ))}
            </Selecao>
            <Campo
              id="m-dia"
              rotulo="Dia"
              type="date"
              min={hoje}
              value={data}
              onChange={(e) => setData(e.target.value)}
              erro={texto("data")}
            />
            <Campo
              id="m-hora"
              rotulo="Horário"
              type="time"
              value={hora}
              onChange={(e) => setHora(e.target.value)}
              erro={texto("hora")}
              ajuda="Hora de início, no horário da barbearia. A antecedência mínima não vale aqui, mas o horário não pode ter passado."
            />
            <Campo
              id="m-nome"
              rotulo="Nome do cliente (opcional)"
              maxLength={80}
              autoComplete="off"
              ajuda="Não digite telefone nem documento."
              value={nomeCliente}
              onChange={(e) => setNomeCliente(e.target.value)}
              erro={texto("nomeCliente")}
            />
            <AreaDeTexto
              id="m-obs"
              rotulo="Observação (opcional)"
              maxLength={200}
              contador
              value={observacao}
              onChange={(e) => setObservacao(e.target.value)}
              erro={texto("observacao")}
              ajuda="Não digite telefone nem documento. A observação some da agenda 90 dias depois do atendimento."
            />
            <Aviso tipo="info">Sem cobrança pelo app: combine o pagamento direto com o cliente.</Aviso>
            <div className="acoes">
              <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
                {envio.esperando ? `Aguarde ${envio.restante} s` : "Criar reserva"}
              </Botao>
              <Link href="/painel">Voltar à agenda</Link>
            </div>
          </form>
        )}
      </EstadoDaConsulta>
    </>
  );
}
