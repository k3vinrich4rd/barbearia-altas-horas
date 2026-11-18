"use client";
import { useSearchParams } from "next/navigation";
import { Aviso } from "./Aviso";

const AVISOS: Record<string, string> = { "conta-excluida": "Conta excluída. Seus dados pessoais foram apagados." };

export function AvisoDaHome() {
  const texto = AVISOS[useSearchParams().get("aviso") ?? ""];
  return texto ? <Aviso tipo="sucesso">{texto}</Aviso> : null;
}
