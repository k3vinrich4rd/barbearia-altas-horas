// Tipo de página (Fatia 16, F2): nas telas de tarefa (agendar, pagar, entrar, criar conta) o topo fica só com a marca e o
// Menu, e o rodapé fica com uma linha. Assim o cliente não vê "Agendar horário" enquanto já está agendando (A15).
const TAREFA = [/^\/agendar(\/|$)/, /^\/entrar(\/|$)/, /^\/cadastro(\/|$)/, /^\/pagamento-simulado(\/|$)/];

export const ehPaginaDeTarefa = (caminho: string) => TAREFA.some((r) => r.test(caminho));
