"use client";
// "Retirar minha autorização de região" (Fatia 11, A48, LGPD art. 8º, § 5º): o visitante apaga a região da reserva na hora, com
// o código dela. O BFF usa o cookie do visitante (ou o código que a consulta mandou) e a API devolve 204, idempotente.
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { useEnvio } from "@/api/hooks";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";

export function RetirarRegiaoDoVisitante({
  reservaId,
  codigo,
  aoRetirar,
}: {
  reservaId: string;
  /** O código da reserva, quando a tela o tem (a consulta); na confirmação o BFF usa o cookie do visitante. */
  codigo?: string;
  aoRetirar?: () => void;
}) {
  const envio = useEnvio();
  const [feito, setFeito] = useState(false);

  async function retirar() {
    const ok = await envio.executar(async () => {
      await chamar(
        api().DELETE("/agendamentos/guest/{id}/regiao", {
          params: { path: { id: reservaId } },
          ...(codigo ? { headers: { "X-Guest-Token": codigo } } : {}),
        }),
      );
      return true;
    });
    if (ok) {
      setFeito(true);
      aoRetirar?.();
    }
  }

  if (feito)
    return (
      <p role="status" className="nota">
        Pronto: a região desta reserva foi apagada e não entra mais no resumo de onde vêm os clientes.
      </p>
    );
  return (
    <div className="regiao-retirar">
      <p className="nota">
        Você autorizou usar só o seu bairro e a sua cidade no resumo de onde vêm os clientes. Retirar apaga isso agora; a
        reserva continua igual.
      </p>
      <Botao onClick={() => void retirar()} disabled={envio.enviando || envio.esperando}>
        Retirar minha autorização de região
      </Botao>
      {envio.erro && <AvisoDeErro erro={envio.erro} focar={false} />}
      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
    </div>
  );
}
