-- Fatia 5B · painel do barbeiro e do admin (cadastros).
-- O contrato (GET e PUT /admin/configuracao) já exige "version" para a tela não sobrescrever a alteração de outra
-- pessoa (409 CONFLITO_DE_VERSAO), e a V1 não criou a coluna. Migração já mergeada não se edita: a coluna nasce aqui,
-- com 0 na linha única que já existe. As demais tabelas do painel (auditoria_admin, folga, cobranca.devolvida_*,
-- reserva.cancelado_* e finalizado_*) já estão na V1.
ALTER TABLE configuracao_agenda ADD COLUMN version bigint NOT NULL DEFAULT 0;
