"use client";
// Convite do barbeiro (Fatia 10B, A36 a A38). AGUARDA DESENHO: layout neutro, só com tokens e os componentes que já existem.
// O código vem no fragmento da URL (#), que nem o servidor nem a prévia de link do WhatsApp recebem; abrir a página NÃO chama
// a API. Depois de lido, o código sai da barra de endereço (history.replaceState) e fica só na memória desta aba.
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { aceitarConvite } from "@/api/bff";
import { textoDoCodigo } from "@/api/mensagens";
import { TEXTOS } from "@/conteudo/textos";
import { MENSAGEM_SENHA, senhaValida } from "@/api/validacao";
import { useEnvio } from "@/api/hooks";
import { Aviso } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { CampoSenha } from "@/componentes/CampoSenha";
import { useSessao } from "@/componentes/Sessao";

const FORMATO_DO_CODIGO = /^[A-Za-z0-9_-]{43}$/;
/** Só para sobreviver ao desvio "Já tenho conta: entrar" na mesma aba; apagado ao aceitar. */
const CHAVE_DA_ABA = "ah_convite";

function lerCodigo(): string | null {
  const doFragmento = window.location.hash.replace(/^#/, "");
  if (doFragmento) {
    try {
      window.sessionStorage.setItem(CHAVE_DA_ABA, doFragmento);
    } catch {
      // sem armazenamento da aba: o código só vive nesta página
    }
    // o código sai da barra de endereço, do histórico e de qualquer cópia do endereço
    window.history.replaceState(null, "", window.location.pathname);
    return doFragmento;
  }
  try {
    return window.sessionStorage.getItem(CHAVE_DA_ABA);
  } catch {
    return null;
  }
}

function esquecerCodigo() {
  try {
    window.sessionStorage.removeItem(CHAVE_DA_ABA);
  } catch {
    // nada a apagar
  }
}

export function Convite() {
  const roteador = useRouter();
  const sessao = useSessao();
  const envio = useEnvio();
  // undefined = ainda lendo o fragmento; null = não há código nesta aba
  const [codigo, setCodigo] = useState<string | null | undefined>(undefined);
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [erros, setErros] = useState<Partial<Record<"email" | "senha" | "confirmacao", string>>>({});

  useEffect(() => {
    // lê o fragmento só no navegador (o servidor nunca o recebe) e o tira da barra de endereço
    setCodigo(lerCodigo());
  }, []);

  const conta = sessao.autenticado;
  const erroApi = envio.erro;
  const conviteInvalido = erroApi?.code === "CONVITE_INVALIDO";
  const doServidor: typeof erros = {};
  if (erroApi?.code === "VALIDACAO_INVALIDA") {
    doServidor.email = erroApi.doCampo("email");
    doServidor.senha = erroApi.doCampo("senha");
  }
  const campos = { ...doServidor, ...erros };
  const ordem = ["email", "senha", "confirmacao"] as const;
  const primeiroComErro = ordem.find((c) => campos[c]);

  useEffect(() => {
    if (primeiroComErro) document.getElementById(`convite-${primeiroComErro}`)?.focus();
  }, [primeiroComErro, erroApi]);

  if (codigo === undefined) return <p role="status">{TEXTOS.carregando}</p>;

  if (!codigo || !FORMATO_DO_CODIGO.test(codigo)) {
    return (
      <>
        <h1>Link do convite</h1>
        <Aviso tipo="erro">
          Este link não vale mais ou não pode ser usado aqui. Peça outro convite ao dono da barbearia.
        </Aviso>
        <p>
          <Link href="/entrar">Já tenho conta no site: entrar</Link>
        </p>
      </>
    );
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    const novos: typeof erros = {};
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) novos.email = "Digite o seu e-mail.";
    if (!conta) {
      if (!senhaValida(senha)) novos.senha = MENSAGEM_SENHA;
      else if (confirmacao !== senha) novos.confirmacao = "As duas senhas precisam ser iguais.";
    }
    setErros(novos);
    if (Object.keys(novos).length > 0 || !codigo) return;
    const ok = await envio.executar(async () => {
      await aceitarConvite(codigo, email.trim(), conta ? undefined : senha);
      return true;
    });
    setSenha("");
    setConfirmacao("");
    if (!ok) return;
    esquecerCodigo();
    setCodigo(null);
    if (conta) await sessao.atualizar(); // o BFF encerrou a sessão: o papel novo só vem no próximo login
    roteador.push(conta ? "/entrar?aviso=convite-aceito-conta" : "/entrar?aviso=convite-aceito");
    roteador.refresh();
  }

  const aviso = conviteInvalido ? (
    <Aviso tipo="erro" focar>
      {textoDoCodigo("CONVITE_INVALIDO")}
    </Aviso>
  ) : erroApi && erroApi.code !== "VALIDACAO_INVALIDA" && erroApi.status !== 429 ? (
    <Aviso tipo="erro" focar>
      {textoDoCodigo(erroApi.code)}
    </Aviso>
  ) : primeiroComErro ? (
    <Aviso tipo="erro">{textoDoCodigo("VALIDACAO_INVALIDA")}</Aviso>
  ) : null;

  return (
    <>
      <h1>{conta ? "Aceitar convite" : "Criar minha senha"}</h1>
      {conta ? (
        <p>
          Isto soma o papel de barbeiro à sua conta. Sua senha não muda. Digite o e-mail da sua conta para confirmar.
          Depois de aceitar, entre de novo para ver o painel.
        </p>
      ) : (
        <p>
          O dono da barbearia convidou você. Digite o seu e-mail e crie a sua senha: é com eles que você entra no
          painel. A senha é só sua, nunca peça nem mande por mensagem.
        </p>
      )}
      <form onSubmit={aoEnviar} noValidate aria-label={conta ? "Aceitar convite" : "Criar minha senha"}>
        {aviso}
        <Campo
          id="convite-email"
          rotulo="Seu e-mail"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          erro={campos.email}
        />
        {!conta && (
          <>
            <CampoSenha
              id="convite-senha"
              rotulo="Senha"
              autoComplete="new-password"
              ajuda="De 10 a 72 caracteres (acentos e símbolos contam mais)."
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              erro={campos.senha}
            />
            <CampoSenha
              id="convite-confirmacao"
              rotulo="Repita a senha"
              autoComplete="new-password"
              value={confirmacao}
              onChange={(e) => setConfirmacao(e.target.value)}
              erro={campos.confirmacao}
            />
          </>
        )}
        {envio.esperando && <Aviso tipo="aviso">Muitas tentativas. Tente de novo em {envio.restante} s.</Aviso>}
        <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
          {conta ? "Aceitar convite" : "Criar minha senha"}
        </Botao>
      </form>
      {!conta && (
        <p>
          <Link href="/entrar?voltar=%2Fconvite">Já tenho conta no site: entrar</Link>
        </p>
      )}
    </>
  );
}
