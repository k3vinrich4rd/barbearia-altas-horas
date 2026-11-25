// "Adicionar à minha agenda" (.ics) e "Mandar no WhatsApp" da confirmação (Fatia 14D, M6 e M7). Tudo gerado no navegador,
// sem serviço pago. Só serviço, dia, hora e endereço: nunca nome, e-mail, WhatsApp nem o código do visitante (LGPD).

export const ENDERECO = "R. São Judas Tadeu, 313 · Jd. Bonança · Osasco/SP";

interface Horario {
  inicio: string;
  fim: string;
  servico?: { nome?: string } | null;
}

/** "2026-12-01T09:00:00-03:00" → "20261201T120000Z". */
const emUtc = (iso: string) =>
  new Date(iso)
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}/, "");

/** Texto do iCalendar (RFC 5545), com as quebras CRLF que a norma pede. */
export function gerarIcs(r: Horario, agora = new Date()): string {
  const escapar = (t: string) => t.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,");
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Barbearia Altas Horas//Agenda//PT-BR",
    "BEGIN:VEVENT",
    `UID:${crypto.randomUUID()}@barbearia-altas-horas`,
    `DTSTAMP:${emUtc(agora.toISOString())}`,
    `DTSTART:${emUtc(r.inicio)}`,
    `DTEND:${emUtc(r.fim)}`,
    `SUMMARY:${escapar(`${r.servico?.nome ?? "Horário"} · Barbearia Altas Horas`)}`,
    `LOCATION:${escapar(ENDERECO)}`,
    "END:VEVENT",
    "END:VCALENDAR",
    "",
  ].join("\r\n");
}

/** Baixa o .ics (um link temporário com o arquivo gerado aqui mesmo). */
export function baixarIcs(r: Horario) {
  const url = URL.createObjectURL(new Blob([gerarIcs(r)], { type: "text/calendar;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = "horario-barbearia-altas-horas.ics";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Link do WhatsApp sem destinatário: a pessoa escolhe para quem mandar. */
export function linkDoWhatsapp(r: Horario, quando: string): string {
  const texto = `Marquei ${r.servico?.nome ?? "um horário"} na Barbearia Altas Horas: ${quando}. Endereço: ${ENDERECO}.`;
  return `https://wa.me/?text=${encodeURIComponent(texto)}`;
}
