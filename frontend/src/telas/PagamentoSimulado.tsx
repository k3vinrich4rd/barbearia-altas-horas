"use client";
// Checkout simulado do cartão (A63, D149, E28): o GatewaySimulado aponta urlCheckout para
// /pagamento-simulado/{cobrancaId}. Esta tela confirma pelo mesmo POST da simulação e não pede cartão.
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { AvisoDeErro } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";

const ROTULO_CONFIRMAR = "Ver a tela de confirmação (simula o pagamento aprovado)";

export function PagamentoSimulado({ cobrancaId }: { cobrancaId: string }) {
  const roteador = useRouter();
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<ErroApi | undefined>();

  async function confirmar() {
    setErro(undefined);
    setEnviando(true);
    try {
      const cobranca = await chamar(
        api().POST("/pagamentos/simulacao/{cobrancaId}/confirmar", {
          params: { path: { cobrancaId } },
        }),
      );
      roteador.push(`/agendar/confirmacao/${cobranca.reservaId}`);
    } catch (e) {
      setEnviando(false);
      if (e instanceof ErroApi) {
        setErro(e);
        return;
      }
      throw e;
    }
  }

  return (
    <>
      <p className="sobretitulo">Pagamento</p>
      <h1>Pagamento com cartão</h1>
      <p className="destaque">
        Esta é a tela de checkout simulado do beta. Nenhum número de cartão é pedido. Confirmar aprova o pagamento
        como o gateway faria.
      </p>
      {erro && <AvisoDeErro erro={erro} />}
      <Botao type="button" variante="principal" onClick={() => void confirmar()} disabled={enviando}>
        {enviando ? "Confirmando…" : ROTULO_CONFIRMAR}
      </Botao>
    </>
  );
}
