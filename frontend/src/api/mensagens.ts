// Catálogo de mensagens por `code` (Anexo A da spec, D-4). Um único arquivo; a tela busca por `code`.
// Português do Brasil, sem dado pessoal e sem culpar quem usa. `{n}` = segundos do Retry-After.
// O texto NUNCA vem do `title` nem do `detail` da API (ADR 0005).
import { API_INDISPONIVEL, type ErroApi } from "./erro";

export const MENSAGENS: Record<string, string> = {
  VALIDACAO_INVALIDA: "Confira os campos marcados.",
  JSON_MALFORMADO: "Não conseguimos entender o que foi enviado. Tente de novo.",
  CAMPO_DESCONHECIDO: "Algo mudou por aqui. Recarregue a página e tente de novo.",
  CEP_INVALIDO: "CEP inválido. Digite os 8 números.",
  CPF_INVALIDO: "CPF inválido. Confira os números.",
  IDEMPOTENCY_KEY_AUSENTE: "Não foi possível iniciar o pagamento. Tente de novo.",
  NAO_AUTENTICADO: "Entre na sua conta para continuar.",
  CREDENCIAIS_INVALIDAS: "E-mail ou senha incorretos.",
  REFRESH_INVALIDO: "Sua sessão terminou. Entre de novo.",
  REFRESH_REUTILIZADO: "Por segurança, encerramos sua sessão. Entre de novo.",
  WEBHOOK_NAO_AUTENTICADO: "Não foi possível concluir. Tente de novo mais tarde.",
  ACESSO_NEGADO: "Você não tem acesso a esta área.",
  RECURSO_NAO_ENCONTRADO: "Não encontramos o que você procura.",
  TROCA_DE_SENHA_OBRIGATORIA: "Antes de continuar, troque a senha inicial.",
  CEP_NAO_ENCONTRADO: "Não encontramos este CEP. Confira os números.",
  FUNCIONALIDADE_DESLIGADA: "Este recurso ainda não está disponível.",
  METODO_NAO_PERMITIDO: "Algo mudou por aqui. Recarregue a página.",
  MIDIA_NAO_SUPORTADA: "Algo mudou por aqui. Recarregue a página.",
  EMAIL_JA_CADASTRADO: "Já existe uma conta com este e-mail. Entre ou use outro.",
  HORARIO_INDISPONIVEL: "Esse horário acabou de ser reservado. Escolha outro.",
  CONFLITO_DE_VERSAO: "Alguém alterou isto antes de você. Mostramos a versão atual.",
  CONFLITO_CONCORRENTE: "Muitas reservas ao mesmo tempo. Tente de novo.",
  NOME_JA_EXISTE: "Já existe um item com este nome.",
  FORA_DO_EXPEDIENTE: "A barbearia não atende neste horário. Escolha outro.",
  ANTECEDENCIA_INSUFICIENTE: "É preciso agendar com pelo menos 2 horas de antecedência.",
  FORA_DO_HORIZONTE: "Só dá para agendar com até 30 dias de antecedência.",
  ACEITE_POLITICA_OBRIGATORIO: "Para pagar, aceite a política de pagamento.",
  TRANSICAO_INVALIDA: "Essa mudança não é possível agora. Em atendimentos, só depois do horário de início.",
  MOTIVO_OBRIGATORIO: "Informe o motivo do cancelamento.",
  RESERVA_EXPIRADA: "O tempo para pagar acabou. Escolha um horário de novo.",
  COBRANCA_EM_ANDAMENTO: "Já existe um pagamento em andamento para esta reserva.",
  SERVICO_INATIVO: "Este serviço não está disponível agora. Escolha outro.",
  IDEMPOTENCIA_CONFLITO: "Não foi possível repetir este pagamento. Comece de novo.",
  JORNADA_SOBREPOSTA: "Há horários que se sobrepõem no mesmo dia. Ajuste a jornada.",
  FOLGA_COM_RESERVAS: "Há agendamentos neste período. Cancele-os antes ou escolha outro período.",
  USUARIO_JA_VINCULADO: "Esta conta já está ligada a outro barbeiro.",
  LIMITE_EXCEDIDO: "Muitas tentativas. Tente de novo em {n} segundos.",
  GATEWAY_INDISPONIVEL:
    "O pagamento está fora do ar agora. Tente de novo em instantes; seu horário continua guardado até o prazo.",
  CEP_INDISPONIVEL: "Não conseguimos buscar o endereço agora. O cadastro continua e o CEP é salvo.",
  EXCLUSAO_BLOQUEADA_POR_RESERVA:
    "Você tem um agendamento pago. Combine o cancelamento com a barbearia e depois exclua a conta.",
  ERRO_INTERNO: "Algo deu errado do nosso lado. Tente de novo.",
  [API_INDISPONIVEL]: "O servidor está acordando. Aguarde alguns segundos.",
};

export const MENSAGEM_GENERICA = "Algo deu errado. Tente de novo.";

/** Texto do erro pelo `code`. Código desconhecido (versão nova da API) cai no texto genérico. */
export function textoDoCodigo(code: string, retryAfter?: number): string {
  const texto = MENSAGENS[code] ?? MENSAGEM_GENERICA;
  return texto.replace("{n}", String(retryAfter ?? 5));
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
