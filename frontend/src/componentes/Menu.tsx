"use client";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { IMAGENS } from "@/conteudo/historia";
import { useSessao } from "./Sessao";

export function Menu() {
  const sessao = useSessao();
  const router = useRouter();
  const equipe = sessao.papeis?.some((p) => p === "ADMIN" || p === "BARBEIRO");

  async function aoSair() {
    await sessao.encerrar();
    router.push("/");
    router.refresh();
  }

  return (
    <header className="cabecalho">
      <div className="cabecalho-interno">
        <Link href="/" className="marca">
          <Image src={IMAGENS.logo.src} alt="" width={48} height={48} />
          <span>Barbearia Altas Horas</span>
        </Link>
        <nav aria-label="Principal" className="menu">
          <ul>
            <li>
              <Link href="/servicos">Serviços</Link>
            </li>
            <li>
              <Link href="/reserva">Consultar reserva</Link>
            </li>
            {sessao.autenticado ? (
              <>
                {equipe && (
                  <li>
                    <Link href="/painel">Painel</Link>
                  </li>
                )}
                <li>
                  <Link href="/meus-agendamentos">Meus agendamentos</Link>
                </li>
                <li>
                  <Link href="/minha-conta">Meus dados</Link>
                </li>
                <li>
                  <button type="button" className="botao botao-secundario" onClick={aoSair}>
                    Sair
                  </button>
                </li>
              </>
            ) : (
              <>
                <li>
                  <Link href="/entrar">Entrar</Link>
                </li>
                <li>
                  <Link href="/cadastro">Criar conta</Link>
                </li>
              </>
            )}
          </ul>
        </nav>
        <Link href="/agendar" className="botao botao-principal">
          Agendar horário
        </Link>
      </div>
    </header>
  );
}
