// Navegação "dura" do navegador (troca de página inteira). Fica num módulo só para os testes poderem trocá-la.
export function irPara(destino: string): void {
  if (typeof window !== "undefined") window.location.assign(destino);
}
