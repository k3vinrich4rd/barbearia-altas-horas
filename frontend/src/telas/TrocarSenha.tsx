"use client";
// Troca da senha inicial (A43). Com `deveTrocarSenha`, o proxy.ts manda TODA navegação para cá.
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { useConsulta, useEnvio } from "@/api/hooks";
import { MENSAGEM_SENHA, senhaValida } from "@/api/validacao";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { CampoSenha } from "@/componentes/CampoSenha";
import { useSessao } from "@/componentes/Sessao";

export function TrocarSenha() {
  const roteador = useRouter();
  const sessao = useSessao();
  const envio = useEnvio();
  const perfil = useConsulta(() => chamar(api().GET("/me")), []);
  const [atual, setAtual] = useState("");
  const [nova, setNova] = useState("");
  const [erroNova, setErroNova] = useState<string | undefined>();

  async function aoEnviar(e: React.FormEvent) {
    e.preventDefault();
    if (!senhaValida(nova)) {
      setErroNova(MENSAGEM_SENHA);
      document.getElementById("nova-senha")?.focus();
      return;
    }
    setErroNova(undefined);
    const ok = await envio.executar(async () => {
      await chamar(api().PUT("/me/senha", { body: { senhaAtual: atual, novaSenha: nova } }));
      return true;
    });
    setAtual("");
    setNova("");
    if (!ok) return;
    // 204: o BFF já apagou a sessão (a API revogou os refresh). Pede novo login.
    await sessao.atualizar();
    roteador.push("/entrar?aviso=senha-trocada");
    roteador.refresh();
  }

  return (
    <>
      <h1>Trocar senha</h1>
      {perfil.dados?.deveTrocarSenha && <Aviso tipo="aviso">Antes de continuar, troque a senha inicial.</Aviso>}
      <form onSubmit={aoEnviar} noValidate aria-label="Trocar senha">
        <CampoSenha
          id="senha-atual"
          rotulo="Senha atual"
          autoComplete="current-password"
          value={atual}
          onChange={(e) => setAtual(e.target.value)}
        />
        <CampoSenha
          id="nova-senha"
          rotulo="Nova senha"
          autoComplete="new-password"
          ajuda="De 10 a 72 caracteres."
          value={nova}
          onChange={(e) => setNova(e.target.value)}
          erro={erroNova}
        />
        {envio.erro && <AvisoDeErro erro={envio.erro} />}
        {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
        <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
          Trocar senha
        </Botao>
      </form>
    </>
  );
}
