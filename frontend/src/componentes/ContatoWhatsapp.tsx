// "Prefere combinar pelo WhatsApp?" (README do design system, "WhatsApp do barbeiro"). O link é https://wa.me/55 + o número
// PÚBLICO do barbeiro, que vem de GET /barbeiros (contrato 1.6.0, Fatia 6D): só dígitos, com DDI opcional, ou nulo.
// Sem número, o bloco some. Nenhum número escrito no código.
import type { components } from "@/api/schema";
import { Icone } from "./Icone";

export type BarbeiroComWhatsapp = components["schemas"]["BarbeiroPublico"];

export function linkDoWhatsapp(numero: string): string | undefined {
  const digitos = numero.replace(/\D/g, "");
  if (digitos.length < 10 || digitos.length > 13) return undefined;
  return `https://wa.me/${digitos.length <= 11 ? "55" + digitos : digitos}`;
}

export function ContatoWhatsapp({ barbeiro }: { barbeiro?: { nomeExibicao: string; whatsapp?: string | null } }) {
  const link = barbeiro?.whatsapp ? linkDoWhatsapp(barbeiro.whatsapp) : undefined;
  if (!barbeiro || !link) return null;
  const primeiroNome = barbeiro.nomeExibicao.split(" ")[0];
  return (
    <p className="contato-whatsapp">
      Prefere combinar pelo WhatsApp?{" "}
      <a
        href={link}
        rel="noopener noreferrer"
        target="_blank"
        className="link-whatsapp"
        aria-label={`Falar com ${primeiroNome} no WhatsApp (abre o WhatsApp)`}
      >
        <Icone nome="conversa" />
        <span>Falar com {primeiroNome} no WhatsApp</span>
      </a>
    </p>
  );
}
