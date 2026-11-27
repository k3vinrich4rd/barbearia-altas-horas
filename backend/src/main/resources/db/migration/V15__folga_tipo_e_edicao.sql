-- =============================================================================
-- Migração Flyway V15 · férias e folgas no painel (Fatia 15, ADR 0015, D202 e D203)
--
-- 1. folga.tipo: FERIAS ou FOLGA, só um rótulo para o painel (os dois bloqueiam a agenda do mesmo jeito, E1).
--    DEFAULT 'FOLGA': as linhas que já existem e o INSERT da versão anterior da API continuam valendo.
-- 2. folga.atualizado_por e folga.atualizado_em: quem editou por último e quando (PUT da 1.12.0). Nulos até a
--    primeira edição. O motivo e o nome de clientes nunca vão para cá nem para a auditoria (R30).
-- Só acrescenta (expandir e contrair): a versão anterior da API continua funcionando com o banco novo.
-- =============================================================================

ALTER TABLE folga
    ADD COLUMN tipo           varchar(10) NOT NULL DEFAULT 'FOLGA',
    ADD COLUMN atualizado_por uuid        REFERENCES usuario (id),
    ADD COLUMN atualizado_em  timestamptz,
    ADD CONSTRAINT folga_tipo_ck CHECK (tipo IN ('FERIAS', 'FOLGA'));

COMMENT ON COLUMN folga.tipo IS 'FERIAS ou FOLGA (ADR 0015): rótulo do painel; nunca é público';
COMMENT ON COLUMN folga.atualizado_em IS 'Última edição pelo PUT (1.12.0); nulo até a primeira edição';
