"use client";
// Meus dados (A39 a A41, A49): consentimento de marketing e de região, baixar os dados e excluir a conta.
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { baixarArquivo } from "@/api/arquivo";
import { cepComOitoDigitos } from "@/api/validacao";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { diaNoFuso } from "@/api/formato";
import { useConsulta, useEnvio } from "@/api/hooks";
import { textoDoCodigo } from "@/api/mensagens";
import type { components } from "@/api/schema";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { ConsentimentoDeRegiao } from "@/componentes/ConsentimentoDeRegiao";
import { Icone } from "@/componentes/Icone";
import { Dialogo } from "@/componentes/Dialogo";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { useSessao } from "@/componentes/Sessao";

type Consentimento = components["schemas"]["Consentimento"];
type Perfil = components["schemas"]["Perfil"];

export function MinhaConta() {
  const perfil = useConsulta(() => chamar(api().GET("/me")), []);
  return (
    <>
      <h1>Meus dados</h1>
      <EstadoDaConsulta consulta={perfil}>
        {(p) => (
          <>
            <p>
              O que a barbearia guarda sobre você, e o que você pode fazer com isso. Saiba mais na{" "}
              <Link href="/privacidade">política de privacidade</Link>.
            </p>
            <section aria-labelledby="t-pessoais" className="cartao">
              <h2 id="t-pessoais">Dados pessoais</h2>
              <dl>
                <dt>Nome</dt>
                <dd>{p.nome}</dd>
                <dt>E-mail</dt>
                <dd>{p.email}</dd>
              </dl>
            </section>
            <Marketing />
            <Regiao perfil={p} />
            <Exportar />
            {p.papeis.includes("CLIENTE") && !p.papeis.includes("ADMIN") && !p.papeis.includes("BARBEIRO") && (
              <Excluir />
            )}
          </>
        )}
      </EstadoDaConsulta>
    </>
  );
}

function Marketing() {
  const consentimentos = useConsulta(() => chamar(api().GET("/me/consentimentos")), []);
  const politica = useConsulta(() => chamar(api().GET("/politica-privacidade")), []);
  const [novo, setNovo] = useState<Consentimento | undefined>();
  const [avisoEstado, setAvisoEstado] = useState("");
  const envio = useEnvio();

  const lista = consentimentos.dados;
  const marcado = novo
    ? novo.aceito
    : (lista ?? [])
        .filter((c) => c.finalidade === "MARKETING_CEP")
        .sort((a, b) => b.registradoEm.localeCompare(a.registradoEm))[0]?.aceito === true;

  async function alternar(aceito: boolean) {
    if (!politica.dados) return;
    const registrar = (versaoTexto: string) =>
      chamar(api().PUT("/me/consentimentos/marketing", { body: { aceito, versaoTexto } }));
    const r = await envio.executar(async () => {
      try {
        return await registrar(politica.dados!.versao);
      } catch (e) {
        // versão do texto mudou: busca a política de novo e tenta UMA vez (E16)
        if (e instanceof ErroApi && e.code === "VALIDACAO_INVALIDA" && e.erros.some((x) => x.field === "versaoTexto")) {
          const p = await chamar(api().GET("/politica-privacidade"));
          return registrar(p.versao);
        }
        throw e;
      }
    });
    if (r) {
      setNovo(r);
      setAvisoEstado(r.aceito ? "Marketing: aceito." : "Marketing: não aceito.");
    }
  }

  return (
    <section aria-labelledby="titulo-marketing" className="cartao">
      <h2 id="titulo-marketing">Ofertas e novidades</h2>
      <EstadoDaConsulta consulta={consentimentos}>
        {() => (
          <div className="caixa">
            <input
              id="marketing"
              type="checkbox"
              checked={marcado}
              disabled={!politica.dados || envio.enviando || envio.esperando}
              onChange={(e) => void alternar(e.target.checked)}
            />
            <label htmlFor="marketing">
              {politica.dados?.textoConsentimento ?? "Quero receber ofertas e novidades."}
            </label>
          </div>
        )}
      </EstadoDaConsulta>
      <p role="status">
        {avisoEstado || (consentimentos.dados ? (marcado ? "Marketing: aceito." : "Marketing: não aceito.") : "")}
      </p>
      {envio.erro && <AvisoDeErro erro={envio.erro} />}
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
    </section>
  );
}

/** Região no alcance (A49, ADR 0014): ao lado do marketing, revogável com uma linha de confirmação. O CEP nunca é guardado. */
function Regiao({ perfil }: { perfil: Perfil }) {
  const politica = useConsulta(() => chamar(api().GET("/politica-privacidade")), []);
  const [autorizada, setAutorizada] = useState(perfil.regiaoAutorizada === true);
  const [endereco, setEndereco] = useState(perfil.endereco);
  const [confirmando, setConfirmando] = useState(false);
  const [marcado, setMarcado] = useState(false);
  const [cep, setCep] = useState("");
  const [erroCep, setErroCep] = useState<string | undefined>();
  const envio = useEnvio();

  const lugar = endereco
    ? [[endereco.bairro, endereco.cidade].filter(Boolean).join(", "), endereco.uf].filter(Boolean).join(" - ")
    : "";

  async function retirar() {
    if (!politica.dados) return;
    const registrar = (versaoTexto: string) =>
      chamar(api().PUT("/me/consentimentos/regiao", { body: { aceito: false, versaoTexto } }));
    const r = await envio.executar(async () => {
      try {
        return await registrar(politica.dados!.versao);
      } catch (e) {
        if (e instanceof ErroApi && e.code === "VALIDACAO_INVALIDA" && e.erros.some((x) => x.field === "versaoTexto")) {
          return registrar((await chamar(api().GET("/politica-privacidade"))).versao);
        }
        throw e;
      }
    });
    if (r) {
      setAutorizada(false);
      setEndereco(undefined);
      setConfirmando(false);
      setMarcado(false);
    }
  }

  async function autorizar(e: React.FormEvent) {
    e.preventDefault();
    if (!politica.dados) return;
    if (!cepComOitoDigitos(cep)) {
      setErroCep("Informe um CEP com 8 números.");
      document.getElementById("regiao-cep")?.focus();
      return;
    }
    setErroCep(undefined);
    const r = await envio.executar(() =>
      chamar(
        api().PUT("/me", {
          body: { cep, autorizaRegiao: true, versaoTextoRegiao: politica.dados!.versao },
        }),
      ),
    );
    if (r) {
      setAutorizada(r.regiaoAutorizada === true);
      setEndereco(r.endereco);
      setCep("");
    }
  }

  return (
    <section aria-labelledby="titulo-regiao" className="cartao">
      <h2 id="titulo-regiao">Minha região</h2>
      <p>
        Mostra no site e no painel de onde vêm os clientes, sempre somada à de outras pessoas. O CEP não é guardado: ficam só
        o bairro e a cidade.
      </p>
      <p role="status">
        {autorizada
          ? `Região: autorizada${lugar ? ` (${lugar})` : " (ainda sem bairro e cidade)"}.`
          : "Região: não autorizada."}
      </p>
      {autorizada ? (
        confirmando ? (
          <p role="alert" className="acoes">
            Retirar apaga a sua região do perfil e das suas reservas.
            <Botao variante="principal" onClick={() => void retirar()} disabled={envio.enviando || envio.esperando}>
              Retirar
            </Botao>
            <Botao onClick={() => setConfirmando(false)}>Manter</Botao>
          </p>
        ) : (
          <Botao onClick={() => setConfirmando(true)}>Retirar minha autorização de região</Botao>
        )
      ) : (
        <form onSubmit={autorizar} noValidate aria-label="Autorizar região">
          <ConsentimentoDeRegiao id="regiao-autorizar" marcado={marcado} aoMudar={setMarcado} politica={politica.dados}>
            <Campo
              id="regiao-cep"
              rotulo="CEP"
              inputMode="numeric"
              autoComplete="postal-code"
              value={cep}
              onChange={(e) => setCep(e.target.value)}
              erro={erroCep}
            />
            <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
              Autorizar
            </Botao>
          </ConsentimentoDeRegiao>
        </form>
      )}
      {envio.erro && <AvisoDeErro erro={envio.erro} />}
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
    </section>
  );
}

function Exportar() {
  const envio = useEnvio();
  async function baixar() {
    const dados = await envio.executar(() => chamar(api().GET("/me/dados")));
    if (!dados) return;
    baixarArquivo(`dados-altas-horas-${diaNoFuso(new Date(dados.geradoEm))}.json`, JSON.stringify(dados, null, 2));
  }
  return (
    <section aria-labelledby="titulo-exportar" className="cartao">
      <h2 id="titulo-exportar">Seus dados</h2>
      <p>Baixe uma cópia dos dados que guardamos sobre você.</p>
      <Botao onClick={() => void baixar()} disabled={envio.enviando || envio.esperando}>
        Baixar meus dados
      </Botao>
      {envio.erro && <AvisoDeErro erro={envio.erro} />}
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
    </section>
  );
}

function Excluir() {
  const roteador = useRouter();
  const sessao = useSessao();
  const [aberto, setAberto] = useState(false);
  const [confirmacao, setConfirmacao] = useState("");
  const envio = useEnvio();

  async function excluir() {
    const ok = await envio.executar(async () => {
      await chamar(api().DELETE("/me"));
      return true;
    });
    if (!ok) return;
    await sessao.atualizar();
    roteador.push("/?aviso=conta-excluida");
    roteador.refresh();
  }

  return (
    <>
      <hr />
      <section aria-labelledby="titulo-excluir">
        <h2 id="titulo-excluir">Excluir conta</h2>
        <p>Apaga seus dados pessoais. Não tem volta.</p>
        <button type="button" className="botao botao-perigo" onClick={() => setAberto(true)}>
          <Icone nome="lixeira" /> Excluir minha conta
        </button>
        {aberto && (
          <Dialogo
            titulo="Excluir a conta?"
            aoFechar={() => {
              setAberto(false);
              setConfirmacao("");
            }}
          >
            <p>Seus dados pessoais serão apagados e você não poderá mais entrar. Isso não tem volta.</p>
            <Campo
              id="confirmar-exclusao"
              rotulo="Digite EXCLUIR para confirmar"
              autoComplete="off"
              value={confirmacao}
              onChange={(e) => setConfirmacao(e.target.value)}
            />
            {envio.erro?.code === "EXCLUSAO_BLOQUEADA_POR_RESERVA" ? (
              <Aviso tipo="aviso">{textoDoCodigo("EXCLUSAO_BLOQUEADA_POR_RESERVA")}</Aviso>
            ) : (
              envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />
            )}
            <div className="acoes">
              <Botao
                onClick={() => {
                  setAberto(false);
                  setConfirmacao("");
                }}
              >
                Cancelar
              </Botao>
              <Botao
                variante="principal"
                disabled={confirmacao !== "EXCLUIR" || envio.enviando}
                onClick={() => void excluir()}
              >
                Excluir para sempre
              </Botao>
            </div>
          </Dialogo>
        )}
      </section>
    </>
  );
}
