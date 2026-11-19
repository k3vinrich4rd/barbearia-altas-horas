import Link from "next/link";

export default function NaoEncontrada() {
  return (
    <>
      <h1>Página não encontrada</h1>
      <p>Não encontramos o que você procura.</p>
      <Link href="/">Voltar para o início</Link>
    </>
  );
}
