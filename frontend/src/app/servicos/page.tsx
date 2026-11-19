import type { Metadata } from "next";
import { LinkBotao } from "@/componentes/Botao";
import { ServicosDaHome } from "@/componentes/ListaDeServicos";

export const metadata: Metadata = { title: "Serviços" };

export default function Servicos() {
  return (
    <>
      <h1>Serviços</h1>
      <ServicosDaHome />
      <p>
        <LinkBotao href="/agendar" variante="principal">
          Agendar horário
        </LinkBotao>
      </p>
    </>
  );
}
