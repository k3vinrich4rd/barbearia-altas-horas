// Etiqueta do status da reserva (README, "Padrões da agenda"): cada estado tem uma FORMA e uma PALAVRA.
//   confirmado: bloco sólido · concluído: bloco mais baixo com visto · aguardando: tracejado
//   não compareceu: contorno sólido de 2px com ✕ · cancelado: riscado · expirado: pontilhado com relógio
import { STATUS_DA_RESERVA } from "@/telas/status";
import { Icone, type NomeDeIcone } from "./Icone";

const ICONES: Record<string, NomeDeIcone | undefined> = { CONFIRMADO: "visto", CONCLUIDO: "visto", NAO_COMPARECEU: "x", EXPIRADO: "relogio" };

export function EtiquetaDeStatus({ status }: { status: string }) {
  const icone = ICONES[status];
  return (
    <span className="etiqueta" data-status={status.toLowerCase()}>
      {icone && <Icone nome={icone} />}
      {STATUS_DA_RESERVA[status] ?? status}
    </span>
  );
}
