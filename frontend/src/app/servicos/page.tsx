import { redirect } from "next/navigation";

// Fatia 16 (F5, A27): a lista completa de serviços e preços é a seção "02 · O que fazemos" da home. Links antigos e
// favoritos para /servicos continuam valendo e caem nela.
export default function Servicos() {
  redirect("/#servicos");
}
