"use client";
// Cadastros do painel (A49 a A51, V8): serviços, barbeiros com jornada e folgas, e as regras da agenda. Só ADMIN.
import { CadastroDeBarbeiros } from "./CadastroDeBarbeiros";
import { CadastroDeConfiguracao } from "./CadastroDeConfiguracao";
import { CadastroDeServicos } from "./CadastroDeServicos";

export function PainelCadastros() {
  return (
    <>
      <h1>Cadastros</h1>
      <CadastroDeServicos />
      <CadastroDeBarbeiros />
      <CadastroDeConfiguracao />
    </>
  );
}
