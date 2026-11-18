// Texto da história na home (D-10). Só fatos que já estão no repositório (README, contexto e design system).
// O Kevin revisa o texto. A voz: o Eduardo pelo nome quando é sobre a barbearia; o Kevin em primeira pessoa sobre o projeto.
export const TITULO_DA_HOME = "Desde 1997 no Jardim Bonança";

export const SUBTITULO_DA_HOME = "Horário garantido, pago antes. Cadeira sem falta.";

export const HISTORIA: readonly string[] = [
  "A Barbearia Altas Horas atende no Jardim Bonança, em Osasco, desde 1997. É do Eduardo Cândido, barbeiro e professor, que já levou dois prêmios em 2017: o Barbeiros do Brasil e o Master Barber.",
  "A casa abre de terça a sábado, das 7h às 20h, com pausa para o almoço das 12h às 13h. Os horários são de 30 em 30 minutos.",
  "Como desenvolvedor, criei esta agenda para o meu pai: cadeira vazia é tempo perdido. Você escolhe o serviço e o horário e paga na hora, por Pix ou cartão. O horário fica guardado por poucos minutos enquanto você paga.",
];

export const IMAGENS = {
  logo: {
    src: "/assets/logo-altas-horas.png",
    alt: "Logo da Barbearia Altas Horas: círculo preto com BARBEARIA em arco, monograma AA serifado com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★",
  },
  fachada: {
    src: "/assets/fachada.jpg",
    alt: "Fachada de vidro da Barbearia Altas Horas com dois adesivos redondos do logo e, ao fundo, as cadeiras, o balcão e os troféus",
  },
  premios: [
    {
      src: "/assets/premios/premio-barbeiros-do-brasil-2017.jpg",
      alt: "Eduardo Cândido, de terno escuro, segurando o troféu hexagonal do Prêmio Barbeiros do Brasil, com uma barra de barbeiro no centro",
    },
    {
      src: "/assets/premios/premio-master-barber-2017.jpg",
      alt: "Eduardo Cândido, de terno preto e gravata, sorrindo e segurando o troféu dourado do Prêmio Master Barber",
    },
  ],
} as const;
