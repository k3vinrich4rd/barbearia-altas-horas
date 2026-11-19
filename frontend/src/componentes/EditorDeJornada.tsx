"use client";
// Editor da jornada semanal (A50, V8): a semana inteira vai de uma vez (PUT), até 28 intervalos, "HH:mm", dia 1 = segunda.
// O almoço NÃO é cadastro à parte: é a lacuna entre dois intervalos do mesmo dia.
import { useState } from "react";
import { almocosDoDia, horaCurta, NOMES_DOS_DIAS, type IntervaloJornada } from "@/telas/painel/tipos";
import { Botao } from "./Botao";
import { Icone } from "./Icone";

export const MAXIMO_DE_INTERVALOS = 28;
const HORA = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

interface Linha {
  inicio: string;
  fim: string;
}

/** Dias (1 a 7) com algum intervalo que se sobrepõe a outro; encostar (12:00 e 12:00) não é sobrepor. */
export function diasSobrepostos(semana: Linha[][]): number[] {
  const achados: number[] = [];
  semana.forEach((linhas, i) => {
    const ordenadas = linhas
      .filter((l) => HORA.test(l.inicio) && HORA.test(l.fim))
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
    for (let k = 1; k < ordenadas.length; k++) if (ordenadas[k].inicio < ordenadas[k - 1].fim) achados.push(i + 1);
  });
  return achados;
}

export function semanaDaJornada(jornada: IntervaloJornada[]): Linha[][] {
  return NOMES_DOS_DIAS.map((_, i) =>
    jornada
      .filter((j) => j.diaSemana === i + 1)
      .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
      .map((j) => ({ inicio: j.horaInicio, fim: j.horaFim })),
  );
}

export function EditorDeJornada({
  inicial,
  desabilitado,
  sobreposta = false,
  erroGeral,
  aoSalvar,
}: {
  inicial: IntervaloJornada[];
  desabilitado?: boolean;
  /** A API respondeu JORNADA_SOBREPOSTA: o erro aparece junto de cada dia que se sobrepõe. */
  sobreposta?: boolean;
  erroGeral?: string;
  aoSalvar: (jornada: IntervaloJornada[]) => void;
}) {
  const [semana, setSemana] = useState<Linha[][]>(() => semanaDaJornada(inicial));
  const [erro, setErro] = useState("");
  const total = semana.reduce((n, d) => n + d.length, 0);

  function mudar(dia: number, indice: number, campo: keyof Linha, valor: string) {
    setSemana((s) =>
      s.map((linhas, i) => (i === dia ? linhas.map((l, k) => (k === indice ? { ...l, [campo]: valor } : l)) : linhas)),
    );
  }

  function salvar() {
    setErro("");
    const jornada: IntervaloJornada[] = [];
    for (let d = 0; d < 7; d++)
      for (const l of semana[d]) {
        if (!HORA.test(l.inicio) || !HORA.test(l.fim)) {
          setErro(`Confira os horários de ${NOMES_DOS_DIAS[d]}: preencha início e fim (HH:mm).`);
          return;
        }
        if (l.fim <= l.inicio) {
          setErro(`Em ${NOMES_DOS_DIAS[d]}, o fim deve ser depois do início.`);
          return;
        }
        jornada.push({ diaSemana: d + 1, horaInicio: l.inicio, horaFim: l.fim });
      }
    if (jornada.length > MAXIMO_DE_INTERVALOS) {
      setErro(`A semana aceita no máximo ${MAXIMO_DE_INTERVALOS} intervalos.`);
      return;
    }
    // sobreposição quem decide é a API (JORNADA_SOBREPOSTA, 422): o editor só aponta o dia depois da resposta
    aoSalvar(jornada);
  }

  const marcados = sobreposta ? diasSobrepostos(semana) : [];
  return (
    <div className="jornada">
      {NOMES_DOS_DIAS.map((nome, d) => {
        const lacunas = almocosDoDia(
          semana[d]
            .filter((l) => HORA.test(l.inicio) && HORA.test(l.fim))
            .map((l) => ({ diaSemana: d + 1, horaInicio: l.inicio, horaFim: l.fim })),
          d + 1,
        );
        return (
          <fieldset key={nome} aria-describedby={marcados.includes(d + 1) ? `jornada-erro-${d + 1}` : undefined}>
            <legend>{nome.charAt(0).toUpperCase() + nome.slice(1)}</legend>
            {semana[d].length === 0 && <p className="campo-ajuda">Sem expediente.</p>}
            {semana[d].map((l, k) => (
              <div className="jornada-linha" key={k}>
                <div className="campo">
                  <label htmlFor={`j-${d}-${k}-i`}>{`Início do intervalo ${k + 1} de ${nome}`}</label>
                  <input
                    id={`j-${d}-${k}-i`}
                    type="time"
                    value={l.inicio}
                    onChange={(e) => mudar(d, k, "inicio", e.target.value)}
                  />
                </div>
                <div className="campo">
                  <label htmlFor={`j-${d}-${k}-f`}>{`Fim do intervalo ${k + 1} de ${nome}`}</label>
                  <input
                    id={`j-${d}-${k}-f`}
                    type="time"
                    value={l.fim}
                    onChange={(e) => mudar(d, k, "fim", e.target.value)}
                  />
                </div>
                <Botao
                  aria-label={`Remover o intervalo ${k + 1} de ${nome}`}
                  onClick={() =>
                    setSemana((s) => s.map((linhas, i) => (i === d ? linhas.filter((_, j) => j !== k) : linhas)))
                  }
                >
                  Remover
                </Botao>
              </div>
            ))}
            {lacunas.map((a) => (
              <p key={a.inicio} className="campo-ajuda">
                Almoço: {horaCurta(a.inicio)} às {horaCurta(a.fim)}
              </p>
            ))}
            {marcados.includes(d + 1) && (
              <p id={`jornada-erro-${d + 1}`} className="campo-erro">
                <Icone nome="alerta" />
                <span>
                  <span className="aviso-prefixo">Erro: </span>Os intervalos de {nome} se sobrepõem.
                </span>
              </p>
            )}
            <div>
              <Botao
                disabled={total >= MAXIMO_DE_INTERVALOS}
                onClick={() =>
                  setSemana((s) => s.map((linhas, i) => (i === d ? [...linhas, { inicio: "", fim: "" }] : linhas)))
                }
              >
                {`Adicionar intervalo em ${nome}`}
              </Botao>
            </div>
          </fieldset>
        );
      })}
      {(erro || erroGeral) && (
        <p className="campo-erro" role="alert">
          <Icone nome="alerta" />
          <span>
            <span className="aviso-prefixo">Erro: </span>
            {erro || erroGeral}
          </span>
        </p>
      )}
      <p className="campo-ajuda">
        {total} de {MAXIMO_DE_INTERVALOS} intervalos. Para o almoço, deixe um intervalo vazio entre dois de trabalho
        (por exemplo 07:00 às 12:00 e 13:00 às 20:00).
      </p>
      <div className="acoes">
        <Botao variante="principal" disabled={desabilitado} onClick={salvar}>
          Salvar jornada
        </Botao>
      </div>
    </div>
  );
}
