"use client";
// Convite do barbeiro no painel (Fatia 10B, A35). AGUARDA DESENHO: layout neutro, só com tokens e componentes que existem.
// O código aparece UMA vez, no diálogo, como link /convite#<codigo> e QR; fechou, só resta "convite pendente até <data>".
import { useState } from "react";
import { api, chamar } from "@/api/cliente";
import { formatarDataEHora } from "@/api/formato";
import { useEnvio } from "@/api/hooks";
import { AvisoDeErro, Aviso } from "@/componentes/Aviso";
import { Botao } from "@/componentes/Botao";
import { CopiarTexto } from "@/componentes/CopiarTexto";
import { Dialogo } from "@/componentes/Dialogo";
import { QrPix } from "@/componentes/QrPix";
import type { BarbeiroAdmin } from "./tipos";

/** O estado do acesso por palavra (nunca só cor). */
export const ROTULO_DO_ACESSO: Record<string, string> = {
  SEM_CONTA: "Sem acesso",
  CONVITE_PENDENTE: "Convite pendente",
  ATIVO: "Acesso ativo",
  DESLIGADO: "Desligado",
};

const FORMA_DO_ACESSO: Record<string, string> = {
  SEM_CONTA: "expirado",
  CONVITE_PENDENTE: "aguardando_pagamento",
  ATIVO: "confirmado",
  DESLIGADO: "cancelado",
};

export function EtiquetaDeAcesso({ barbeiro }: { barbeiro: BarbeiroAdmin }) {
  const estado = barbeiro.acesso ?? "SEM_CONTA";
  return (
    <>
      <span className="etiqueta" data-status={FORMA_DO_ACESSO[estado]}>
        {ROTULO_DO_ACESSO[estado] ?? estado}
      </span>
      {estado === "CONVITE_PENDENTE" && barbeiro.conviteExpiraEm && (
        <p className="campo-ajuda">Convite pendente até {formatarDataEHora(barbeiro.conviteExpiraEm)}</p>
      )}
    </>
  );
}

interface Gerado {
  link: string;
  expiraEm: string;
}

export function AcoesDeConvite({
  barbeiro,
  aoMudar,
  aoAvisar,
}: {
  barbeiro: BarbeiroAdmin;
  aoMudar: () => void;
  aoAvisar: (texto: string) => void;
}) {
  const envio = useEnvio();
  const [gerado, setGerado] = useState<Gerado | null>(null);
  const estado = barbeiro.acesso ?? "SEM_CONTA";
  const pendente = estado === "CONVITE_PENDENTE";
  // Só quem ainda não tem acesso ativo e não está desligado do cadastro recebe convite.
  if (estado === "ATIVO" || !barbeiro.ativo) return null;

  async function gerar() {
    const criado = await envio.executar(() =>
      chamar(api().POST("/admin/barbeiros/{id}/convite", { params: { path: { id: barbeiro.id } } })),
    );
    if (!criado) return;
    // O código só existe aqui, na memória desta tela: vira o link do diálogo e nunca é gravado em outro lugar.
    // A lista só recarrega quando o diálogo fecha: recarregar agora desmontaria a linha e o link sumiria antes de ser copiado.
    setGerado({ link: `${window.location.origin}/convite#${criado.codigo}`, expiraEm: criado.expiraEm });
  }

  function fechar() {
    setGerado(null);
    aoMudar();
  }

  async function revogar() {
    const feito = await envio.executar(async () => {
      await chamar(api().DELETE("/admin/barbeiros/{id}/convite", { params: { path: { id: barbeiro.id } } }));
      return true;
    });
    if (feito) {
      aoAvisar(`Convite de ${barbeiro.nomeExibicao} revogado.`);
      aoMudar();
    }
  }

  const mensagem = gerado
    ? `Oi, ${barbeiro.nomeExibicao}! Este é o seu convite para entrar na agenda da Altas Horas: ${gerado.link}`
    : "";

  return (
    <>
      <Botao
        aria-label={`${pendente ? "Gerar outro convite" : "Gerar convite"} para ${barbeiro.nomeExibicao}`}
        disabled={envio.enviando}
        onClick={gerar}
      >
        {pendente ? "Gerar outro" : "Gerar convite"}
      </Botao>
      {pendente && (
        <Botao aria-label={`Revogar o convite de ${barbeiro.nomeExibicao}`} disabled={envio.enviando} onClick={revogar}>
          Revogar
        </Botao>
      )}
      {envio.erro && !gerado && <AvisoDeErro erro={envio.erro} focar={false} />}
      {gerado && (
        <Dialogo titulo={`Convite de ${barbeiro.nomeExibicao}`} aoFechar={fechar}>
          <Aviso tipo="aviso">
            Este link aparece só agora e vale até {formatarDataEHora(gerado.expiraEm)}. Entregue em pessoa ou pelo WhatsApp. Quem
            abrir o link digita o próprio e-mail e cria a própria senha; senha nunca vai por mensagem.
          </Aviso>
          <CopiarTexto id={`convite-link-${barbeiro.id}`} rotulo="Link do convite" texto={gerado.link} botao="Copiar link" />
          <QrPix copiaECola={gerado.link} descricao={`QR Code do convite de ${barbeiro.nomeExibicao}: aponte a câmera para abrir o link`} />
          <div className="acoes">
            <a
              className="botao botao-secundario"
              href={`https://wa.me/?text=${encodeURIComponent(mensagem)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              Enviar pelo WhatsApp
            </a>
            <Botao variante="principal" onClick={fechar}>
              Fechar
            </Botao>
          </div>
        </Dialogo>
      )}
    </>
  );
}
