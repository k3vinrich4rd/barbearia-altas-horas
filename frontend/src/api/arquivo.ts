// Entrega um texto como arquivo para baixar (E18). O conteúdo não fica guardado depois do download.
export function baixarArquivo(nome: string, texto: string, tipo = "application/json"): void {
  const url = URL.createObjectURL(new Blob([texto], { type: tipo }));
  const a = document.createElement("a");
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
