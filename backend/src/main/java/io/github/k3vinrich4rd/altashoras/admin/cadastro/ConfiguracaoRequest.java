package io.github.k3vinrich4rd.altashoras.admin.cadastro;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.github.k3vinrich4rd.altashoras.comum.validacao.ValorPermitido;

/**
 * O corpo do PUT da configuração da agenda (E22). Os limites são os da {@code configuracao_agenda_valores_ck}: o
 * banco é a última barreira, mas o erro por campo sai daqui. O {@code fuso} só aparece na leitura: mandá-lo é
 * 400 CAMPO_DESCONHECIDO. A antecedência mínima e o horizonte foram revogados (Fatia 14A, D197): os dois campos antigos
 * são deprecated desde a D197, sem efeito: continuam aceitos, sem validação, e nenhum código os lê (saem na próxima
 * versão maior do contrato).
 */
record ConfiguracaoRequest(
        /** Deprecated desde a D197 (1.11.0), sem efeito: aceita e ignorada, para um front antigo não receber 400. */
        @Deprecated Integer antecedenciaMinimaMin,
        @NotNull(message = "obrigatório") @Min(value = 0, message = "não pode ser negativo")
        @Max(value = 120, message = "deve ser de no máximo 120 minutos") Integer intervaloEntreAtendimentosMin,
        @NotNull(message = "obrigatório")
        @ValorPermitido(value = {5, 10, 15, 20, 30, 60}, message = "deve ser 5, 10, 15, 20, 30 ou 60") Integer granularidadeMin,
        /** Deprecated desde a D197 (1.11.0), sem efeito: aceito e ignorado, como a antecedência. */
        @Deprecated Integer horizonteDias,
        @NotNull(message = "obrigatório") @Min(value = 3, message = "deve ser de pelo menos 3 minutos")
        @Max(value = 60, message = "deve ser de no máximo 60 minutos") Integer expiracaoPagamentoMin,
        @NotNull(message = "obrigatório") Long version) {
}
