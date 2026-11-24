"use client";
// Ativação do aviso de horário novo no aparelho do barbeiro (Fatia 13, A41). AGUARDA DESENHO (D148): layout neutro, só com
// tokens e componentes que existem. Cinco estados, cada um com palavra e ícone (nunca só cor): sem-suporte, ios-fora-da-tela-inicial,
// permissao-negada, desligado e ativo. A permissão do navegador só é pedida DEPOIS de um toque em "Ativar" (regra dos navegadores).
import { useCallback, useEffect, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { useConsulta, useEnvio } from "@/api/hooks";
import {
  ativarNoAparelho,
  desativarNoAparelho,
  estadoDoPush,
  INSTRUCAO_DO_IOS_ANTIGO,
  INSTRUCAO_DO_IPHONE,
  lerAmbiente,
  type AmbienteDePush,
  type Diagnostico,
} from "@/api/push";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";

interface Props {
  /** Só os testes trocam: lê o navegador de verdade por padrão. */
  lerNavegador?: () => Promise<AmbienteDePush>;
}

export function PainelNotificacoes({ lerNavegador = lerAmbiente }: Props) {
  const [diagnostico, setDiagnostico] = useState<Diagnostico>({ estado: "carregando", iosAntigo: false });
  const [mensagem, setMensagem] = useState("");
  const chave = useConsulta(() => chamar(api().GET("/admin/notificacoes/chave-publica")), []);
  const envio = useEnvio();

  const atualizar = useCallback(async () => {
    setDiagnostico(estadoDoPush(await lerNavegador()));
  }, [lerNavegador]);
  useEffect(() => {
    void atualizar();
  }, [atualizar]);

  async function ativar(chavePublica: string) {
    setMensagem("");
    const resultado = await envio.executar(async () => {
      const inscricao = await ativarNoAparelho(chavePublica);
      if (inscricao === "negada") return "negada" as const;
      await chamar(api().POST("/admin/notificacoes/inscricao", { body: inscricao }));
      return "ativa" as const;
    });
    if (resultado === "negada") setMensagem("Você não liberou as notificações. Toque em Ativar para tentar de novo.");
    if (resultado === "ativa") setMensagem("Notificações ativadas neste aparelho.");
    await atualizar();
  }

  async function desativar() {
    setMensagem("");
    const feito = await envio.executar(async () => {
      const endpoint = await desativarNoAparelho();
      if (endpoint) await chamar(api().DELETE("/admin/notificacoes/inscricao", { body: { endpoint } }));
      return true;
    });
    if (feito) setMensagem("Notificações desligadas neste aparelho.");
    await atualizar();
  }

  async function testar() {
    setMensagem("");
    const feito = await envio.executar(async () => {
      await chamar(api().POST("/admin/notificacoes/teste"));
      return true;
    });
    if (feito) setMensagem("Teste enviado. Se não chegar em alguns segundos, confira as permissões do aparelho.");
  }

  return (
    <>
      <h1>Notificações</h1>
      <p>
        Receba um aviso no celular quando um cliente marcar horário com você. O aviso só sai depois do pagamento
        confirmado e mostra o serviço, o dia e a hora. Os avisos também aparecem em Novidades, aqui no painel.
      </p>
      <EstadoDaConsulta consulta={chave}>
        {(dadosDaChave) => (
          <section aria-labelledby="notificacoes-estado">
            <h2 id="notificacoes-estado">Este aparelho</h2>
            <CorpoDoEstado
              diagnostico={diagnostico}
              disponivel={dadosDaChave.disponivel}
              enviando={envio.enviando}
              aoAtivar={() => dadosDaChave.chavePublica && ativar(dadosDaChave.chavePublica)}
              aoDesativar={desativar}
              aoTestar={testar}
            />
          </section>
        )}
      </EstadoDaConsulta>
      {mensagem && <Aviso tipo="info">{mensagem}</Aviso>}
      {envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />}
    </>
  );
}

function CorpoDoEstado({
  diagnostico,
  disponivel,
  enviando,
  aoAtivar,
  aoDesativar,
  aoTestar,
}: {
  diagnostico: Diagnostico;
  disponivel: boolean;
  enviando: boolean;
  aoAtivar: () => void;
  aoDesativar: () => void;
  aoTestar: () => void;
}) {
  switch (diagnostico.estado) {
    case "carregando":
      return <p role="status">Verificando este aparelho…</p>;
    case "sem-suporte":
      return (
        <Aviso tipo="aviso">
          {diagnostico.iosAntigo
            ? INSTRUCAO_DO_IOS_ANTIGO
            : "Este navegador não recebe notificações. Os avisos continuam aparecendo em Novidades, aqui no painel."}
        </Aviso>
      );
    case "ios-fora-da-tela-inicial":
      return <Aviso tipo="aviso">{INSTRUCAO_DO_IPHONE}</Aviso>;
    case "permissao-negada":
      return (
        <Aviso tipo="aviso">
          As notificações estão bloqueadas neste aparelho. Libere nas configurações do navegador e volte aqui.
        </Aviso>
      );
    case "desligado":
      return (
        <>
          <p>
            <span className="etiqueta" data-status="expirado">
              Desligado
            </span>{" "}
            Este aparelho não recebe avisos.
          </p>
          {!disponivel && <Aviso tipo="aviso">As notificações estão indisponíveis no momento. Tente mais tarde.</Aviso>}
          <div className="acoes">
            <Botao variante="principal" disabled={enviando || !disponivel} onClick={aoAtivar}>
              Ativar notificações
            </Botao>
          </div>
        </>
      );
    case "ativo":
      return (
        <>
          <p>
            <span className="etiqueta" data-status="confirmado">
              Ativo
            </span>{" "}
            Este aparelho recebe os avisos de horário novo.
          </p>
          <div className="acoes">
            <Botao disabled={enviando} onClick={aoTestar}>
              Enviar um teste
            </Botao>
            <Botao disabled={enviando} onClick={aoDesativar}>
              Desativar
            </Botao>
          </div>
        </>
      );
  }
}
