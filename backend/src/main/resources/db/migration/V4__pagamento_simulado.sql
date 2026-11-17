-- Fatia 4B · provedor SIMULADO (D59).
-- No beta o pagamento roda em modo simulado: nenhuma conta em serviço externo, nenhum custo e nenhum dinheiro real.
-- A cobrança e o evento do GatewaySimulado precisam gravar provedor = 'SIMULADO'; a V1 só aceitava ASAAS e
-- MERCADO_PAGO. Migração já mergeada não se edita: troca os dois CHECKs aqui.
ALTER TABLE cobranca DROP CONSTRAINT cobranca_provedor_ck;
ALTER TABLE cobranca ADD CONSTRAINT cobranca_provedor_ck CHECK (provedor IN ('ASAAS', 'MERCADO_PAGO', 'SIMULADO'));

ALTER TABLE evento_webhook DROP CONSTRAINT evento_webhook_provedor_ck;
ALTER TABLE evento_webhook ADD CONSTRAINT evento_webhook_provedor_ck
    CHECK (provedor IN ('ASAAS', 'MERCADO_PAGO', 'SIMULADO'));
