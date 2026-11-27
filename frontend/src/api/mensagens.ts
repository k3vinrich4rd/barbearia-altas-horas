// Catálogo de mensagens por `code` (Anexo A da spec, D-4). Um único arquivo; a tela busca por `code`.
// Português do Brasil, sem dado pessoal e sem culpar quem usa. `{n}` = segundos do Retry-After.
// Voz da barbearia (Fatia 14B, E21): nunca "servidor", "sessão", "requisição", "token", "API", "erro interno",
// "inválido" nem "falha"; sempre o próximo passo; nenhum número de regra configurável fixo (A34 a A38).
// O texto NUNCA vem do `title` nem do `detail` da API (ADR 0005).
import { API_INDISPONIVEL, type ErroApi } from "./erro";

/** Texto da API dormindo (RNF5): a consulta tenta de novo sozinha. */
export const TEXTO_ACORDANDO =
  "A agenda estava descansando e já está acordando. Leva uns segundos, e a gente tenta de novo sozinho.";
/** Texto da consulta que não respondeu (o botão Tentar de novo fica logo abaixo). */
export const TEXTO_SEM_RESPOSTA =
  "A agenda não respondeu desta vez. Toque em Tentar de novo; se continuar, chame a gente no WhatsApp.";

export const MENSAGENS: Record<string, string> = {
  VALIDACAO_INVALIDA: "Falta ajustar os campos marcados. Confira e envie de novo.",
  JSON_MALFORMADO: "Não conseguimos entender o que foi enviado. Tente de novo.",
  CAMPO_DESCONHECIDO: "Algo mudou por aqui. Recarregue a página e tente de novo.",
  CEP_INVALIDO: "Esse CEP não confere. Digite os 8 números.",
  CPF_INVALIDO: "Esse CPF não confere. Confira os 11 números.",
  IDEMPOTENCY_KEY_AUSENTE: "Não foi possível iniciar o pagamento. Tente de novo.",
  NAO_AUTENTICADO: "Entre na sua conta para continuar.",
  CREDENCIAIS_INVALIDAS: "E-mail ou senha não conferem. Confira e tente de novo; o botão Mostrar a senha ajuda.",
  REFRESH_INVALIDO: "Por segurança, você saiu da conta depois de um tempo parado. Entre de novo para continuar.",
  REFRESH_REUTILIZADO: "Por segurança, você saiu da conta. Entre de novo para continuar.",
  WEBHOOK_NAO_AUTENTICADO: "Não foi possível concluir. Tente de novo mais tarde.",
  ACESSO_NEGADO: "Esta área é só da equipe da barbearia. Volte para o início.",
  RECURSO_NAO_ENCONTRADO: "Não encontramos o que você procura. Volte para o início.",
  TROCA_DE_SENHA_OBRIGATORIA: "Crie uma senha nova para continuar.",
  CEP_NAO_ENCONTRADO: "Não encontramos este CEP. Confira os números.",
  FUNCIONALIDADE_DESLIGADA: "Este recurso ainda não está disponível. Volte mais tarde.",
  METODO_NAO_PERMITIDO: "Algo mudou por aqui. Recarregue a página.",
  MIDIA_NAO_SUPORTADA: "Algo mudou por aqui. Recarregue a página.",
  EMAIL_JA_CADASTRADO: "Esse e-mail já tem conta aqui. Entre com ele, ou use outro e-mail.",
  HORARIO_INDISPONIVEL: "Alguém pegou esse horário agorinha. Escolha outro logo abaixo.",
  CONFLITO_DE_VERSAO: "Alguém alterou isto antes de você. Confira a versão atual e salve de novo.",
  CONFLITO_CONCORRENTE: "Muita gente marcando ao mesmo tempo. Toque de novo em um instante.",
  NOME_JA_EXISTE: "Já existe um item com este nome. Escolha outro nome.",
  FORA_DO_EXPEDIENTE: "A barbearia não atende neste horário. Escolha outro.",
  // D197 (Fatia 14A): não há antecedência mínima nem limite de dias; "antecedência" é só o nome do código, que significa "já passou".
  ANTECEDENCIA_INSUFICIENTE: "Esse horário já passou. Escolha um horário mais para frente.",
  // Deprecated na 1.11.0: a API não devolve mais; o texto fica para um front e uma API de versões diferentes durante o deploy.
  FORA_DO_HORIZONTE: "Essa data não está disponível. Escolha outra em Outro dia.",
  ACEITE_POLITICA_OBRIGATORIO: "Para pagar, aceite a política de pagamento.",
  TRANSICAO_INVALIDA: "Essa mudança não é possível agora. Em atendimentos, espere o horário de início.",
  MOTIVO_OBRIGATORIO: "Informe o motivo do cancelamento.",
  PRORROGACAO_FORA_DA_JANELA: "Ainda não é hora de acrescentar tempo. O botão aparece no último minuto.",
  // Texto final (D214, 27/11): o 11º pedido de mais tempo (contrato 1.12.0, ADR 0015).
  PRORROGACAO_ESGOTADA: "Você já usou todo o tempo extra. Conclua o pagamento agora ou escolha outro horário.",
  RESERVA_EXPIRADA: "O tempo para pagar acabou. Escolha um horário de novo.",
  COBRANCA_EM_ANDAMENTO: "Já existe um pagamento em andamento para esta reserva. Espere a confirmação nesta tela.",
  SERVICO_INATIVO: "Este serviço não está disponível agora. Escolha outro.",
  IDEMPOTENCIA_CONFLITO: "Não foi possível repetir este pagamento. Comece de novo.",
  CONVITE_INVALIDO: "Este link não vale mais ou não pode ser usado aqui. Peça outro convite ao dono da barbearia.",
  JORNADA_SOBREPOSTA: "Há horários que se sobrepõem no mesmo dia. Ajuste a jornada.",
  FOLGA_COM_RESERVAS: "Há agendamentos neste período. Cancele-os antes ou escolha outro período.",
  USUARIO_JA_VINCULADO: "Esta conta já está ligada a outro barbeiro. Use outro e-mail.",
  LIMITE_EXCEDIDO: "Muitas tentativas. Tente de novo em {n} segundos.",
  GATEWAY_INDISPONIVEL:
    "Não conseguimos gerar o pagamento agora. Seu horário continua separado até o fim do contador: toque em Pagar de novo.",
  CEP_INDISPONIVEL: "Não conseguimos buscar o endereço agora. Continue o cadastro: o CEP é salvo.",
  BANCO_INDISPONIVEL: "A agenda está acordando. Espere uns segundos e toque em Tentar de novo.",
  EXCLUSAO_BLOQUEADA_POR_RESERVA: "Você tem um agendamento em aberto. Para excluir a conta, fale com a barbearia.",
  ERRO_INTERNO: "Deu um problema do nosso lado, não foi você. Tente de novo; se continuar, chame a gente no WhatsApp.",
  [API_INDISPONIVEL]: TEXTO_ACORDANDO,
};

export const MENSAGEM_GENERICA =
  "Deu um problema do nosso lado, não foi você. Tente de novo; se continuar, chame a gente no WhatsApp.";

/** Texto do erro pelo `code`. Código desconhecido (versão nova da API) cai no texto genérico. */
export function textoDoCodigo(code: string, retryAfter?: number): string {
  const texto = MENSAGENS[code] ?? MENSAGEM_GENERICA;
  const espera = retryAfter ?? 5;
  // texto aceito no design system: acima de um minuto a espera vem em minutos
  if (code === "LIMITE_EXCEDIDO" && espera >= 60)
    return `Muitas tentativas. Tente de novo daqui a ${Math.ceil(espera / 60)} min.`;
  return texto.replace("{n}", String(espera));
}

/** Texto para o usuário. Um 429 sem `code` conhecido mostra a espera do Retry-After. */
export function mensagemDoErro(erro: Pick<ErroApi, "code" | "status" | "retryAfter">): string {
  if (erro.status === 429 && !(erro.code in MENSAGENS)) return textoDoCodigo("LIMITE_EXCEDIDO", erro.retryAfter);
  return textoDoCodigo(erro.code, erro.retryAfter);
}

/** Código para suporte: só o traceId, que não tem dado pessoal. */
export function codigoParaSuporte(erro: Pick<ErroApi, "traceId">): string | undefined {
  return erro.traceId ? `Código para suporte: ${erro.traceId}` : undefined;
}
