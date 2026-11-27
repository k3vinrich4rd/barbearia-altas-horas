// Textos de tela com a voz da barbearia (Fatia 14B, seção 5 da spec, regra E21). Os textos de erro por `code` ficam no
// catálogo `api/mensagens.ts`; aqui ficam os de tela, num lugar só, para o A37 congelar a versão aprovada palavra por palavra.
// Regras: calorosa e direta, sem gíria forçada; sempre o próximo passo; não culpa quem usa; nenhum número de regra
// configurável fixo (o número vem da API ou some); sem emoji e sem exclamação em erro.

export const TEXTOS = {
  /** 1 · toda consulta (com o skeleton no lugar, M4). */
  carregando: "Só um instante, já estamos trazendo.",
  /** 6 · dia sem horário em /agendar. */
  diaCheio: "Esse dia já está cheio. Veja o próximo dia com horário.",
  /** C8 (Fatia 15, E18): dia escolhido com o expediente fechado (fora da jornada, ou folga ou férias no dia todo). Sem motivo. */
  diaFechado: "Fechado nesse dia. Veja o próximo dia com horário.",
  /** 8 · nota de /agendar (D197). */
  notaAgendar: "Só aparecem os horários livres. Pode marcar para hoje ou para qualquer data futura.",
  /** 15 · esperando o Pix. */
  esperandoPagamento: "Assim que o pagamento cair, a gente confirma aqui. Pode deixar esta tela aberta.",
  /** 16 · último minuto sem a prorrogação disponível. */
  ultimoMinuto: "Menos de 1 minuto para pagar.",
  /** 16 · último minuto com o botão; o rótulo vem do número que a API manda (prorrogacao.segundos). */
  ultimoMinutoComBotao: (rotuloDoBotao: string) => `Menos de 1 minuto. Se precisar, toque em ${rotuloDoBotao}.`,
  /** C1 (Fatia 15): abaixo do "Acrescentar 2 minutos", só a partir do 1º pedido; o número vem de `prorrogacao.restantes`. */
  pedidosRestantes: (n: number) => (n === 1 ? "Resta 1 pedido." : `Restam ${n} pedidos.`),
  /** 17 · contador zerado. */
  prazoZerado: "Conferindo se o pagamento chegou…",
  /** 18 · o tempo para pagar acabou (Pagamento, RESERVA_EXPIRADA). */
  tempoAcabou:
    "O tempo para pagar acabou e o horário voltou para a agenda. Nada foi cobrado. Escolha um horário de novo.",
  /** 19 · a rede caiu no meio da reserva. */
  redeCaiuNaReserva:
    "A conexão caiu no meio do caminho e não sabemos se a reserva entrou. Confira em Meus agendamentos antes de tentar de novo.",
  /** Confirmação: único ponto de exclamação do site (é sucesso, não erro). */
  confirmado: "Agendado! Seu horário está garantido.", // D207: "Fechado" agora é só dia ou faixa sem atendimento
  /** Painel: dia sem cliente. */
  painelSemClientes: "Nenhum cliente marcado neste dia ainda.",
  /** Dúvida da home "Com quanta antecedência posso agendar?" (D197). */
  duvidaAntecedencia:
    "Quando quiser: para hoje mesmo, desde que o horário ainda não tenha passado, ou para qualquer data futura. Pagou, o horário é seu.",
  /** Busca do próximo dia com horário sem achar nada (o 30 é o tamanho da busca, não regra). */
  buscaSemHorario: "Não achamos horário livre nos próximos 30 dias. Procure mais 30 dias ou escolha em Outro dia.",
  /** Terceiro jeito de marcar: rótulo do grupo no PDF p.6. */
  pedidoWhatsappRotulo: "Pelo WhatsApp",
  /** Aviso antes do link. O nome é o primeiro nome do barbeiro, vindo da API. */
  pedidoWhatsappAviso: (nome: string) =>
    `O horário só fica seu quando o ${nome} confirmar. Até lá, outra pessoa pode marcar pelo site.`,
  pedidoWhatsappBotao: "Pedir este horário pelo WhatsApp",
  /** Proposta, para o Kevin revisar no PR. O nome vem da API. */
  pedidoWhatsappNota: (nome: string) => `Mandou a mensagem? Agora é com o ${nome}: ele confirma na conversa.`,
  pedidoWhatsappOutroHorario: "Escolher outro horário",
  /**
   * Mensagem pronta do WhatsApp (proposta, para o Kevin revisar no PR).
   * Não leva link do painel, id de reserva nem dado pessoal.
   */
  pedidoWhatsappMensagem: (
    primeiroNome: string,
    servico: string,
    preposicao: "na" | "no",
    dia: string,
    data: string,
    hora: string,
  ) =>
    `Olá, ${primeiroNome}! Quero marcar ${servico} ${preposicao} ${dia}, ${data}, às ${hora}. Esse horário está livre?`,
} as const;
