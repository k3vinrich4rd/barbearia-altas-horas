-- Fatia 6D · prazo de pagamento de 3 minutos, WhatsApp do barbeiro e reserva manual pelo painel.
-- Migração já mergeada não se edita: tudo o que a V1 a V6 não previa nasce aqui.

-- -----------------------------------------------------------------------------
-- 1. Prazo de pagamento: de 15 para 3 minutos (R7, D121)
-- -----------------------------------------------------------------------------
-- O CHECK da V1 aceitava de 5 a 60 minutos; o novo padrão (3) ficaria fora dele. O piso passa a 3.
ALTER TABLE configuracao_agenda DROP CONSTRAINT configuracao_agenda_valores_ck;
ALTER TABLE configuracao_agenda ADD CONSTRAINT configuracao_agenda_valores_ck CHECK (
    antecedencia_minima_min >= 0
    AND intervalo_entre_atendimentos_min BETWEEN 0 AND 120
    AND granularidade_min IN (5, 10, 15, 20, 30, 60)
    AND horizonte_dias BETWEEN 1 AND 365
    AND expiracao_pagamento_min BETWEEN 3 AND 60
);
-- Só muda quem ainda está no padrão antigo: se o admin já escolheu outro prazo, a escolha dele vale. A versão sobe
-- para a tela que estava aberta com o prazo antigo receber 409 e recarregar (a mesma regra de qualquer alteração).
UPDATE configuracao_agenda
SET expiracao_pagamento_min = 3, atualizado_em = now(), version = version + 1
WHERE id = 1 AND expiracao_pagamento_min = 15;

-- -----------------------------------------------------------------------------
-- 2. WhatsApp do barbeiro: contato comercial, cadastrado pelo ADMIN e mostrado ao público (D122)
-- -----------------------------------------------------------------------------
-- Não é dado de cliente. Só dígitos, com DDD e DDI opcional: o mesmo formato do usuario.whatsapp. Opcional.
ALTER TABLE barbeiro
    ADD COLUMN whatsapp varchar(13),
    ADD CONSTRAINT barbeiro_whatsapp_ck CHECK (whatsapp IS NULL OR whatsapp ~ '^[0-9]{10,13}$');

-- -----------------------------------------------------------------------------
-- 3. Reserva manual: horário combinado por WhatsApp, travado pelo painel (D123 a D127)
-- -----------------------------------------------------------------------------
-- Nasce CONFIRMADO, sem cliente, sem cobrança e sem aceite de política (ninguém pagou pelo app).
-- nome_cliente e observacao são dado pessoal opcional digitado pela barbearia: só existem nesta origem e viram NULL
-- na retenção (90 dias depois do fim do atendimento, como o contato do visitante). O WhatsApp do cliente NÃO é guardado.
ALTER TABLE reserva
    ADD COLUMN nome_cliente varchar(80),
    ADD COLUMN observacao   varchar(200),
    ADD COLUMN criado_por   uuid;                           -- quem travou o horário (id da conta do painel)

ALTER TABLE reserva
    DROP CONSTRAINT reserva_origem_ck,
    DROP CONSTRAINT reserva_aceite_ck,
    ADD CONSTRAINT reserva_origem_ck CHECK (
        (origem = 'CLIENTE' AND cliente_id IS NOT NULL)
        OR (origem = 'GUEST' AND cliente_id IS NULL)
        OR (origem = 'MANUAL' AND cliente_id IS NULL)
    ),
    -- Só se confirma, conclui ou marca falta quem pagou (e aceitou a política); a reserva manual não passa pelo app.
    ADD CONSTRAINT reserva_aceite_ck CHECK (status NOT IN ('CONFIRMADO', 'CONCLUIDO', 'NAO_COMPARECEU')
                                            OR aceite_politica_em IS NOT NULL OR origem = 'MANUAL'),
    -- A reserva manual nunca espera pagamento nem expira, e não tem aceite.
    ADD CONSTRAINT reserva_manual_ck CHECK (
        origem <> 'MANUAL'
        OR (status IN ('CONFIRMADO', 'CONCLUIDO', 'NAO_COMPARECEU', 'CANCELADO')
            AND aceite_politica_em IS NULL AND expira_em IS NULL)
    ),
    -- Nome e observação só existem na reserva manual: nas outras origens o contato mora em usuario e reserva_guest.
    ADD CONSTRAINT reserva_contato_manual_ck CHECK (origem = 'MANUAL' OR (nome_cliente IS NULL AND observacao IS NULL));

-- A retenção procura só as manuais que ainda guardam nome ou observação, pelo fim do atendimento.
CREATE INDEX reserva_manual_retencao_ix ON reserva (fim)
    WHERE origem = 'MANUAL' AND (nome_cliente IS NOT NULL OR observacao IS NOT NULL);
