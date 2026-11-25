"use client";
// Campo de senha com "Mostrar a senha" (Fatia 14B, E20, A28 a A33). Um padrão só no site todo, extraído do que o
// Convite já fazia. AGUARDA DESENHO do ícone de olho (D131): até lá, o botão é só texto, com o Botao que já existe.
// - o botão é type="button" (não envia o formulário), com aria-pressed e aria-controls apontando o campo;
// - o rótulo muda junto com o estado; o foco fica no botão e o valor no campo;
// - o autoComplete não muda com o estado (o gerenciador de senhas continua reconhecendo o campo);
// - ao enviar o formulário, o campo volta a escondido ANTES do envio (a senha não fica exposta se a tela mostrar erro).
import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ComponentProps } from "react";
import { flushSync } from "react-dom";
import { Botao } from "./Botao";
import { Campo } from "./Campo";

type Props = Omit<ComponentProps<typeof Campo>, "type"> & {
  autoComplete: "current-password" | "new-password";
};

export const CampoSenha = forwardRef<HTMLInputElement, Props>(function CampoSenha({ id, ...resto }, ref) {
  const [visivel, setVisivel] = useState(false);
  const campo = useRef<HTMLInputElement>(null);
  useImperativeHandle(ref, () => campo.current as HTMLInputElement);

  useEffect(() => {
    const formulario = campo.current?.form;
    if (!formulario) return;
    // Captura no próprio <form>: roda antes do onSubmit do React (que escuta na raiz).
    const esconder = () => {
      if (campo.current) campo.current.type = "password";
      flushSync(() => setVisivel(false));
    };
    formulario.addEventListener("submit", esconder, true);
    return () => formulario.removeEventListener("submit", esconder, true);
  }, []);

  return (
    <div className="campo-senha">
      <Campo ref={campo} id={id} type={visivel ? "text" : "password"} {...resto} />
      <Botao aria-pressed={visivel} aria-controls={id} onClick={() => setVisivel((v) => !v)}>
        {visivel ? "Esconder a senha" : "Mostrar a senha"}
      </Botao>
    </div>
  );
});
