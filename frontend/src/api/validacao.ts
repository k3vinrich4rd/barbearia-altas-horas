// Validações do navegador (a API continua sendo quem decide; aqui é só para avisar antes de enviar).
export const apenasDigitos = (texto: string) => texto.replace(/\D/g, "");

/** CPF com dígitos verificadores. Não guarda nem registra o número. */
export function cpfValido(texto: string): boolean {
  const d = apenasDigitos(texto);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const digito = (base: string, pesoInicial: number) => {
    let soma = 0;
    for (let i = 0; i < base.length; i++) soma += Number(base[i]) * (pesoInicial - i);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  return digito(d.slice(0, 9), 10) === Number(d[9]) && digito(d.slice(0, 10), 11) === Number(d[10]);
}

/** Senha de 10 a 72 BYTES em UTF-8 (limite do bcrypt, ADR do contrato). */
export function bytesDaSenha(senha: string): number {
  return new TextEncoder().encode(senha).length;
}

export function senhaValida(senha: string): boolean {
  const n = bytesDaSenha(senha);
  return n >= 10 && n <= 72;
}

export const MENSAGEM_SENHA = "A senha precisa ter de 10 a 72 caracteres (acentos e símbolos contam mais).";

export function cepComOitoDigitos(texto: string): boolean {
  return apenasDigitos(texto).length === 8 && /^\d{5}-?\d{3}$/.test(texto.trim());
}
