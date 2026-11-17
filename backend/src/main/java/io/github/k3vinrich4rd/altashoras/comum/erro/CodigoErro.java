package io.github.k3vinrich4rd.altashoras.comum.erro;

import java.util.Locale;

/**
 * Códigos estáveis de erro do contrato (docs/contrato/contrato-api.md, seção 5). O front decide pelo código,
 * nunca pelo texto (ADR 0005). Renomear um código quebra o front: só acrescentar.
 */
public enum CodigoErro {

    VALIDACAO_INVALIDA(400, "Dados inválidos", "Um ou mais campos estão inválidos."),
    JSON_MALFORMADO(400, "JSON malformado", "O corpo da requisição não é um JSON válido."),
    CAMPO_DESCONHECIDO(400, "Campo desconhecido", "O corpo tem um campo que a API não aceita."),
    CEP_INVALIDO(400, "CEP inválido", "O CEP deve ter 8 dígitos."),
    CPF_INVALIDO(400, "CPF inválido", "O CPF informado não é válido."),
    IDEMPOTENCY_KEY_AUSENTE(400, "Chave de idempotência ausente", "Envie o cabeçalho X-Idempotency-Key."),
    NAO_AUTENTICADO(401, "Não autenticado", "Faça login para continuar."),
    CREDENCIAIS_INVALIDAS(401, "Credenciais inválidas", "E-mail ou senha inválidos."),
    REFRESH_INVALIDO(401, "Sessão inválida", "Faça login de novo."),
    REFRESH_REUTILIZADO(401, "Sessão encerrada por segurança", "Faça login de novo."),
    WEBHOOK_NAO_AUTENTICADO(401, "Notificação não autenticada", "Notificação sem autenticação válida."),
    ACESSO_NEGADO(403, "Acesso negado", "Você não tem permissão para esta ação."),
    TROCA_DE_SENHA_OBRIGATORIA(403, "Troca de senha obrigatória", "Troque a senha inicial antes de continuar."),
    RECURSO_NAO_ENCONTRADO(404, "Não encontrado", "O recurso pedido não existe."),
    CEP_NAO_ENCONTRADO(404, "CEP não encontrado", "Nenhum endereço encontrado para este CEP."),
    FUNCIONALIDADE_DESLIGADA(404, "Não encontrado", "O recurso pedido não existe."),
    METODO_NAO_PERMITIDO(405, "Método não permitido", "Este método HTTP não é aceito nesta rota."),
    EMAIL_JA_CADASTRADO(409, "E-mail já cadastrado", "Já existe uma conta com este e-mail."),
    HORARIO_INDISPONIVEL(409, "Horário indisponível", "Este horário acabou de ser reservado. Escolha outro."),
    CONFLITO_DE_VERSAO(409, "Registro alterado por outra pessoa", "Recarregue e tente de novo."),
    CONFLITO_CONCORRENTE(409, "Conflito momentâneo", "Muitas reservas ao mesmo tempo. Tente de novo."),
    NOME_JA_EXISTE(409, "Nome já existe", "Já existe um registro com este nome."),
    COBRANCA_EM_ANDAMENTO(409, "Cobrança em andamento", "Esta reserva já tem uma cobrança pendente."),
    MIDIA_NAO_SUPORTADA(415, "Tipo de conteúdo não suportado", "Envie o corpo como application/json."),
    FORA_DO_EXPEDIENTE(422, "Fora do horário de atendimento", "A barbearia não atende neste horário."),
    ANTECEDENCIA_INSUFICIENTE(422, "Antecedência mínima não atendida", "Escolha um horário mais à frente."),
    FORA_DO_HORIZONTE(422, "Data além do permitido", "A agenda ainda não está aberta para esta data."),
    ACEITE_POLITICA_OBRIGATORIO(422, "Aceite da política obrigatório", "Aceite a política de pagamento para continuar."),
    TRANSICAO_INVALIDA(422, "Mudança de status não permitida", "Esta mudança de status não é permitida."),
    MOTIVO_OBRIGATORIO(422, "Motivo obrigatório", "Informe o motivo."),
    RESERVA_EXPIRADA(422, "Reserva expirada", "O prazo de pagamento desta reserva acabou."),
    SERVICO_INATIVO(422, "Serviço indisponível", "Este serviço não está disponível."),
    IDEMPOTENCIA_CONFLITO(422, "Chave de idempotência já usada", "Esta chave já foi usada com outro corpo."),
    JORNADA_SOBREPOSTA(422, "Jornada inválida", "Os intervalos da jornada se sobrepõem."),
    FOLGA_COM_RESERVAS(422, "Existem reservas no período", "Há reservas no período da folga."),
    LIMITE_EXCEDIDO(429, "Muitas tentativas", "Aguarde um pouco e tente de novo."),
    GATEWAY_INDISPONIVEL(503, "Pagamento indisponível no momento", "Tente de novo em alguns minutos."),
    CEP_INDISPONIVEL(503, "Consulta de CEP indisponível", "Preencha o endereço manualmente."),
    ERRO_INTERNO(500, "Erro interno", "Algo deu errado do nosso lado. Tente de novo.");

    private static final String BASE_TIPO =
            "https://github.com/k3vinrich4rd/barbearia-altas-horas/blob/main/docs/contrato/contrato-api.md#";

    private final int status;
    private final String titulo;
    private final String detalhePadrao;

    CodigoErro(int status, String titulo, String detalhePadrao) {
        this.status = status;
        this.titulo = titulo;
        this.detalhePadrao = detalhePadrao;
    }

    public int status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public String detalhePadrao() {
        return detalhePadrao;
    }

    /** URI do "type" do RFC 9457: aponta para a linha do código na tabela de erros do contrato. */
    public String tipo() {
        return BASE_TIPO + name().toLowerCase(Locale.ROOT);
    }
}
