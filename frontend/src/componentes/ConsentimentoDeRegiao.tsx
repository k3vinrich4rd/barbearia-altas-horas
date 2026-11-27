"use client";
// A caixinha de consentimento de região (Fatia 11, A47, ADR 0014): nasce DESMARCADA, não é obrigatória e o texto vem da API
// (a versão vigente da política). O campo de CEP (os `children`) só aparece com a caixinha marcada: sem marcar, o CEP nem
// existe na tela e nunca sai do navegador. Ao lado dela, o canal de contato e o link da política (art. 8º, § 2º).
import Link from "next/link";
import type { ReactNode } from "react";
import type { components } from "@/api/schema";

type Politica = components["schemas"]["PoliticaPrivacidade"];

const TEXTO_SEM_POLITICA =
  "Autorizo usar só meu bairro e minha cidade, somados aos de outros clientes, para mostrar no site de onde vêm os clientes da barbearia. (Indisponível agora: é opcional.)";

export function ConsentimentoDeRegiao({
  id = "autoriza-regiao",
  marcado,
  aoMudar,
  politica,
  desabilitado = false,
  children,
}: {
  id?: string;
  marcado: boolean;
  aoMudar: (marcado: boolean) => void;
  politica?: Politica;
  desabilitado?: boolean;
  children?: ReactNode;
}) {
  return (
    <div className="regiao-consentimento">
      <div className="caixa">
        <input
          id={id}
          type="checkbox"
          checked={marcado}
          disabled={!politica || desabilitado}
          aria-describedby={`${id}-contato`}
          onChange={(e) => aoMudar(e.target.checked)}
        />
        <label htmlFor={id}>{politica?.textoConsentimentoRegiao ?? TEXTO_SEM_POLITICA}</label>
      </div>
      <p id={`${id}-contato`} className="nota">
        {politica?.canalDeContato ? `Para falar sobre seus dados: ${politica.canalDeContato}. ` : ""}
        <Link href="/privacidade" className="link-solto">
          Saiba mais na política de privacidade
        </Link>
      </p>
      {marcado && children}
    </div>
  );
}
