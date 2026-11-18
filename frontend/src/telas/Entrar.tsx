"use client";
// Entrar (A36). Uma única mensagem para credencial errada (não diz se foi e-mail ou senha).
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { entrar } from "@/api/bff";
import { useEnvio } from "@/api/hooks";
import { voltarSeguro } from "@/api/sessao-encerrada";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { useSessao } from "@/componentes/Sessao";

const AVISOS: Record<string, string> = {
  sessao: "Sua sessão terminou. Entre de novo.",
  "conta-criada": "Conta criada. Entre para continuar.",
  "senha-trocada": "Senha trocada. Entre com a nova senha.",
};

export function Entrar() {
  const roteador = useRouter();
  const busca = useSearchParams();
  const sessao = useSessao();
  const envio = useEnvio();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const aviso = AVISOS[busca.get("aviso") ?? ""];

  async function aoEnviar(e: React.FormEvent) {
    e.preventDefault();
    const r = await envio.executar(() => entrar(email, senha));
    if (!r) return;
    setSenha("");
    await sessao.atualizar();
    const papeis = r.usuario.papeis ?? [];
    let destino: string;
    if (r.deveTrocarSenha) destino = "/conta/trocar-senha";
    else if (papeis.includes("ADMIN") || papeis.includes("BARBEIRO")) destino = "/painel";
    else destino = voltarSeguro(busca.get("voltar"), "/meus-agendamentos");
    roteador.push(destino);
    roteador.refresh();
  }

  return (
    <>
      <nav aria-label="Entrar ou criar conta" className="alternador">
        <Link href="/entrar" aria-current="page">
          Entrar
        </Link>
        <Link href="/cadastro">Criar conta</Link>
      </nav>
      <h1>Entrar</h1>
      {aviso && <Aviso tipo="info">{aviso}</Aviso>}
      <form onSubmit={aoEnviar} aria-label="Entrar">
        <Campo
          id="email"
          rotulo="E-mail"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Campo
          id="senha"
          rotulo="Senha"
          type="password"
          autoComplete="current-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
        />
        {envio.erro && <AvisoDeErro erro={envio.erro} />}
        {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
        <Botao type="submit" variante="principal" disabled={envio.enviando || envio.esperando}>
          Entrar
        </Botao>
      </form>
      <p>
        Ainda não tem conta? <Link href="/cadastro">Criar conta</Link>
      </p>
    </>
  );
}
