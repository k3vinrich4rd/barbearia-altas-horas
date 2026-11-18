import type { Metadata } from "next";

export const metadata: Metadata = { title: "Painel" };

// As telas do painel (agenda, pendências, cadastros, auditoria e alcance) entram na 7B.
export default function Painel() {
  return (
    <>
      <h1>Painel</h1>
      <p>As telas do painel chegam na próxima etapa.</p>
    </>
  );
}
