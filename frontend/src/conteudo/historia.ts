// Texto da história na home (D-10). Só fatos que já estão no repositório (README, contexto e design system).
// O Kevin revisa o texto. A voz: o Eduardo pelo nome quando é sobre a barbearia; o Kevin em primeira pessoa sobre o projeto.
import { TEXTOS } from "./textos";
export const TITULO_DA_HOME = "Seu horário com o Eduardo, garantido.";

// Subtítulo do herói: texto do PDF (p. 1), sem mudança (Fatia 16, F3; spec 09, TJ-1).
export const SUBTITULO_DA_HOME =
  "Escolha o serviço, veja os horários livres e pague na hora, por Pix ou cartão, com conta ou sem cadastro. Pagou, o horário é seu.";

// "Como marcar" (Fatia 16, F3, A11): os Três jeitos do PDF (p. 1), na ordem do PDF. Substituem a faixa "Como funciona"
// e os fatos do herói. Só "Agendar sem cadastro" é o botão principal.
export type JeitoDeMarcar = {
  numero: string;
  titulo: string;
  texto: string;
  acao: { href: string; texto: string; principal?: boolean };
  /** Com sessão (Fatia 16, decisão 6, A29): a ação troca por esta, no mesmo lugar e com o mesmo estilo. */
  acaoComSessao?: { href: string; texto: string };
};

export const TRES_JEITOS: readonly JeitoDeMarcar[] = [
  {
    numero: "1 · Com conta",
    titulo: "Criar conta e agendar",
    texto: "Pague no app e acompanhe seus horários em Meus agendamentos.",
    acao: { href: "/entrar", texto: "Criar conta ou entrar" },
    acaoComSessao: { href: "/meus-agendamentos", texto: "Meus agendamentos" },
  },
  {
    numero: "2 · Sem cadastro",
    titulo: "Agendar sem cadastro",
    texto: "Só nome, e-mail e WhatsApp. Pague no app e receba o link da reserva.",
    acao: { href: "/agendar", texto: "Agendar sem cadastro", principal: true },
  },
  {
    numero: "3 · Pelo WhatsApp",
    titulo: "Combinar com o Eduardo",
    texto: "Veja os horários livres agora e peça o seu na conversa. O pagamento é direto com ele.",
    acao: { href: "/agendar", texto: "Ver horários livres" },
  },
];

// Trajetória (Fatia 14B, seção 6.2 da spec): só os fatos da tabela 6.1, cada um com fonte no repositório (A39).
// Fatos novos (primeiro mestre, ano da Academy, alunos, corte preferido) são melhoria futura (seção 10.1).
export const HISTORIA: readonly string[] = [
  "Em 1994, o Carlos Eduardo Candido, o Eduardo, começou no ofício aprendendo com o primeiro mestre. Três anos depois, em 1997, abriu a Barbearia Altas Horas no Jardim Bonança, em Osasco. E de lá não saiu mais: são quase 30 anos na mesma cadeira.",
  'O trabalho fala por ele. Em 2017, esse trabalho foi reconhecido duas vezes: com o Prêmio Barbeiros do Brasil, que ele fez questão de trazer para Osasco, e com o Prêmio Master Barber · Êxito Profissional. Ao receber o primeiro, resumiu tudo numa frase só: "Amor na ponta da tesoura!"',
  "Instrutor formado pela Ibel Formação, hoje ele também forma novos barbeiros na Altas Horas Barbearia Academy. Quase 30 anos depois, a cadeira e a tesoura são as mesmas. O que mudou foi o caminho até elas. O filho, Kevin, criou esta agenda para que cada cliente marque do seu jeito. Você pode criar o seu cadastro e marcar por ele. Se estiver na correria do dia a dia, pode só escolher o horário e pagar no Pix, sem cadastro nenhum. E, se preferir como antigamente, é só chamar o Eduardo no WhatsApp, que ele mesmo marca o seu horário.",
];

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

// "Antes de pagar", dentro de "Como marcar" (Fatia 16, F3, A11). Saíram "Preciso criar uma conta?" e "Como eu pago?",
// que os Três jeitos e o subtítulo já respondem. Ficam as 3 que só aparecem aqui: prazo, cancelamento e antecedência.
export const DUVIDAS: readonly { pergunta: string; resposta: string }[] = [
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
// do site e da versão atual do repositório. A foto do salão, recortada sem as TVs e sem reflexo de pessoa, fica fora da
// galeria (cenário A da Fatia 16).
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

/** A única foto aprovada sem pessoa (o salão). No cenário A ela sai da galeria; volta na home em "Onde estamos" (F6). */
export const FOTO_DO_SALAO: FotoDaGaleria = {
  src: "/assets/galeria/salao-vitrine-sem-tv.jpg",
  legenda: "O salão",
  alt: "Porta de vidro da barbearia com dois adesivos redondos do logo Altas Horas 1997 e, atrás do vidro, a cadeira preta, a bancada de ferramentas e um troféu na prateleira",
  largura: 1100,
  altura: 619,
  semPessoas: true,
};

/** Fotos de corte aprovadas para a galeria (critério: nenhum rosto, nenhuma criança, nenhuma TV com gente). Cenário A
 * (Fatia 16, decisão 5, A17): nenhuma ainda, então a seção e o link "Galeria" não existem. */
export const GALERIA: readonly FotoDaGaleria[] = [];

export const IMAGENS = {
  logo: {
    src: "/assets/logo-altas-horas.png",
    alt: "Logo da Barbearia Altas Horas: círculo preto com BARBEARIA em arco, monograma AA serifado com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★",
  },
  retrato: {
    src: "/assets/eduardo-candido-avatar.png",
    alt: "Eduardo Candido, barbeiro e professor da Altas Horas, de camisa branca e gravata",
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
