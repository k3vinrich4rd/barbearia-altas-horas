-- =============================================================================
-- Migração Flyway V14 · "Acrescentar 1 minuto" no Pix e agenda sem antecedência nem horizonte (Fatia 14A)
--
-- 1. reserva.prorrogada_em: o instante em que o cliente (ou o visitante) acrescentou 1 minuto ao prazo do Pix (E2, D188).
--    Só se acrescenta uma vez por reserva: depois de preenchida, a coluna nunca volta a nulo. O banco é a última
--    linha de defesa: reserva manual (R33) não tem pagamento, então não pode ter prorrogação.
-- 2. configuracao_agenda perde antecedencia_minima_min e horizonte_dias (D197). Não é "zerar o número": a regra foi
--    revogada pelo dono, e deixar a coluna permitiria o painel religá-la. Só configuração, nenhum dado pessoal.
--    O tempo extra (60 s) NÃO é coluna: é a propriedade app.agenda.prorrogacao-pagamento (E10).
--
-- Migração já mergeada nunca é editada: o CHECK da V7 vira o CHECK abaixo, sem os dois campos.
-- =============================================================================

ALTER TABLE reserva
    ADD COLUMN prorrogada_em timestamptz,
    ADD CONSTRAINT reserva_prorrogacao_ck CHECK (prorrogada_em IS NULL OR origem <> 'MANUAL');

-- O DROP COLUMN levaria junto o CHECK inteiro (ele cita as duas colunas): recria só com o que sobra.
ALTER TABLE configuracao_agenda DROP CONSTRAINT configuracao_agenda_valores_ck;
ALTER TABLE configuracao_agenda
    DROP COLUMN antecedencia_minima_min,
    DROP COLUMN horizonte_dias;
ALTER TABLE configuracao_agenda ADD CONSTRAINT configuracao_agenda_valores_ck CHECK (
    intervalo_entre_atendimentos_min BETWEEN 0 AND 120
    AND granularidade_min IN (5, 10, 15, 20, 30, 60)
    AND expiracao_pagamento_min BETWEEN 3 AND 60
);
