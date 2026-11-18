"use client";
// Política de privacidade (A42): Markdown sem HTML cru. Os títulos do texto descem um nível (o h1 é da página).
import type { Components } from "react-markdown";
import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import { api, chamar } from "@/api/cliente";
import { formatarDataLonga } from "@/api/formato";
import { useConsulta } from "@/api/hooks";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";

const TITULOS: Components = {
  h1: ({ children }) => <h2>{children}</h2>,
  h2: ({ children }) => <h3>{children}</h3>,
  h3: ({ children }) => <h4>{children}</h4>,
  h4: ({ children }) => <h5>{children}</h5>,
  h5: ({ children }) => <h6>{children}</h6>,
};

export function Privacidade() {
  const consulta = useConsulta(() => chamar(api().GET("/politica-privacidade")), []);
  return (
    <>
      <h1>Política de privacidade</h1>
      <EstadoDaConsulta consulta={consulta}>
        {(p) => (
          <>
            <p className="nota">
              Versão {p.versao} · atualizada em {formatarDataLonga(p.atualizadaEm)}
            </p>
            <article className="politica">
              <ReactMarkdown rehypePlugins={[rehypeSanitize]} components={TITULOS}>
                {p.politica}
              </ReactMarkdown>
            </article>
          </>
        )}
      </EstadoDaConsulta>
    </>
  );
}
