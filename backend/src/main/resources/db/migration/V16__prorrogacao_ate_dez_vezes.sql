-- =============================================================================
-- Migração Flyway V16 · "Mais tempo para pagar" até 10 vezes (Fatia 15, ADR 0015, E20 e E21)
--
-- 1. reserva.prorrogacoes: quantos pedidos de mais tempo a reserva já usou (0 a app.agenda.prorrogacao-maximo).
--    A reserva prorrogada na regra antiga (uma vez só, V14) conta como 1.
-- 2. reserva.prorrogacao_chave: a X-Idempotency-Key do ÚLTIMO pedido (opcional). Repetir o último pedido com a mesma
--    chave devolve o mesmo corpo sem somar nem contar.
-- 3. prorrogada_em continua: agora é o instante do último pedido.
-- O máximo e os segundos NÃO são colunas: são propriedades (app.agenda.prorrogacao-maximo e prorrogacao-pagamento).
-- Só acrescenta: a versão anterior da API ignora as colunas novas.
-- =============================================================================

ALTER TABLE reserva
    ADD COLUMN prorrogacoes integer NOT NULL DEFAULT 0,
    ADD COLUMN prorrogacao_chave uuid,
    ADD CONSTRAINT reserva_prorrogacoes_ck CHECK (prorrogacoes >= 0),
    ADD CONSTRAINT reserva_prorrogacoes_manual_ck CHECK (prorrogacoes = 0 OR origem <> 'MANUAL');

UPDATE reserva SET prorrogacoes = 1 WHERE prorrogada_em IS NOT NULL;

COMMENT ON COLUMN reserva.prorrogacoes IS 'Pedidos de mais tempo já usados (ADR 0015); o máximo é a propriedade app.agenda.prorrogacao-maximo';
COMMENT ON COLUMN reserva.prorrogacao_chave IS 'X-Idempotency-Key do último pedido de mais tempo (opcional, ADR 0015, E21)';
COMMENT ON COLUMN reserva.prorrogada_em IS 'Instante do último pedido de mais tempo';
