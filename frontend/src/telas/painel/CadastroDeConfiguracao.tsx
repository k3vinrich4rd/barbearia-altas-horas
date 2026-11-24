"use client";
// Regras da agenda (A51): o fuso é só leitura e NUNCA vai no PUT (senão 400 CAMPO_DESCONHECIDO). Limites do contrato 1.11.0:
// a antecedência mínima e o horizonte foram revogados (D197) e saíram desta tela e do PUT.
import { useState, type FormEvent } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { useConsulta, useEnvio } from "@/api/hooks";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { Campo } from "@/componentes/Campo";
import { Selecao } from "@/componentes/CamposDoPainel";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import type { ConfiguracaoAgenda } from "./tipos";

const GRANULARIDADES = [5, 10, 15, 20, 30, 60];

type Campos = "intervaloEntreAtendimentosMin" | "expiracaoPagamentoMin";
const LIMITES: Record<Campos, { min: number; max: number; rotulo: string; ajuda: string }> = {
  intervaloEntreAtendimentosMin: {
    min: 0,
    max: 120,
    rotulo: "Intervalo entre atendimentos (minutos)",
    ajuda: "De 0 a 120 minutos.",
  },
  expiracaoPagamentoMin: {
    min: 3,
    max: 60,
    rotulo: "Prazo para pagar (minutos)",
    ajuda: "De 3 a 60 minutos. A reserva que não for paga nesse prazo volta a ficar livre.",
  },
};

function Formulario({
  atual,
  aoSalvar,
}: {
  atual: ConfiguracaoAgenda;
  aoSalvar: (motivo: "salvo" | "conflito") => void;
}) {
  const [valores, setValores] = useState({
    intervaloEntreAtendimentosMin: String(atual.intervaloEntreAtendimentosMin),
    expiracaoPagamentoMin: String(atual.expiracaoPagamentoMin),
  });
  const [granularidade, setGranularidade] = useState(String(atual.granularidadeMin));
  const version = atual.version;
  const [erros, setErros] = useState<Partial<Record<Campos, string>>>({});
  const envio = useEnvio();

  async function salvar(e: FormEvent) {
    e.preventDefault();
    const novos: Partial<Record<Campos, string>> = {};
    const numeros = {} as Record<Campos, number>;
    for (const c of Object.keys(LIMITES) as Campos[]) {
      const texto = valores[c].trim();
      const n = /^\d+$/.test(texto) ? Number(texto) : NaN;
      if (!(n >= LIMITES[c].min && n <= LIMITES[c].max))
        novos[c] = `Informe um número de ${LIMITES[c].min} a ${LIMITES[c].max}.`;
      else numeros[c] = n;
    }
    setErros(novos);
    if (Object.keys(novos).length > 0) return;
    // o corpo NÃO leva o fuso (somente leitura)
    const salvo = await envio.executar(async () => {
      try {
        return await chamar(
          api().PUT("/admin/configuracao", {
            body: {
              ...numeros,
              granularidadeMin: Number(granularidade) as ConfiguracaoAgenda["granularidadeMin"],
              version,
            },
          }),
        );
      } catch (falha) {
        if (falha instanceof ErroApi && falha.code === "CONFLITO_DE_VERSAO") aoSalvar("conflito");
        throw falha;
      }
    });
    if (salvo) aoSalvar("salvo");
  }

  const erroApi = envio.erro;
  return (
    <form onSubmit={salvar} noValidate aria-label="Regras da agenda">
      {erroApi && erroApi.code !== "VALIDACAO_INVALIDA" && erroApi.code !== "CONFLITO_DE_VERSAO" && (
        <AvisoDeErro erro={erroApi} focar={false} />
      )}
      <Campo
        id="c-fuso"
        rotulo="Fuso horário"
        value={atual.fuso ?? ""}
        readOnly
        ajuda="Só leitura: o fuso da barbearia não muda por aqui."
      />
      <Campo
        id="c-intervalo"
        rotulo={LIMITES.intervaloEntreAtendimentosMin.rotulo}
        inputMode="numeric"
        value={valores.intervaloEntreAtendimentosMin}
        onChange={(e) => setValores({ ...valores, intervaloEntreAtendimentosMin: e.target.value })}
        erro={erros.intervaloEntreAtendimentosMin ?? erroApi?.doCampo("intervaloEntreAtendimentosMin")}
        ajuda={LIMITES.intervaloEntreAtendimentosMin.ajuda}
      />
      <Selecao
        id="c-grade"
        rotulo="Grade de horários (minutos)"
        value={granularidade}
        onChange={(e) => setGranularidade(e.target.value)}
        erro={erroApi?.doCampo("granularidadeMin")}
        ajuda="Os horários saem de 5, 10, 15, 20, 30 ou 60 em 60 minutos, a partir do início da jornada."
      >
        {GRANULARIDADES.map((g) => (
          <option key={g} value={g}>
            {g}
          </option>
        ))}
      </Selecao>
      <Campo
        id="c-prazo"
        rotulo={LIMITES.expiracaoPagamentoMin.rotulo}
        inputMode="numeric"
        value={valores.expiracaoPagamentoMin}
        onChange={(e) => setValores({ ...valores, expiracaoPagamentoMin: e.target.value })}
        erro={erros.expiracaoPagamentoMin ?? erroApi?.doCampo("expiracaoPagamentoMin")}
        ajuda={LIMITES.expiracaoPagamentoMin.ajuda}
      />
      <div className="acoes">
        <Botao type="submit" variante="principal" disabled={envio.enviando}>
          Salvar regras
        </Botao>
      </div>
    </form>
  );
}

export function CadastroDeConfiguracao() {
  const configuracao = useConsulta(() => chamar(api().GET("/admin/configuracao")), []);
  const [aviso, setAviso] = useState<"salvo" | "conflito" | null>(null);
  return (
    <section aria-labelledby="t-regras">
      <h2 id="t-regras">Regras da agenda</h2>
      {aviso === "salvo" && (
        <Aviso tipo="sucesso">
          Regras salvas. Valem a partir da próxima requisição; as reservas que já existem não mudam.
        </Aviso>
      )}
      {aviso === "conflito" && (
        <Aviso tipo="aviso">
          Alguém alterou as regras antes de você. Atualizamos a tela: confira os campos e salve de novo.
        </Aviso>
      )}
      <EstadoDaConsulta consulta={configuracao}>
        {(atual) => (
          <Formulario
            atual={atual}
            aoSalvar={(motivo) => {
              setAviso(motivo);
              configuracao.recarregar();
            }}
          />
        )}
      </EstadoDaConsulta>
    </section>
  );
}
