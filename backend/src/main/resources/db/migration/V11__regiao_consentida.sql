-- Fatia 11A · região só com consentimento e sem guardar o CEP (ADR 0014, D157). Primeira de duas migrações: esta só ACRESCENTA
-- (finalidade nova, código IBGE, versão do consentimento). A limpeza do dado antigo e a retirada das colunas de CEP ficam na
-- V12, para a regra "sem prova de consentimento a região é anulada" poder ser testada com dado dos dois tipos.
-- Números: a V10 está reservada para a Fatia 12 (serviço de platinado); a Fatia 11 usa a partir da V11.

-- 1. A conta prova o consentimento de região com uma linha nova de consentimento (histórico só de inserção, R28).
ALTER TABLE consentimento DROP CONSTRAINT consentimento_finalidade_ck;
ALTER TABLE consentimento
    ADD CONSTRAINT consentimento_finalidade_ck CHECK (finalidade IN ('MARKETING_CEP', 'REGIAO_ALCANCE'));

-- 2. Código IBGE do município (7 dígitos) no perfil: bairro, cidade e uf continuam como a localidade da conta.
ALTER TABLE usuario
    ADD COLUMN regiao_municipio_ibge char(7),
    ADD CONSTRAINT usuario_regiao_municipio_ibge_ck CHECK (regiao_municipio_ibge IS NULL OR regiao_municipio_ibge ~ '^[0-9]{7}$');

-- 3. A reserva guarda a CÓPIA da região, o município IBGE e a versão do texto que a pessoa aceitou (a prova do visitante,
--    art. 8º, § 2º). Sem a versão a região não se sustenta (V12).
ALTER TABLE reserva
    ADD COLUMN regiao_municipio_ibge char(7),
    ADD COLUMN regiao_consentimento_versao varchar(20),
    ADD CONSTRAINT reserva_regiao_municipio_ibge_ck CHECK (regiao_municipio_ibge IS NULL OR regiao_municipio_ibge ~ '^[0-9]{7}$');

-- O índice parcial do alcance passa a cobrir o município.
DROP INDEX reserva_alcance_ix;
CREATE INDEX reserva_alcance_ix ON reserva (inicio, regiao_municipio_ibge) WHERE status IN ('CONFIRMADO', 'CONCLUIDO');
