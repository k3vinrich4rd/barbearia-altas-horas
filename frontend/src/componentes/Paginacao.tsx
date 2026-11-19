"use client";
import { Botao } from "./Botao";

/** Páginas de 0 a N-1 (a API conta a partir de 0). */
export function Paginacao({
  pagina,
  tamanho,
  total,
  aoMudar,
}: {
  pagina: number;
  tamanho: number;
  total: number;
  aoMudar: (p: number) => void;
}) {
  const paginas = Math.max(1, Math.ceil(total / tamanho));
  return (
    <nav aria-label="Paginação" className="paginacao">
      <Botao disabled={pagina <= 0} onClick={() => aoMudar(pagina - 1)}>
        Anterior
      </Botao>
      <span>
        Página {pagina + 1} de {paginas}
      </span>
      <Botao disabled={pagina + 1 >= paginas} onClick={() => aoMudar(pagina + 1)}>
        Próxima
      </Botao>
    </nav>
  );
}
