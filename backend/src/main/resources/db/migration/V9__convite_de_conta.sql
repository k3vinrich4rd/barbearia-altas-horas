-- =============================================================================
-- Migração Flyway V9 · convite do barbeiro (Fatia 10B, ADR 0013, D151 e D153)
--
-- O dono (ADMIN) gera um convite de uso único para um barbeiro cadastrado e entrega o
-- link por WhatsApp ou em pessoa. O barbeiro abre o link, digita o PRÓPRIO e-mail e cria
-- a senha (D-1 da spec): só nesse momento nasce a conta, então o convite aponta para o
-- BARBEIRO e não para uma conta. Nenhuma tabela existente muda e nenhum dado pessoal entra
-- em seed. Papéis continuam os três de sempre (ADR 0013, opção B).
--
-- O banco guarda só o SHA-256 do código (256 bits de SecureRandom: não precisa de BCrypt);
-- o código aparece uma vez, na tela do dono, e nunca em coluna, log ou auditoria.
-- =============================================================================

CREATE TABLE convite_conta (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    barbeiro_id  uuid         NOT NULL REFERENCES barbeiro (id),
    nome         varchar(120) NOT NULL,                -- nome de exibição do barbeiro, copiado na emissão (vira o nome da conta)
    token_hash   char(64)     NOT NULL,                -- SHA-256 hex do código; o código nunca é guardado
    expira_em    timestamptz  NOT NULL,
    usado_em     timestamptz,
    revogado_em  timestamptz,
    tentativas   smallint     NOT NULL DEFAULT 0,      -- falhas com o código certo (E8)
    criado_por   uuid         NOT NULL REFERENCES usuario (id),
    criado_em    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT convite_conta_hash_uk UNIQUE (token_hash),
    CONSTRAINT convite_conta_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT convite_conta_tentativas_ck CHECK (tentativas >= 0)
);

-- Um convite pendente por barbeiro: emitir de novo revoga o anterior na mesma transação.
CREATE UNIQUE INDEX convite_conta_pendente_ux ON convite_conta (barbeiro_id)
    WHERE usado_em IS NULL AND revogado_em IS NULL;
