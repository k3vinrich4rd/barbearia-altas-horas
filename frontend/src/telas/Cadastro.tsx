"use client";
// Cadastro (A33 a A35). O CEP NUNCA derruba o cadastro (R31): a busca só ajuda a conferir o endereço.
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { cadastrarConta } from "@/api/bff";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { useConsulta, useEnvio } from "@/api/hooks";
import { textoDoCodigo } from "@/api/mensagens";
import { voltarSeguro } from "@/api/sessao-encerrada";
import { apenasDigitos, cepComOitoDigitos, MENSAGEM_SENHA, senhaValida } from "@/api/validacao";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { useSessao } from "@/componentes/Sessao";

export const TEMPO_LIMITE_CEP_MS = 3000;

type SituacaoCep = { tipo: "nenhuma" } | { tipo: "achou"; texto: string } | { tipo: "aviso"; texto: string };

export function Cadastro() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const sessao = useSessao();
  const envio = useEnvio();
  const [v, setV] = useState({ nome: "", email: "", whatsapp: "", senha: "", cep: "" });
  const [marketing, setMarketing] = useState(false);
  const [cep, setCep] = useState<SituacaoCep>({ tipo: "nenhuma" });
  const [erroSenha, setErroSenha] = useState<string | undefined>();
  const politica = useConsulta(() => chamar(api().GET("/politica-privacidade")), []);

  const campos: Record<string, string> = {};
  if (envio.erro?.code === "VALIDACAO_INVALIDA") for (const e of envio.erro.erros) campos[e.field] = e.message;
  if (envio.erro?.code === "EMAIL_JA_CADASTRADO") campos.email = textoDoCodigo("EMAIL_JA_CADASTRADO");
  const ordem = ["nome", "email", "whatsapp", "senha", "cep"];

  useEffect(() => {
    const primeiro = ordem.find((c) => campos[c]);
    if (primeiro) document.getElementById(primeiro)?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [envio.erro]);

  async function consultarCep() {
    if (!cepComOitoDigitos(v.cep)) {
      setCep({ tipo: "nenhuma" });
      return;
    }
    try {
      const e = await chamar(api().GET("/cep/{cep}", { params: { path: { cep: apenasDigitos(v.cep) } }, signal: AbortSignal.timeout(TEMPO_LIMITE_CEP_MS) }));
      const partes = [e.bairro, e.cidade].filter(Boolean).join(", ");
      const lugar = [partes, e.uf].filter(Boolean).join(" - ");
      setCep(lugar ? { tipo: "achou", texto: lugar } : { tipo: "aviso", texto: textoDoCodigo("CEP_INDISPONIVEL") });
    } catch (erro) {
      if (erro instanceof ErroApi && erro.code === "CEP_NAO_ENCONTRADO") setCep({ tipo: "aviso", texto: textoDoCodigo("CEP_NAO_ENCONTRADO") });
      else setCep({ tipo: "aviso", texto: textoDoCodigo("CEP_INDISPONIVEL") });
    }
  }

  async function aoEnviar(e: React.FormEvent) {
    e.preventDefault();
    if (!senhaValida(v.senha)) {
      setErroSenha(MENSAGEM_SENHA);
      document.getElementById("senha")?.focus();
      return;
    }
    setErroSenha(undefined);
    const resposta = await envio.executar(() =>
      cadastrarConta({ nome: v.nome, email: v.email, whatsapp: v.whatsapp, senha: v.senha, ...(v.cep.trim() ? { cep: v.cep } : {}), aceitaMarketing: marketing }),
    );
    if (!resposta) return;
    setV((x) => ({ ...x, senha: "" }));
    const voltar = busca.get("voltar");
    if (resposta.sessao === false) {
      roteador.push(`/entrar?aviso=conta-criada${voltar ? `&voltar=${encodeURIComponent(voltar)}` : ""}`);
      return;
    }
    await sessao.atualizar();
    roteador.push(voltarSeguro(voltar, "/meus-agendamentos"));
    roteador.refresh();
  }

  const set = (k: keyof typeof v) => (e: React.ChangeEvent<HTMLInputElement>) => setV({ ...v, [k]: e.target.value });

  return (
    <>
      <nav aria-label="Entrar ou criar conta" className="alternador">
        <Link href="/entrar">Entrar</Link>
        <Link href="/cadastro" aria-current="page">Criar conta</Link>
      </nav>
      <h1>Criar conta</h1>
      <form onSubmit={aoEnviar} noValidate aria-label="Criar conta">
        <Campo id="nome" rotulo="Nome" autoComplete="name" value={v.nome} onChange={set("nome")} erro={campos.nome} />
        <Campo id="email" rotulo="E-mail" type="email" autoComplete="email" value={v.email} onChange={set("email")} erro={campos.email} />
        <Campo id="whatsapp" rotulo="WhatsApp" type="tel" autoComplete="tel" value={v.whatsapp} onChange={set("whatsapp")} erro={campos.whatsapp} />
        <Campo
          id="senha"
          rotulo="Senha"
          type="password"
          autoComplete="new-password"
          ajuda="De 10 a 72 caracteres."
          value={v.senha}
          onChange={set("senha")}
          erro={erroSenha ?? campos.senha}
        />
        <Campo id="cep" rotulo="CEP (opcional)" inputMode="numeric" autoComplete="postal-code" value={v.cep} onChange={set("cep")} onBlur={consultarCep} erro={campos.cep} />
        <div aria-live="polite">
          {cep.tipo === "achou" && <p>{cep.texto}</p>}
          {cep.tipo === "aviso" && <Aviso tipo="aviso">{cep.texto}</Aviso>}
        </div>
        <p className="nota">Se o endereço não puder ser buscado agora, o cadastro continua e seu CEP é salvo. O endereço completa depois.</p>

        <div className="caixa">
          <input id="marketing" type="checkbox" checked={marketing} disabled={!politica.dados} onChange={(e) => setMarketing(e.target.checked)} />
          <label htmlFor="marketing">
            {politica.dados ? politica.dados.textoConsentimento : "Receber ofertas e novidades (indisponível agora, é opcional)."}{" "}
            <Link href="/privacidade">Política de privacidade</Link>
          </label>
        </div>

        {envio.erro && envio.erro.code !== "EMAIL_JA_CADASTRADO" && envio.erro.code !== "VALIDACAO_INVALIDA" && <AvisoDeErro erro={envio.erro} />}
        {envio.erro?.code === "VALIDACAO_INVALIDA" && <Aviso tipo="erro">{textoDoCodigo("VALIDACAO_INVALIDA")}</Aviso>}
        {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
        <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
          Criar conta
        </Botao>
      </form>
      <p>
        Já tem conta? <Link href="/entrar">Entrar</Link>
      </p>
    </>
  );
}
