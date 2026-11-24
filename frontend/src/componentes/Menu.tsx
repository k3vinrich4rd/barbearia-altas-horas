"use client";
// Cabeçalho do site (PDF, pág. 1): marca, menu com as âncoras da home, links da conta e o botão "Agendar horário".
// No celular o menu fica recolhido atrás do botão "Menu" (padrão Disclosure da APG), para a ação principal
// aparecer na primeira tela. No desktop tudo fica visível, em duas linhas (conta em cima, seções embaixo), na
// mesma ordem do DOM, para a ordem de foco seguir a leitura. Esc fecha e devolve o foco ao botão; trocar de rota fecha.
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { IMAGENS } from "@/conteudo/historia";
import { useSessao } from "./Sessao";

/** Itens do menu (Fatia 12, A2): as seções da home na ordem em que o cliente decide e "Onde estamos", que aponta para o rodapé
 * (em todas as páginas). "Como funciona" virou faixa do herói; "Serviços" abre a página /servicos (decisão do Kevin); a home tem o link "Ver todos". */
export const SECOES_DA_HOME: { href: string; texto: string }[] = [
  { href: "/servicos", texto: "Serviços" }, // página com a lista completa e os preços (decisão do Kevin, 23/11, opção B)
  { href: "/#barbeiro", texto: "Barbeiro" },
  { href: "/#galeria", texto: "Galeria" },
  { href: "/#trajetoria", texto: "Trajetória" },
  { href: "/#duvidas", texto: "Dúvidas" },
  { href: "#onde-estamos", texto: "Onde estamos" },
];

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
                <>
                  <li>
                    <Link href="/entrar" aria-current={atual("/entrar")}>
                      Entrar
                    </Link>
                  </li>
                  <li>
                    <Link href="/cadastro" aria-current={atual("/cadastro")}>
                      Criar conta
                    </Link>
                  </li>
                </>
              )}
            </ul>
          </nav>
          <nav aria-label="Principal" className="menu menu-principal">
            <ul>
              {SECOES_DA_HOME.map((s) => (
                <li key={s.href}>
                  <Link
                    href={s.href}
                    onClick={fechar}
                    aria-current={s.href.startsWith("/") && !s.href.includes("#") ? atual(s.href) : undefined}
                  >
                    {s.texto}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
        <Link href="/agendar" className="botao botao-principal botao-agendar-topo" aria-current={atual("/agendar")}>
          Agendar horário
        </Link>
      </div>
    </header>
  );
}
