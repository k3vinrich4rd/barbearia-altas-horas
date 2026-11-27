// Indicador das 3 etapas do agendamento pago (PDF p.10 e p.12): Horário, Pagamento e Confirmação.
// A diferença entre concluído, atual e futuro não depende de cor: ✓ nos concluídos, atual em negrito e sublinhado.
export const PASSOS_DO_AGENDAMENTO = [
  { numero: 1, rotulo: "1 · Horário" },
  { numero: 2, rotulo: "2 · Pagamento" },
  { numero: 3, rotulo: "3 · Confirmação" },
] as const;

export type PassoDoAgendamento = (typeof PASSOS_DO_AGENDAMENTO)[number]["numero"];

export function IndicadorDePassos({ passoAtual }: { passoAtual: PassoDoAgendamento }) {
  return (
    <nav aria-label="Etapas do agendamento" className="indicador-de-passos">
      <ol>
        {PASSOS_DO_AGENDAMENTO.map((passo) => {
          const atual = passo.numero === passoAtual;
          const concluido = passo.numero < passoAtual;
          return (
            <li key={passo.numero} aria-current={atual ? "step" : undefined}>
              {concluido && (
                <>
                  <span aria-hidden="true">✓ </span>
                  <span className="so-leitor">concluído, </span>
                </>
              )}
              {atual && <span className="so-leitor">etapa atual, </span>}
              <span className={atual ? "indicador-passo-atual" : undefined}>{passo.rotulo}</span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
