// Texto da história na home (D-10). Só fatos que já estão no repositório (README, contexto e design system).
// O Kevin revisa o texto. A voz: o Eduardo pelo nome quando é sobre a barbearia; o Kevin em primeira pessoa sobre o projeto.
import { TEXTOS } from "./textos";
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

// Trajetória (Fatia 14B, seção 6.2 da spec): só os fatos da tabela 6.1, cada um com fonte no repositório (A39).
// Fatos novos (primeiro mestre, ano da Academy, alunos, corte preferido) são melhoria futura (seção 10.1).
export const HISTORIA: readonly string[] = [
  "Em 1994, o Carlos Eduardo Candido, o Eduardo, começou no ofício aprendendo com o primeiro mestre. Três anos depois, em 1997, abriu a Barbearia Altas Horas no Jardim Bonança, em Osasco. E de lá não saiu mais: são quase 30 anos na mesma cadeira.",
  'O trabalho fala por ele. Em 2017, esse trabalho foi reconhecido duas vezes: com o Prêmio Barbeiros do Brasil, que ele fez questão de trazer para Osasco, e com o Prêmio Master Barber · Êxito Profissional. Ao receber o primeiro, resumiu tudo numa frase só: "Amor na ponta da tesoura!"',
  "Instrutor formado pela Ibel Formação, hoje ele também forma novos barbeiros na Altas Horas Barbearia Academy. Quase 30 anos depois, a cadeira e a tesoura são as mesmas. O que mudou foi o caminho até elas. O filho, Kevin, criou esta agenda para que cada cliente marque do seu jeito. Você pode criar o seu cadastro e marcar por ele. Se estiver na correria do dia a dia, pode só escolher o horário e pagar no Pix, sem cadastro nenhum. E, se preferir como antigamente, é só chamar o Eduardo no WhatsApp, que ele mesmo marca o seu horário.",
];

/** Texto do barbeiro na home (BarbeiroDaHome, seção 6.2). */
export const TEXTO_DO_BARBEIRO =
  "Barbeiro desde 1994 e dono da Altas Horas desde 1997. Professor na Academy e duas vezes premiado em 2017.";

export const MARCOS: readonly { ano: string; titulo: string; texto: string }[] = [
  {
    ano: "1994",
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
      "Barbeiro na cadeira e professor na Altas Horas Barbearia Academy. A agenda sai do caderno e vem para a internet.",
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
    resposta: TEXTOS.duvidaAntecedencia,
  },
];

// Galeria da home: só fotos sem rosto identificável e sem criança (privacidade, 27/11/2026). As fotos de clientes saíram
// do site e da versão atual do repositório. Hoje fica só o salão, recortado sem as TVs e sem reflexo de pessoa.
// Largura e altura são as reais do arquivo (o next/image reserva o espaço, sem salto de layout).
export type FotoDaGaleria = {
  src: string;
  legenda: string;
  alt: string;
  largura: number;
  altura: number;
  /** Foto sem pessoa (salão, ferramentas): a miniatura pode preencher o quadro (cover), como pede o design system. */
  semPessoas?: boolean;
};

/** As únicas fotos aprovadas para a galeria (critério: nenhum rosto, nenhuma criança, nenhuma TV com gente). */
export const GALERIA: readonly FotoDaGaleria[] = [
  {
    src: "/assets/galeria/salao-vitrine-sem-tv.jpg",
    legenda: "O salão",
    alt: "Porta de vidro da barbearia com dois adesivos redondos do logo Altas Horas 1997 e, atrás do vidro, a cadeira preta, a bancada de ferramentas e um troféu na prateleira",
    largura: 1100,
    altura: 619,
    semPessoas: true,
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
    src: "/assets/fachada-sem-tv.jpg",
    alt: "Porta de vidro da Barbearia Altas Horas com dois adesivos redondos do logo e, atrás do vidro, a cadeira preta, a bancada e um troféu na prateleira",
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
