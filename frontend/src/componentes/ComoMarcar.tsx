// "03 · Como marcar" (Fatia 16, F3; substitui o C2 da spec 09): o único lugar da home que explica os jeitos de marcar
// e as regras antes de pagar. Substitui a faixa "Como funciona" do herói e a seção "Dúvidas".
// O bloco "Antes de pagar" mantém o id "duvidas", para os links antigos (/#duvidas) e o menu atual continuarem chegando aqui.
import { AcaoDoJeito } from "./AcaoDoJeito";
import { WhatsappDireto } from "./SecoesDaHome";
import { DUVIDAS, TRES_JEITOS } from "@/conteudo/historia";

export function ComoMarcar() {
  return (
    <section id="como-marcar" aria-labelledby="t-como-marcar" className="secao">
      <p className="sobretitulo">03 · Como marcar</p>
      <h2 id="t-como-marcar">Três jeitos de marcar</h2>
      <ul className="tres-jeitos">
        {TRES_JEITOS.map((j, i) => (
          <li key={j.titulo}>
            <article className={j.acao.principal ? "cartao jeito jeito-principal" : "cartao jeito"}>
              <p className="sobretitulo">{j.numero}</p>
              <h3>{j.titulo}</h3>
              <p>{j.texto}</p>
              <p className="jeito-acoes">
                <AcaoDoJeito jeito={j} />
                {i === TRES_JEITOS.length - 1 && <WhatsappDireto />}
              </p>
            </article>
          </li>
        ))}
      </ul>
      <div id="duvidas" className="antes-de-pagar" aria-labelledby="t-antes-de-pagar" role="group">
        <h3 id="t-antes-de-pagar">Antes de pagar</h3>
        <div>
          {DUVIDAS.map((d) => (
            <details key={d.pergunta} className="duvida">
              <summary>
                {d.pergunta}
                <span aria-hidden="true" className="mais">
                  +
                </span>
              </summary>
              <p>{d.resposta}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
