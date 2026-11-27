"use client";
// Passo 2 da reserva: dados do visitante (ou "Reservar" direto para o cliente logado). A24 e A25 vivem aqui.
import Link from "next/link";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { formatarDataEHora, formatarPreco } from "@/api/formato";
import { useConsulta, useEnvio } from "@/api/hooks";
import { mensagemDoErro } from "@/api/mensagens";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao, LinkBotao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { ConsentimentoDeRegiao } from "@/componentes/ConsentimentoDeRegiao";
import { IndicadorDePassos } from "@/componentes/IndicadorDePassos";
import { buscarServicos } from "@/componentes/ListaDeServicos";
import { useSessao } from "@/componentes/Sessao";
import { TEXTOS } from "@/conteudo/textos";

const CAMPOS = ["nome", "email", "whatsapp", "cep"] as const;

export function ReservaDados() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const caminho = usePathname();
  const sessao = useSessao();
  const servicoId = busca.get("servicoId") ?? "";
  const barbeiroId = busca.get("barbeiroId") ?? "";
  const inicio = busca.get("inicio") ?? "";

  const [semConta, setSemConta] = useState(false);
  const [valores, setValores] = useState({ nome: "", email: "", whatsapp: "", cep: "" });
  const [regiao, setRegiao] = useState(false);
  const politica = useConsulta(() => chamar(api().GET("/politica-privacidade")), [], semConta);
  const envio = useEnvio();

  const servicos = useConsulta(buscarServicos, [], Boolean(servicoId));
  const barbeiros = useConsulta(() => chamar(api().GET("/barbeiros")), [], Boolean(barbeiroId));

  const camposComErro: Record<string, string> = {};
  if (envio.erro?.code === "VALIDACAO_INVALIDA") for (const e of envio.erro.erros) camposComErro[e.field] = e.message;
  if (envio.erro?.code === "CEP_INVALIDO") camposComErro.cep = mensagemDoErro(envio.erro);

  useEffect(() => {
    if (Object.keys(camposComErro).length === 0) return;
    const primeiro = CAMPOS.find((c) => camposComErro[c]);
    if (primeiro) document.getElementById(primeiro)?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [envio.erro]);

  if (sessao.carregando) return <p role="status">{TEXTOS.carregando}</p>;
  if (!servicoId || !barbeiroId || !inicio) {
    return (
      <>
        <IndicadorDePassos passoAtual={1} />
        <h1>Seus dados</h1>
        <p>Escolha o serviço e o horário primeiro.</p>
        <LinkBotao href="/agendar">Escolher horário</LinkBotao>
      </>
    );
  }

  const servico = servicos.dados?.find((s) => s.id === servicoId);
  const barbeiro = barbeiros.dados?.find((b) => b.id === barbeiroId);
  const voltarParaEscolha = (extra: Record<string, string> = {}) =>
    `/agendar?${new URLSearchParams({ servicoId, barbeiroId, data: inicio.slice(0, 10), ...extra }).toString()}`;

  async function reservar() {
    const corpoCliente = { servicoId, barbeiroId, inicio };
    const chamarApi = () =>
      sessao.autenticado
        ? chamar(api().POST("/agendamentos", { body: corpoCliente }))
        : chamar(
            api().POST("/agendamentos/guest", {
              body: {
                ...corpoCliente,
                nome: valores.nome,
                email: valores.email,
                whatsapp: valores.whatsapp,
                // O CEP só vai com a caixinha de região marcada, junto com a versão do texto que a pessoa viu (A47)
                ...(regiao && valores.cep.trim() && politica.dados
                  ? { cep: valores.cep, autorizaRegiao: true, versaoTextoRegiao: politica.dados.versao }
                  : {}),
              },
            }),
          );
    let reserva;
    try {
      reserva = await chamarApi();
    } catch (e) {
      // 409 CONFLITO_CONCORRENTE: repete a MESMA requisição uma única vez (E13).
      if (e instanceof ErroApi && e.code === "CONFLITO_CONCORRENTE") reserva = await chamarApi();
      else throw e;
    }
    roteador.push(`/agendar/pagamento/${reserva.id}`);
  }

  async function aoEnviar(e: React.FormEvent) {
    e.preventDefault();
    try {
      await envio.executar(async () => {
        try {
          await reservar();
        } catch (erro) {
          if (erro instanceof ErroApi && erro.code === "HORARIO_INDISPONIVEL") {
            roteador.replace(voltarParaEscolha({ ocupado: inicio }));
            return;
          }
          throw erro;
        }
      });
    } catch {
      // useEnvio já guarda o erro
    }
  }

  const erro = envio.erro;
  const bloqueado = envio.enviando || envio.esperando;
  const botao = (
    <Botao type="submit" variante="principal" disabled={bloqueado}>
      {envio.enviando ? "Reservando…" : "Reservar"}
    </Botao>
  );

  return (
    <>
      <IndicadorDePassos passoAtual={1} />
      <h1>Seus dados</h1>

      <div className="resumo-fixo-espaco" aria-hidden="true" />
      <aside aria-labelledby="t-resumo-dados" className="resumo-fixo">
        <h2 id="t-resumo-dados" className="so-leitor">
          Resumo
        </h2>
        <dl>
          <dt>Serviço</dt>
          <dd>{servico?.nome ?? "Serviço escolhido"}</dd>
          <dt>Quando</dt>
          <dd>
            {formatarDataEHora(inicio)}
            {barbeiro ? ` · com ${barbeiro.nomeExibicao}` : ""}
          </dd>
          <dt>Total</dt>
          <dd>{servico ? formatarPreco(servico) : "-"}</dd>
        </dl>
        <Link href={voltarParaEscolha({ inicio })} className="botao botao-secundario">
          Alterar
        </Link>
      </aside>

      {!sessao.autenticado && !semConta && (
        <section aria-labelledby="como-reservar">
          <h2 id="como-reservar">Como você quer reservar?</h2>
          <div className="acoes">
            <LinkBotao href={`/entrar?voltar=${encodeURIComponent(`${caminho}?${busca.toString()}`)}`}>
              Entrar
            </LinkBotao>
            <Botao variante="principal" onClick={() => setSemConta(true)}>
              Continuar sem conta
            </Botao>
          </div>
        </section>
      )}

      {erro && !erro.acordando && <ErroDaReserva erro={erro} voltar={voltarParaEscolha()} />}
      {erro?.acordando && (
        <Aviso tipo="erro" focar>
          {sessao.autenticado ? (
            <>
              {TEXTOS.redeCaiuNaReserva.split("Meus agendamentos")[0]}
              <Link href="/meus-agendamentos">Meus agendamentos</Link>
              {TEXTOS.redeCaiuNaReserva.split("Meus agendamentos")[1]}
            </>
          ) : (
            // o visitante não tem Meus agendamentos: confere pelo código que recebeu
            `${TEXTOS.redeCaiuNaReserva.split(" Confira")[0]} Confira antes de tentar de novo.`
          )}
        </Aviso>
      )}
      {erro && Object.keys(camposComErro).length > 0 && (
        <Aviso tipo="erro">{mensagemDoErro({ code: "VALIDACAO_INVALIDA", status: 400 })}</Aviso>
      )}
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}

      {sessao.autenticado ? (
        <form onSubmit={aoEnviar} aria-label="Confirmar reserva">
          {botao}
        </form>
      ) : (
        semConta && (
          <form onSubmit={aoEnviar} noValidate aria-label="Dados do visitante">
            <Campo
              id="nome"
              rotulo="Nome"
              autoComplete="name"
              value={valores.nome}
              onChange={(e) => setValores({ ...valores, nome: e.target.value })}
              erro={camposComErro.nome}
            />
            <Campo
              id="email"
              rotulo="E-mail"
              type="email"
              autoComplete="email"
              value={valores.email}
              onChange={(e) => setValores({ ...valores, email: e.target.value })}
              erro={camposComErro.email}
            />
            <Campo
              id="whatsapp"
              rotulo="WhatsApp"
              type="tel"
              autoComplete="tel"
              value={valores.whatsapp}
              onChange={(e) => setValores({ ...valores, whatsapp: e.target.value })}
              erro={camposComErro.whatsapp}
            />
            <ConsentimentoDeRegiao id="autoriza-regiao" marcado={regiao} aoMudar={setRegiao} politica={politica.dados}>
              <Campo
                id="cep"
                rotulo="CEP"
                inputMode="numeric"
                autoComplete="postal-code"
                ajuda="Só o bairro e a cidade são usados; o CEP não é guardado."
                value={valores.cep}
                onChange={(e) => setValores({ ...valores, cep: e.target.value })}
                erro={camposComErro.cep}
              />
            </ConsentimentoDeRegiao>
            {botao}
          </form>
        )
      )}
    </>
  );
}

function ErroDaReserva({ erro, voltar }: { erro: ErroApi; voltar: string }) {
  if (erro.code === "VALIDACAO_INVALIDA" || erro.code === "CEP_INVALIDO") return null;
  if (erro.status === 401 || erro.code === "NAO_AUTENTICADO")
    return (
      <Aviso tipo="erro" focar>
        {mensagemDoErro({ code: "NAO_AUTENTICADO", status: 401 })} <Link href="/entrar">Entrar</Link>
      </Aviso>
    );
  if (erro.status === 403)
    return (
      <Aviso tipo="erro" focar>
        Contas da equipe não fazem reservas como cliente. Use uma conta de cliente ou reserve sem conta.
      </Aviso>
    );
  const volta = ["FORA_DO_EXPEDIENTE", "ANTECEDENCIA_INSUFICIENTE", "FORA_DO_HORIZONTE", "SERVICO_INATIVO"].includes(
    erro.code,
  );
  return (
    <>
      <AvisoDeErro erro={erro} />
      {volta && <Link href={voltar}>Escolher outro horário</Link>}
    </>
  );
}
