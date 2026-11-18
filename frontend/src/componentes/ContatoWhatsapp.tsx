// "Prefere combinar pelo WhatsApp?" (decisão do Kevin, Fatia 6D). O contrato 1.5.0 ainda NÃO tem `whatsapp` no barbeiro:
// o campo é opcional aqui e o componente só aparece quando a API passar a mandá-lo. Nenhuma rota inventada.
export type BarbeiroComWhatsapp = { id: string; nomeExibicao: string; whatsapp?: string | null };

export function linkDoWhatsapp(numero: string): string | undefined {
  const digitos = numero.replace(/\D/g, "");
  if (digitos.length < 10 || digitos.length > 13) return undefined;
  return `https://wa.me/${digitos.length <= 11 ? "55" + digitos : digitos}`;
}

export function ContatoWhatsapp({ barbeiro }: { barbeiro?: { nomeExibicao: string; whatsapp?: string | null } }) {
  const link = barbeiro?.whatsapp ? linkDoWhatsapp(barbeiro.whatsapp) : undefined;
  if (!barbeiro || !link) return null;
  return (
    <p className="contato-whatsapp">
      Prefere combinar pelo WhatsApp?{" "}
      <a href={link} rel="noopener noreferrer" target="_blank">
        Falar com {barbeiro.nomeExibicao}
      </a>
    </p>
  );
}
