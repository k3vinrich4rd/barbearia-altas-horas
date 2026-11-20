"use client";
// Auditoria (A52, A8, V8): quem mudou o quê e quando, com filtro por ação (vai para a API: a lista é paginada). Sem IP, sem nome, sem motivo e sem WhatsApp: a API não manda. Só ADMIN.
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { formatarDataEHora } from "@/api/formato";
import type { components } from "@/api/schema";
import { useConsulta } from "@/api/hooks";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { Selecao } from "@/componentes/CamposDoPainel";
import { Paginacao } from "@/componentes/Paginacao";

const TAMANHO = 20;
type AcaoAuditada = components["schemas"]["AcaoAuditada"];

const ACOES: Record<AcaoAuditada, string> = {
  AGENDAMENTO_MANUAL_CRIADO: "Reserva manual criada",
  AGENDAMENTO_CONCLUIDO: "Atendimento concluído",
  AGENDAMENTO_NAO_COMPARECEU: "Falta registrada",
  AGENDAMENTO_CANCELADO: "Reserva cancelada pela barbearia",
  BARBEIRO_CRIADO: "Barbeiro cadastrado",
  BARBEIRO_ALTERADO: "Barbeiro alterado",
  SERVICO_CRIADO: "Serviço criado",
  SERVICO_ALTERADO: "Serviço alterado",
  CONFIGURACAO_ALTERADA: "Regras alteradas",
  JORNADA_SUBSTITUIDA: "Jornada substituída",
  FOLGA_CRIADA: "Folga lançada",
  FOLGA_REMOVIDA: "Folga removida",
  DEVOLUCAO_REGISTRADA: "Devolução registrada",
  EVENTO_REVISADO: "Aviso do gateway revisado",
};

/** Só os campos técnicos que vierem: `de → para`, ids e a lista de campos alterados. */
function detalheEmTexto(detalhe: { [chave: string]: string } | null | undefined): string {
  if (!detalhe) return "—";
  const pares = Object.entries(detalhe);
  if (pares.length === 0) return "—";
  const de = detalhe.de;
  const para = detalhe.para;
  const outros = pares.filter(([k]) => k !== "de" && k !== "para").map(([k, v]) => `${k}: ${v}`);
  return (
    [de !== undefined && para !== undefined ? `${de} → ${para}` : "", ...outros].filter(Boolean).join(" · ") || "—"
  );
}

export function PainelAuditoria() {
  const [pagina, setPagina] = useState(0);
  const [acao, setAcao] = useState("");
  const consulta = useConsulta(
    () =>
      chamar(
        api().GET("/admin/auditoria", {
          params: {
            query: { pagina, tamanho: TAMANHO, ...(acao ? { acao: acao as AcaoAuditada } : {}) },
          },
        }),
      ),
    [pagina, acao],
  );
  return (
    <>
      <h1>Auditoria</h1>
      <p>Quem mudou o quê, e quando. Toda mudança feita no painel aparece aqui, sem dados pessoais.</p>
      <div className="agenda-controles">
        <Selecao
          id="filtro-acao"
          rotulo="Ação"
          value={acao}
          onChange={(e) => {
            setAcao(e.target.value);
            setPagina(0);
          }}
        >
          <option value="">Todas</option>
          {Object.entries(ACOES).map(([codigo, nome]) => (
            <option key={codigo} value={codigo}>
              {nome}
            </option>
          ))}
        </Selecao>
      </div>
      <EstadoDaConsulta consulta={consulta}>
        {(p) =>
          p.itens.length === 0 ? (
            <p>{acao ? "Nenhuma ação deste tipo registrada." : "Nenhuma ação registrada ainda."}</p>
          ) : (
            <>
              <div className="tabela-rolavel" tabIndex={0} role="region" aria-label="Tabela da auditoria">
                <table>
                  <caption>
                    Últimas ações · página {p.pagina + 1} de {Math.max(1, Math.ceil(p.total / p.tamanho))}
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">Quando</th>
                      <th scope="col">Ação</th>
                      <th scope="col">Recurso</th>
                      <th scope="col">Detalhe</th>
                    </tr>
                  </thead>
                  <tbody>
                    {p.itens.map((a, i) => (
                      <tr key={a.id ?? i}>
                        <td className="hora">{a.ocorridoEm ? formatarDataEHora(a.ocorridoEm) : "—"}</td>
                        <td>
                          {a.acao ? (ACOES[a.acao as AcaoAuditada] ?? a.acao) : "—"}
                          {a.acao && ACOES[a.acao as AcaoAuditada] && (
                            <>
                              <br />
                              <code>{a.acao}</code>
                            </>
                          )}
                        </td>
                        <td>{a.recurso}</td>
                        <td>{detalheEmTexto(a.detalhe)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Paginacao pagina={p.pagina} tamanho={p.tamanho} total={p.total} aoMudar={setPagina} />
            </>
          )
        }
      </EstadoDaConsulta>
    </>
  );
}
