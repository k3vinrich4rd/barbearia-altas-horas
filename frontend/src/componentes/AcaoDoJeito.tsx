"use client";
// Ação de um cartão de "Como marcar". O cartão 1 (Com conta) troca "Criar conta ou entrar" por "Meus agendamentos"
// quando há sessão (Fatia 16, decisão 6, A29). A sessão vem do SessaoProvider, o mesmo do menu: /api/sessao no BFF,
// same-origin e sem cache; o cookie HttpOnly nunca é lido aqui. Carregando ou com erro, fica o estado sem sessão.
// Sem CLS: os dois rótulos ocupam a mesma célula do botão, e o que não vale fica invisível (e fora da leitura). O botão
// tem sempre o tamanho do rótulo maior, com ou sem sessão; a troca não desloca nada.
import type { JeitoDeMarcar } from "@/conteudo/historia";
import { LinkBotao } from "./Botao";
import { useSessao } from "./Sessao";

export function AcaoDoJeito({ jeito }: { jeito: JeitoDeMarcar }) {
  const sessao = useSessao();
  const variante = jeito.acao.principal ? "principal" : "secundario";
  if (!jeito.acaoComSessao)
    return (
      <LinkBotao href={jeito.acao.href} variante={variante}>
        {jeito.acao.texto}
      </LinkBotao>
    );
  const comSessao = !sessao.carregando && sessao.autenticado;
  const [atual, outra] = comSessao ? [jeito.acaoComSessao, jeito.acao] : [jeito.acao, jeito.acaoComSessao];
  return (
    <LinkBotao href={atual.href} variante={variante}>
      <span className="rotulos-alternados">
        <span>{atual.texto}</span>
        <span className="rotulo-reserva" aria-hidden="true">
          {outra.texto}
        </span>
      </span>
    </LinkBotao>
  );
}
