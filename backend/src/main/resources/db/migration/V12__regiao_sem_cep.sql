-- Fatia 11A · segunda migração: limpa o dado antigo sem prova de consentimento e tira o CEP do banco (ADR 0014, D157, D-3).

-- 1. Reserva com região mas sem a versão do texto aceito: não há prova (LGPD art. 8º, § 2º). A região some; a reserva fica.
UPDATE reserva
SET regiao_bairro = NULL, regiao_cidade = NULL, regiao_uf = NULL, regiao_municipio_ibge = NULL
WHERE regiao_consentimento_versao IS NULL
  AND (regiao_bairro IS NOT NULL OR regiao_cidade IS NOT NULL OR regiao_uf IS NOT NULL OR regiao_municipio_ibge IS NOT NULL);

-- 2. Conta cuja linha mais recente de REGIAO_ALCANCE não é um aceite (ou que nunca teve linha): a localidade do perfil some.
UPDATE usuario u
SET bairro = NULL, cidade = NULL, uf = NULL, regiao_municipio_ibge = NULL
WHERE (u.bairro IS NOT NULL OR u.cidade IS NOT NULL OR u.uf IS NOT NULL OR u.regiao_municipio_ibge IS NOT NULL)
  AND NOT COALESCE((SELECT c.aceito FROM consentimento c
                    WHERE c.usuario_id = u.id AND c.finalidade = 'REGIAO_ALCANCE'
                    ORDER BY c.registrado_em DESC, c.id DESC LIMIT 1), false);

-- 3. O CEP completo não é guardado por ninguém: nenhuma coluna "cep" sobra em nenhuma tabela.
ALTER TABLE usuario DROP CONSTRAINT usuario_cep_ck;
ALTER TABLE usuario DROP COLUMN cep;
ALTER TABLE reserva_guest DROP CONSTRAINT reserva_guest_cep_ck;
ALTER TABLE reserva_guest DROP COLUMN cep;
