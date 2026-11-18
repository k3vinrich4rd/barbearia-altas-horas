// Texto da história na home (D-10). Só fatos que já estão no repositório (README, contexto e design system).
// O Kevin revisa o texto. A voz: o Eduardo pelo nome quando é sobre a barbearia; o Kevin em primeira pessoa sobre o projeto.
export const TITULO_DA_HOME = "Seu horário com o Eduardo, garantido.";

export const SUBTITULO_DA_HOME =
  "Escolha o serviço, veja os horários livres e pague na hora, por Pix ou cartão. Pagou, o horário é seu: sem esperar resposta no WhatsApp.";

export const FATOS_DA_HOME: readonly { titulo: string; texto: string }[] = [
  { titulo: "Terça a sábado", texto: "das 7h às 20h" },
  { titulo: "Pix ou cartão", texto: "pago na hora, horário garantido" },
  { titulo: "2 prêmios", texto: "nacionais em 2017" },
];

export const HISTORIA: readonly string[] = [
  "O Eduardo Cândido começou no ofício em 1995, com o primeiro mestre. Em 1997 abriu a Barbearia Altas Horas no Jardim Bonança, em Osasco, e desde então atende de terça a sábado, do corte social clássico ao degradê navalhado.",
  "Na prateleira do salão, os troféus dividem espaço com fotos antigas. Para o Eduardo, cada um marca uma etapa do caminho, e cada dia na cadeira carrega um pouco da dedicação de quase 30 anos.",
  "Instrutor formado pela Ibel Formação, hoje ele também ensina na Altas Horas Barbearia Academy. Com o dia dividido entre clientes e alunos, a agenda saiu do caderno e foi para a internet.",
];

export const MARCOS: readonly { ano: string; titulo: string; texto: string }[] = [
  { ano: "1995", titulo: "O primeiro mestre", texto: "O Eduardo começa no ofício, aprendendo a cortar com o primeiro mestre." },
  { ano: "1997", titulo: "Abre a Altas Horas", texto: "Abre a Barbearia Altas Horas no Jardim Bonança, em Osasco. É ali que está até hoje." },
  { ano: "2017", titulo: "Dois prêmios", texto: "Recebe o Prêmio Barbeiros do Brasil e o Prêmio Master Barber · Êxito Profissional, e traz os dois troféus para Osasco." },
  { ano: "Hoje", titulo: "Cadeira e Academy", texto: "Barbeiro na cadeira e professor na Altas Horas Barbearia Academy. A agenda sai do caderno e vai para a internet." },
];

export const DUVIDAS: readonly { pergunta: string; resposta: string }[] = [
  { pergunta: "Preciso criar uma conta?", resposta: "Não. Você pode agendar só com seus dados de contato. Com conta, acompanha seus agendamentos num lugar só." },
  { pergunta: "Como eu pago?", resposta: "Por Pix ou cartão, no momento da reserva. O horário só é confirmado depois que o pagamento é aprovado." },
  { pergunta: "E se eu não pagar no prazo?", resposta: "Na tela de pagamento, um contador mostra quanto tempo falta. Se o tempo acabar, a reserva expira e o horário volta para a agenda, livre para outro cliente. É só escolher de novo." },
  { pergunta: "Posso cancelar ou remarcar?", resposta: "Não. Antes de pagar, você confirma que não há cancelamento, remarcação nem devolução por desistência ou falta. Se a barbearia precisar cancelar, o valor é devolvido." },
  { pergunta: "Com quanta antecedência posso agendar?", resposta: "Com pelo menos 2 horas de antecedência e até 30 dias à frente." },
];

export const GALERIA: readonly { src: string; legenda: string; alt: string }[] = [
  { src: "/assets/galeria/eduardo-em-atendimento.jpg", legenda: "O Eduardo em atendimento", alt: "Eduardo Cândido, de camisa listrada e gravata borboleta, finalizando um corte com topo reto e degradê, com as prateleiras pretas do salão ao fundo" },
  { src: "/assets/galeria/corte-topete-parede-do-logo.jpg", legenda: "A parede xadrez do logo", alt: "Cliente de perfil com topete alto e degradê, em frente à parede xadrez preta e branca com o logo da barbearia" },
  { src: "/assets/galeria/corte-degrade-com-desenho.jpg", legenda: "Degradê com desenho na navalha", alt: "Cliente de costas com cabelo crespo em degradê, pontas descoloridas e um desenho feito na navalha na nuca" },
  { src: "/assets/fachada.jpg", legenda: "A fachada da barbearia", alt: "Fachada de vidro da Barbearia Altas Horas com dois adesivos redondos do logo e, ao fundo, as cadeiras, o balcão e os troféus" },
];

export const IMAGENS = {
  logo: {
    src: "/assets/logo-altas-horas.png",
    alt: "Logo da Barbearia Altas Horas: círculo preto com BARBEARIA em arco, monograma AA serifado com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★",
  },
  retrato: {
    src: "/assets/eduardo-candido-avatar.png",
    alt: "Eduardo Cândido, barbeiro e professor da Altas Horas, de camisa branca e gravata",
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
