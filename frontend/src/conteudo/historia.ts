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

// Faixa "Como funciona" do herói (Fatia 12, D-11). Nenhum número de minutos: o prazo vem só do `expiraEm` (A19).
export const PASSOS_DA_FAIXA: readonly { titulo: string; texto: string }[] = [
  { titulo: "1 · Escolha", texto: "o serviço e um horário livre" },
  { titulo: "2 · Pague", texto: "por Pix ou cartão, num prazo curto" },
  { titulo: "3 · Confirmado", texto: "o horário fica garantido na agenda" },
];

export const HISTORIA: readonly string[] = [
  "O Eduardo Candido começou no ofício em 1995, com o primeiro mestre. Em 1997 abriu a Barbearia Altas Horas no Jardim Bonança, em Osasco, e desde então atende de terça a sábado, do corte social clássico ao degradê navalhado.",
  "Na prateleira do salão, os troféus dividem espaço com fotos antigas. Para o Eduardo, cada um marca uma etapa do caminho, e cada dia na cadeira carrega um pouco da dedicação de quase 30 anos.",
  "Instrutor formado pela Ibel Formação, hoje ele também ensina na Altas Horas Barbearia Academy. Com o dia dividido entre clientes e alunos, a agenda saiu do caderno e foi para a internet.",
];

export const MARCOS: readonly { ano: string; titulo: string; texto: string }[] = [
  {
    ano: "1995",
    titulo: "O primeiro mestre",
    texto: "O Eduardo começa no ofício, aprendendo a cortar com o primeiro mestre.",
  },
  {
    ano: "1997",
    titulo: "Abre a Altas Horas",
    texto: "Abre a Barbearia Altas Horas no Jardim Bonança, em Osasco. É ali que está até hoje.",
  },
  {
    ano: "2017",
    titulo: "Dois prêmios",
    texto:
      "Recebe o Prêmio Barbeiros do Brasil e o Prêmio Master Barber · Êxito Profissional, e traz os dois troféus para Osasco.",
  },
  {
    ano: "Hoje",
    titulo: "Cadeira e Academy",
    texto:
      "Barbeiro na cadeira e professor na Altas Horas Barbearia Academy. A agenda sai do caderno e vai para a internet.",
  },
];

export const DUVIDAS: readonly { pergunta: string; resposta: string }[] = [
  {
    pergunta: "Preciso criar uma conta?",
    resposta:
      "Não. Você pode agendar só com seus dados de contato. Com conta, acompanha seus agendamentos num lugar só.",
  },
  {
    pergunta: "Como eu pago?",
    resposta: "Por Pix ou cartão, no momento da reserva. O horário só é confirmado depois que o pagamento é aprovado.",
  },
  {
    pergunta: "E se eu não pagar no prazo?",
    resposta:
      "Na tela de pagamento, um contador mostra quanto tempo falta. Se o tempo acabar, a reserva expira e o horário volta para a agenda, livre para outro cliente. É só escolher de novo.",
  },
  {
    pergunta: "Posso cancelar ou remarcar?",
    resposta:
      "Não. Antes de pagar, você confirma que não há cancelamento, remarcação nem devolução por desistência ou falta. Se a barbearia precisar cancelar, o valor é devolvido.",
  },
  {
    pergunta: "Com quanta antecedência posso agendar?",
    resposta: "Com pelo menos 2 horas de antecedência e até 30 dias à frente.",
  },
];

// Galeria da home (Fatia 12, E2 a E4): 3 fotos que já estavam em docs/assets/galeria e 15 novas dos Instagrams do Eduardo
// e da barbearia, com a autorização de imagem confirmada pelo Kevin (D155). Largura e altura são as reais do arquivo
// (o next/image reserva o espaço, sem salto de layout). Sem nome de cliente em legenda ou texto alternativo.
export const GALERIA: readonly { src: string; legenda: string; alt: string; largura: number; altura: number }[] = [
  {
    src: "/assets/galeria/eduardo-em-atendimento.jpg",
    legenda: "O Eduardo em atendimento",
    alt: "Eduardo Candido, de camisa listrada e gravata borboleta, finalizando um corte com topo reto e degradê, com as prateleiras pretas do salão ao fundo",
    largura: 480,
    altura: 480,
  },
  {
    src: "/assets/galeria/corte-topete-parede-do-logo.jpg",
    legenda: "A parede xadrez do logo",
    alt: "Cliente de perfil com topete alto e degradê, em frente à parede xadrez preta e branca com o logo da barbearia",
    largura: 480,
    altura: 480,
  },
  {
    src: "/assets/galeria/corte-degrade-com-desenho.jpg",
    legenda: "Degradê com desenho na navalha",
    alt: "Cliente de costas com cabelo crespo em degradê, pontas descoloridas e um desenho feito na navalha na nuca",
    largura: 480,
    altura: 480,
  },
  {
    src: "/assets/galeria/corte-social-grisalho-na-regua.jpg",
    legenda: "Social na régua",
    alt: "Eduardo, de camiseta preta e óculos, ajeitando com os dedos o cabelo grisalho de um cliente sentado, de capa preta com o logo da barbearia, no salão com espelho, bancada de ferramentas e uma TV ao fundo",
    largura: 608,
    altura: 1080,
  },
  {
    src: "/assets/galeria/corte-infantil-cacheado-com-degrade.jpg",
    legenda: "Corte infantil",
    alt: "Menino de cabelo cacheado volumoso no topo e degradê nas laterais, de capa preta, com o Eduardo sorrindo atrás dele, de tesoura na mão, e o logo da barbearia na parede",
    largura: 608,
    altura: 1080,
  },
  {
    src: "/assets/galeria/corte-franja-curta-jovem.jpg",
    legenda: "Franja curta e laterais baixas",
    alt: "Rapaz de cabelo liso preto com franja curta e laterais baixas, de capa preta, com o Eduardo penteando atrás dele e o logo redondo da barbearia na parede",
    largura: 608,
    altura: 1080,
  },
  {
    src: "/assets/galeria/platinado-cliente-de-oculos.jpg",
    legenda: "Platinado",
    alt: "Cliente de cabelo curto platinado e óculos de armação fina, de capa verde, olhando de lado, com o Eduardo sorrindo atrás e tocando o cabelo",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/corte-infantil-risca-lateral.jpg",
    legenda: "Corte infantil com risca",
    alt: "Menino de cabelo liso penteado com risca lateral marcada e laterais baixas, de capa preta, com o Eduardo sorrindo atrás dele e o logo na parede",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/corte-cacheado-volume.jpg",
    legenda: "Volume e definição",
    alt: "Cliente de cabelo cacheado volumoso e curto, sorrindo, de capa cinza, com o Eduardo atrás dele segurando a tesoura e sorrindo",
    largura: 640,
    altura: 1136,
  },
  {
    src: "/assets/galeria/platinado-cliente-e-eduardo.jpg",
    legenda: "Descoloração e platinado",
    alt: "Cliente de cabelo curto platinado e óculos, sorrindo, com o Eduardo sorrindo atrás, de camiseta com o logo da barbearia",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/corte-topo-volumoso-com-degrade.jpg",
    legenda: "Topo volumoso e degradê",
    alt: "Rapaz de cabelo crespo com topo alto e volumoso e laterais em degradê, de capa preta, com o Eduardo atrás dele, de tesoura na mão, e o logo na parede",
    largura: 640,
    altura: 1136,
  },
  {
    src: "/assets/galeria/fade-perfil-capa-altas-horas.jpg",
    legenda: "Fade de perfil",
    alt: "Cliente de perfil com cabelo crespo e degradê baixo, de capa preta com o nome Barbearia estampado, no salão com o logo na parede ao fundo",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/barba-cheia-desenhada.jpg",
    legenda: "Barba desenhada",
    alt: "Cliente de frente, de cabelo escuro penteado para o lado e barba cheia bem desenhada, de capa preta, com o logo da barbearia ao fundo",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/degrade-parede-do-logo.jpg",
    legenda: "Degradê e logo",
    alt: "Cliente de perfil com cabelo liso penteado para trás em topete e degradê baixo na nuca, sob o logo redondo da barbearia na parede",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/degrade-baixo-alinhamento-na-cadeira.jpg",
    legenda: "Alinhamento",
    alt: "Cliente de cabelo crespo curto com degradê baixo, de capa cinza, com o Eduardo atrás dele passando a mão sobre o topo para conferir o alinhamento",
    largura: 640,
    altura: 1136,
  },
  {
    src: "/assets/galeria/franja-reta-alinhada.jpg",
    legenda: "Franja alinhada",
    alt: "Rapaz de franja reta e comprida, com as laterais baixas, olhando para a câmera, com o logo redondo da barbearia na parede ao fundo",
    largura: 480,
    altura: 854,
  },
  {
    src: "/assets/galeria/salao-vitrine-com-logo.jpg",
    legenda: "O salão",
    alt: "Vitrine de vidro da barbearia com dois adesivos redondos do logo Altas Horas 1997 e, ao fundo, as cadeiras, a bancada, uma TV e prateleiras de produtos",
    largura: 1440,
    altura: 1440,
  },
  {
    src: "/assets/galeria/degrade-grisalho-penteado-para-tras.jpg",
    legenda: "Degradê grisalho",
    alt: "Cliente grisalho de perfil, com o cabelo penteado para trás em topete e degradê nas laterais, de camiseta preta",
    largura: 1080,
    altura: 1080,
  },
];

export const IMAGENS = {
  logo: {
    src: "/assets/logo-altas-horas.png",
    alt: "Logo da Barbearia Altas Horas: círculo preto com BARBEARIA em arco, monograma AA serifado com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★",
  },
  retrato: {
    src: "/assets/eduardo-candido-avatar.png",
    alt: "Eduardo Candido, barbeiro e professor da Altas Horas, de camisa branca e gravata",
  },
  barbeiro: {
    src: "/assets/eduardo-candido.jpg",
    alt: "Eduardo Candido, de camisa branca e gravata preta, olhando para a câmera",
    largura: 480,
    altura: 480,
  },
  fachada: {
    src: "/assets/fachada.jpg",
    alt: "Fachada de vidro da Barbearia Altas Horas com dois adesivos redondos do logo e, ao fundo, as cadeiras, o balcão e os troféus",
  },
  premios: [
    {
      src: "/assets/premios/premio-barbeiros-do-brasil-2017.jpg",
      alt: "Eduardo Candido, de terno escuro, segurando o troféu hexagonal do Prêmio Barbeiros do Brasil, com uma barra de barbeiro no centro",
    },
    {
      src: "/assets/premios/premio-master-barber-2017.jpg",
      alt: "Eduardo Candido, de terno preto e gravata, sorrindo e segurando o troféu dourado do Prêmio Master Barber",
    },
  ],
} as const;
