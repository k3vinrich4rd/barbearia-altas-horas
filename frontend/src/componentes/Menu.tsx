"use client";
// Cabeçalho do site (PDF, pág. 1): marca, menu com as âncoras da home, links da conta e o botão "Agendar horário".
// No celular o menu fica recolhido atrás do botão "Menu" (padrão Disclosure da APG), para a ação principal
// aparecer na primeira tela. No desktop tudo fica visível, em duas linhas (conta em cima, seções embaixo), na
// mesma ordem do DOM, para a ordem de foco seguir a leitura. Esc fecha e devolve o foco ao botão; trocar de rota fecha.
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { desativarNoAparelho, tirarAparelhoNoLogout } from "@/api/push";
import { IMAGENS } from "@/conteudo/historia";
import { useSessao } from "./Sessao";
import { ehPaginaDeTarefa } from "./tipoDePagina";

/** Itens do menu (Fatia 16, F2, A13): as 4 seções da home, numeradas na ordem da página. Cada assunto tem um lugar só:
 * saem "Barbeiro", "Galeria" e "Dúvidas" (viram parte de Quem somos, O que fazemos e Como marcar). "Quem somos" aponta
 * para a seção `quem-somos` do F4, que juntou barbeiro e Trajetória; "Como marcar" veio com o F3. */
export const SECOES_DA_HOME: { href: string; numero: string; texto: string }[] = [
  { href: "/#quem-somos", numero: "01", texto: "Quem somos" },
  { href: "/#servicos", numero: "02", texto: "O que fazemos" },
  { href: "/#como-marcar", numero: "03", texto: "Como marcar" },
  { href: "/#onde-estamos", numero: "04", texto: "Onde estamos" },
];

/** O "Agendar horário" do topo não aparece nas telas de tarefa (A15). Na home ele só existe no menu do celular: no
 * computador, o herói já tem o botão (A3). */
export const mostraAgendarNoTopo = (caminho: string) => !ehPaginaDeTarefa(caminho);

export function Menu() {
  const sessao = useSessao();
  const router = useRouter();
  const caminho = usePathname();
  const equipe = sessao.papeis?.some((p) => p === "ADMIN" || p === "BARBEIRO");
  const [aberto, setAberto] = useState(false);
  const botaoMenu = useRef<HTMLButtonElement>(null);

  // Trocou de rota: o menu fecha (o FocoNoTitulo leva o foco ao h1 da página nova).
  useEffect(() => {
    setAberto(false);
  }, [caminho]);

  useEffect(() => {
    if (!aberto) return;
    function aoTeclar(e: KeyboardEvent) {
      if (e.key !== "Escape") return;
      setAberto(false);
      botaoMenu.current?.focus();
    }
    document.addEventListener("keydown", aoTeclar);
    return () => document.removeEventListener("keydown", aoTeclar);
  }, [aberto]);

  async function aoSair() {
    // O barbeiro que sai leva o aparelho junto das notificações (a inscrição é da conta); falha aqui nunca trava o logout.
    if (sessao.papeis?.some((p) => p === "ADMIN" || p === "BARBEIRO")) {
      await tirarAparelhoNoLogout(desativarNoAparelho, (endpoint) =>
        chamar(api().DELETE("/admin/notificacoes/inscricao", { body: { endpoint } })).then(() => undefined),
      );
    }
    await sessao.encerrar();
    router.push("/");
    router.refresh();
  }

  const atual = (href: string) => (caminho === href ? ("page" as const) : undefined);
  const fechar = () => setAberto(false);

  return (
    <header className="cabecalho">
      <div className="cabecalho-interno">
        <Link href="/" className="marca">
          <Image src={IMAGENS.logo.src} alt="" width={48} height={48} />
          <span>Barbearia Altas Horas</span>
        </Link>
        <button
          ref={botaoMenu}
          type="button"
          className="botao-mini botao-menu"
          aria-expanded={aberto}
          aria-controls="menu-do-site"
          onClick={() => setAberto(!aberto)}
        >
          {aberto ? "Fechar menu" : "Menu"}
        </button>
        <div id="menu-do-site" className="menu-do-site" data-aberto={aberto}>
          <nav aria-label="Conta" className="menu menu-conta">
            <ul>
              <li>
                <Link href="/reserva" aria-current={atual("/reserva")}>
                  Consultar reserva
                </Link>
              </li>
              {sessao.autenticado ? (
                <>
                  {equipe && (
                    <li>
                      <Link href="/painel" aria-current={atual("/painel")}>
                        Painel
                      </Link>
                    </li>
                  )}
                  <li>
                    <Link href="/meus-agendamentos" aria-current={atual("/meus-agendamentos")}>
                      Meus agendamentos
                    </Link>
                  </li>
                  <li>
                    <Link href="/minha-conta" aria-current={atual("/minha-conta")}>
                      Meus dados
                    </Link>
                  </li>
                  <li>
                    <button type="button" className="botao botao-secundario" onClick={aoSair}>
                      Sair
                    </button>
                  </li>
                </>
              ) : (
                <li>
                  <Link href="/entrar" aria-current={atual("/entrar")}>
                    Entrar ou criar conta
                  </Link>
                </li>
              )}
            </ul>
          </nav>
          <nav aria-label="Principal" className="menu menu-principal">
            <ul>
              {SECOES_DA_HOME.map((s) => (
                <li key={s.href}>
                  <Link href={s.href} onClick={fechar}>
                    <span aria-hidden="true" className="menu-numero">
                      {s.numero}
                    </span>{" "}
                    {s.texto}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          {mostraAgendarNoTopo(caminho) && (
            <Link
              href="/agendar"
              onClick={fechar}
              className={`botao botao-principal botao-agendar-topo${caminho === "/" ? " so-no-menu" : ""}`}
            >
              Agendar horário
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
