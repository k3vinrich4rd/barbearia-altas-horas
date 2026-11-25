-- =============================================================================
-- Migração Flyway V14 · "Acrescentar 1 minuto" no Pix e agenda sem antecedência nem horizonte (Fatia 14A)
--
-- 1. reserva.prorrogada_em: o instante em que o cliente (ou o visitante) acrescentou 1 minuto ao prazo do Pix (E2, D188).
--    Só se acrescenta uma vez por reserva: depois de preenchida, a coluna nunca volta a nulo. O banco é a última
--    linha de defesa: reserva manual (R33) não tem pagamento, então não pode ter prorrogação.
-- 2. configuracao_agenda: antecedencia_minima_min e horizonte_dias FICAM, com DEFAULT, sem uso pelo código (D197, D199).
--    Padrão expandir → contrair: a versão anterior da API ainda lê as duas colunas, então apagá-las agora quebraria o
--    rollback. A remoção física fica para uma migração futura, depois de uma release estável sem elas.
--    O tempo extra (60 s) NÃO é coluna: é a propriedade app.agenda.prorrogacao-pagamento (E10).
-- =============================================================================

ALTER TABLE reserva
    ADD COLUMN prorrogada_em timestamptz,
    ADD CONSTRAINT reserva_prorrogacao_ck CHECK (prorrogada_em IS NULL OR origem <> 'MANUAL');

-- Sem uso pelo código desde a 14A (D197); o DEFAULT só garante que um INSERT novo não precise conhecê-las.
ALTER TABLE configuracao_agenda
    ALTER COLUMN antecedencia_minima_min SET DEFAULT 120,
    ALTER COLUMN horizonte_dias SET DEFAULT 30;
COMMENT ON COLUMN configuracao_agenda.antecedencia_minima_min IS 'Sem uso desde a V14 (D197, D199); remover depois de uma release estável';
COMMENT ON COLUMN configuracao_agenda.horizonte_dias IS 'Sem uso desde a V14 (D197, D199); remover depois de uma release estável';
